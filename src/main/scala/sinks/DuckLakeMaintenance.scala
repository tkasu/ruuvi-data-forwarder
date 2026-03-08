package sinks

import config.{CatalogType, DuckLakeConfig}
import zio.*
import zio.logging.*
import java.sql.{Connection, DriverManager}
import java.nio.file.{Files, Paths}

/** Handles scheduled DuckLake maintenance operations.
  *
  * Runs the DuckLake `CHECKPOINT` statement on a fixed schedule. `CHECKPOINT`
  * bundles all maintenance functions in order:
  *   1. `ducklake_flush_inlined_data` 2. `ducklake_expire_snapshots` 3.
  *      `ducklake_merge_adjacent_files` 4. `ducklake_rewrite_data_files` 5.
  *      `ducklake_cleanup_old_files` 6. `ducklake_delete_orphaned_files`
  *
  * Before running `CHECKPOINT`, the `expire_older_than` option is set on the
  * catalog so that snapshot expiry and file cleanup use the configured
  * retention window.
  *
  * This runs concurrently with the append pipeline — DuckLake snapshot
  * isolation ensures there are no conflicts with ongoing inserts.
  */
class DuckLakeMaintenance(config: DuckLakeConfig):

  private val maintenanceConfig = config.maintenance

  /** Acquire a DuckLake JDBC connection with all necessary extensions loaded
    * and the DuckLake catalog attached as "ducklake".
    */
  private def acquireConnection: ZIO[Scope, Throwable, Connection] =
    ZIO.acquireRelease(
      ZIO.attemptBlocking {
        Class.forName("org.duckdb.DuckDBDriver")

        val catalogFilePath = Paths.get(config.catalogPath)
        Option(catalogFilePath.getParent)
          .foreach(Files.createDirectories(_))

        val dataFilePath = Paths.get(config.dataPath)
        Files.createDirectories(dataFilePath)

        val conn = DriverManager.getConnection("jdbc:duckdb:")

        val installStmt = conn.createStatement()
        try
          installStmt.execute("INSTALL ducklake")
          installStmt.execute("LOAD ducklake")

          config.catalogType match
            case CatalogType.SQLite =>
              installStmt.execute("INSTALL sqlite")
              installStmt.execute("LOAD sqlite")
            case CatalogType.Postgres =>
              installStmt.execute("INSTALL postgres")
              installStmt.execute("LOAD postgres")
            case CatalogType.DuckDB =>
        finally installStmt.close()

        val attachSQL = config.catalogType match
          case CatalogType.DuckDB =>
            s"ATTACH 'ducklake:${config.catalogPath}' AS ducklake (DATA_PATH '${config.dataPath}')"
          case CatalogType.SQLite =>
            s"ATTACH 'ducklake:sqlite:${config.catalogPath}' AS ducklake (DATA_PATH '${config.dataPath}')"
          case CatalogType.Postgres =>
            s"ATTACH 'ducklake:${config.catalogPath}' AS ducklake (DATA_PATH '${config.dataPath}')"

        val attachStmt = conn.createStatement()
        try attachStmt.execute(attachSQL)
        finally attachStmt.close()

        conn
      }
    )(conn => ZIO.attemptBlocking(conn.close()).orDie)

  /** Run the full DuckLake maintenance sequence via the `CHECKPOINT` statement.
    *
    * Step 1: Set `expire_older_than` on the catalog so that snapshot expiry and
    * file cleanup respect the configured retention window. Step 2: Run
    * `CHECKPOINT`, which DuckLake expands internally into all 6 maintenance
    * functions: `ducklake_flush_inlined_data`, `ducklake_expire_snapshots`,
    * `ducklake_merge_adjacent_files`, `ducklake_rewrite_data_files`,
    * `ducklake_cleanup_old_files`, `ducklake_delete_orphaned_files`.
    *
    * The connection is acquired and released within this call, keeping it safe
    * to call concurrently with the insert pipeline.
    */
  val runCheckpoint: ZIO[Any, Throwable, Unit] =
    ZIO
      .scoped {
        for
          _ <- ZIO.logInfo(
            s"Starting DuckLake maintenance (expire_older_than='${maintenanceConfig.expireOlderThan}')"
          )
          conn <- acquireConnection
          _ <- ZIO.attemptBlocking {
            val stmt = conn.createStatement()
            try
              // Step 1: Configure the retention window for snapshot expiry and file cleanup
              stmt.execute(
                s"CALL ducklake.set_option('expire_older_than', '${maintenanceConfig.expireOlderThan}')"
              )
              // Step 2: Run all 6 maintenance operations in one statement
              stmt.execute("CHECKPOINT")
            finally stmt.close()
          }
          _ <- ZIO.logInfo("DuckLake maintenance completed successfully")
        yield ()
      }
      .tapError(err =>
        ZIO.logError(s"DuckLake maintenance failed: ${err.getMessage}")
      )

  /** Start the maintenance scheduler as a daemon fiber.
    *
    * If maintenance is disabled, returns immediately without starting anything.
    * Otherwise, forks a daemon fiber that runs the full maintenance sequence on
    * a fixed schedule. The first run is delayed by one full interval so startup
    * does not race with the sink's own catalog initialization. Errors in
    * individual runs are logged and swallowed so a transient failure never
    * kills the scheduler or the main pipeline.
    */
  def startScheduler(): ZIO[Any, Nothing, Unit] =
    if !maintenanceConfig.enabled then
      ZIO.logInfo("DuckLake maintenance scheduler is disabled")
    else
      ZIO.logInfo(
        s"Starting DuckLake maintenance scheduler (interval=${maintenanceConfig.intervalSeconds}s, " +
          s"expire_older_than='${maintenanceConfig.expireOlderThan}')"
      ) *>
        (ZIO.sleep(maintenanceConfig.intervalSeconds.seconds) *>
          runCheckpoint.catchAll(err =>
            ZIO.logError(
              s"DuckLake maintenance run failed (will retry on next interval): ${err.getMessage}"
            )
          )).forever.forkDaemon.unit

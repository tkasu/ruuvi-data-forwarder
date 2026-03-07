package sinks

import zio.*
import zio.test.*
import _root_.config.{CatalogType, DuckLakeConfig, DuckLakeMaintenanceConfig}
import java.nio.file.Files
import scala.util.Using

object DuckLakeMaintenanceSpec extends ZIOSpecDefault:

  /** Build a DuckLakeConfig backed by a DuckDB catalog stored at the given
    * paths. Maintenance is always enabled with a very long interval so it does
    * not fire automatically during tests.
    */
  private def makeConfig(
      catalogPath: String,
      dataPath: String,
      maintenanceEnabled: Boolean = true,
      expireOlderThan: String = "1 week"
  ): DuckLakeConfig =
    DuckLakeConfig(
      catalogType = CatalogType.DuckDB,
      catalogPath = catalogPath,
      dataPath = dataPath,
      maintenance = Some(
        DuckLakeMaintenanceConfig(
          enabled = maintenanceEnabled,
          intervalSeconds = 9999,
          expireOlderThan = expireOlderThan
        )
      )
    )

  /** Seed a DuckLake catalog+data directory with at least one insert so that
    * the maintenance functions (merge, expire, cleanup) have something to work
    * on.
    */
  private def seedDuckLake(
      catalogPath: String,
      dataPath: String
  ): ZIO[Any, Throwable, Unit] =
    ZIO.attemptBlocking {
      Class.forName("org.duckdb.DuckDBDriver")
      val conn =
        java.sql.DriverManager.getConnection("jdbc:duckdb:")

      val stmt = conn.createStatement()
      try
        stmt.execute("INSTALL ducklake")
        stmt.execute("LOAD ducklake")
        stmt.execute(
          s"ATTACH 'ducklake:$catalogPath' AS ducklake (DATA_PATH '$dataPath')"
        )
        stmt.execute(
          "CREATE TABLE IF NOT EXISTS ducklake.maint_test (id INTEGER NOT NULL)"
        )
        stmt.execute("INSERT INTO ducklake.maint_test VALUES (1)")
      finally
        stmt.close()
        conn.close()
    }

  def spec = suite("DuckLakeMaintenanceSpec")(
    test("runCheckpoint should complete successfully on a seeded DuckLake") {
      val tempCatalog =
        Files.createTempFile("ruuvi-maint-checkpoint-catalog-", ".ducklake")
      val tempDataDir =
        Files.createTempDirectory("ruuvi-maint-checkpoint-data-")
      Files.deleteIfExists(tempCatalog)

      val catalogPath = tempCatalog.toString
      val dataPath = tempDataDir.toString

      val run = for
        _ <- seedDuckLake(catalogPath, dataPath)
        config = makeConfig(catalogPath, dataPath)
        maintenance = DuckLakeMaintenance(config)
        _ <- maintenance.runCheckpoint
        // Clean up
        _ <- ZIO.attempt {
          Files.deleteIfExists(tempCatalog)
          Using.resource(Files.walk(tempDataDir)) { stream =>
            stream
              .sorted(java.util.Comparator.reverseOrder())
              .forEach(Files.deleteIfExists(_))
          }
        }
      yield true

      assertZIO(run)(Assertion.isTrue)
    },
    test(
      "runCheckpoint should succeed with a custom expire-older-than interval"
    ) {
      val tempCatalog =
        Files.createTempFile("ruuvi-maint-expire-catalog-", ".ducklake")
      val tempDataDir =
        Files.createTempDirectory("ruuvi-maint-expire-data-")
      Files.deleteIfExists(tempCatalog)

      val catalogPath = tempCatalog.toString
      val dataPath = tempDataDir.toString

      val run = for
        _ <- seedDuckLake(catalogPath, dataPath)
        // Use a short expiry string to exercise the set_option path
        config = makeConfig(
          catalogPath,
          dataPath,
          expireOlderThan = "2 minutes"
        )
        maintenance = DuckLakeMaintenance(config)
        _ <- maintenance.runCheckpoint
        _ <- ZIO.attempt {
          Files.deleteIfExists(tempCatalog)
          Using.resource(Files.walk(tempDataDir)) { stream =>
            stream
              .sorted(java.util.Comparator.reverseOrder())
              .forEach(Files.deleteIfExists(_))
          }
        }
      yield true

      assertZIO(run)(Assertion.isTrue)
    },
    test(
      "startScheduler should not start a fiber when maintenance is disabled"
    ) {
      val tempCatalog =
        Files.createTempFile("ruuvi-maint-disabled-catalog-", ".ducklake")
      val tempDataDir =
        Files.createTempDirectory("ruuvi-maint-disabled-data-")
      Files.deleteIfExists(tempCatalog)

      val config = makeConfig(
        tempCatalog.toString,
        tempDataDir.toString,
        maintenanceEnabled = false
      )

      // startScheduler() should complete immediately (returns ZIO.unit) without
      // connecting to DuckLake or creating any files
      val run = for
        _ <- DuckLakeMaintenance(config).startScheduler()
        // Catalog file should NOT have been created — scheduler is disabled
        catalogCreated <- ZIO.attempt(Files.exists(tempCatalog))
        _ <- ZIO.attempt {
          Files.deleteIfExists(tempCatalog)
          Using.resource(Files.walk(tempDataDir)) { stream =>
            stream
              .sorted(java.util.Comparator.reverseOrder())
              .forEach(Files.deleteIfExists(_))
          }
        }
      yield catalogCreated

      assertZIO(run)(Assertion.isFalse)
    },
    test(
      "startScheduler should fork a daemon fiber when maintenance is enabled"
    ) {
      val tempCatalog =
        Files.createTempFile("ruuvi-maint-enabled-catalog-", ".ducklake")
      val tempDataDir =
        Files.createTempDirectory("ruuvi-maint-enabled-data-")
      Files.deleteIfExists(tempCatalog)

      val catalogPath = tempCatalog.toString
      val dataPath = tempDataDir.toString

      val run = for
        _ <- seedDuckLake(catalogPath, dataPath)
        config = makeConfig(catalogPath, dataPath)
        maintenance = DuckLakeMaintenance(config)
        // startScheduler() should return quickly after forking the daemon fiber.
        // The first run is intentionally delayed by the full interval (9999s) so
        // the fiber is sleeping and no maintenance work happens during this test.
        _ <- maintenance.startScheduler()
        // Give the daemon fiber a brief moment to be scheduled
        _ <- ZIO.sleep(500.millis)
        _ <- ZIO.attempt {
          Files.deleteIfExists(tempCatalog)
          Using.resource(Files.walk(tempDataDir)) { stream =>
            stream
              .sorted(java.util.Comparator.reverseOrder())
              .forEach(Files.deleteIfExists(_))
          }
        }
      yield true

      assertZIO(run)(Assertion.isTrue)
    }
  ) @@ TestAspect.withLiveClock

package sinks

import dto.RuuviTelemetry
import zio.*
import zio.stream.*
import zio.logging.*
import java.sql.{Connection, DriverManager, PreparedStatement}
import java.nio.file.{Files, Paths}

class DuckDBSensorValuesSink(
    dbPath: String,
    tableName: String,
    debugLogging: Boolean,
    val desiredBatchSize: Int,
    val desiredMaxBatchLatencySeconds: Int
) extends SensorValuesSink:

  // Initialize database and table once when sink is created
  private val initializeDatabase: ZIO[Any, Throwable, Unit] =
    ZIO.scoped {
      for
        _ <- validateTableName(tableName)
        conn <- acquireConnection(dbPath)
        _ <- ensureTableExists(conn, tableName)
      yield ()
    }

  def make: ZSink[Any, Throwable, Chunk[RuuviTelemetry], Nothing, Unit] =
    ZSink.unwrap {
      // Initialize database and table once before processing any telemetry
      initializeDatabase.as {
        // Process incoming chunks as batches for efficient insertion
        ZSink.foreach { (chunk: Chunk[RuuviTelemetry]) =>
          val batch: List[RuuviTelemetry] = chunk.toList
          if batch.nonEmpty then
            for
              _ <- ZIO
                .logDebug(
                  s"Processing chunk of ${batch.size} telemetry records for DuckDB (table: $tableName)"
                )
                .when(debugLogging)
              _ <- ZIO.logInfo(
                s"Inserting batch of ${batch.size} records into DuckDB table $tableName"
              )
              _ <- insertBatch(dbPath, tableName, batch)
                .tapError(err =>
                  ZIO.logError(
                    s"Failed to insert batch of ${batch.size} records: ${err.getMessage}"
                  )
                )
            yield ()
          else ZIO.unit
        }
      }
    }

  private def insertBatch(
      path: String,
      table: String,
      telemetryBatch: List[RuuviTelemetry]
  ): ZIO[Any, Throwable, Unit] =
    ZIO.scoped {
      for
        conn <- acquireConnection(path)
        _ <- insertRecordsBatch(conn, table, telemetryBatch)
      yield ()
    }

  private def acquireConnection(
      path: String
  ): ZIO[Scope, Throwable, Connection] =
    ZIO.acquireRelease(
      ZIO.attemptBlocking {
        // Create parent directories if needed (for file-based databases)
        if path != ":memory:" then
          val dbFilePath = Paths.get(path)
          Option(dbFilePath.getParent).foreach(Files.createDirectories(_))

        // Load DuckDB JDBC driver
        Class.forName("org.duckdb.DuckDBDriver")
        // Connect to DuckDB (file-based or in-memory)
        val jdbcUrl =
          if path == ":memory:" then "jdbc:duckdb:" else s"jdbc:duckdb:$path"
        DriverManager.getConnection(jdbcUrl)
      }
    )(conn => ZIO.attemptBlocking(conn.close()).orDie)

  private def ensureTableExists(
      conn: Connection,
      table: String
  ): ZIO[Any, Throwable, Unit] =
    for
      _ <- validateTableName(table)
      _ <- ZIO.attemptBlocking {
        val createTableSQL = s"""
        CREATE TABLE IF NOT EXISTS $table (
          temperature_millicelsius INTEGER NOT NULL,
          humidity INTEGER NOT NULL,
          pressure INTEGER NOT NULL,
          battery_potential INTEGER NOT NULL,
          tx_power INTEGER NOT NULL,
          movement_counter INTEGER NOT NULL,
          measurement_sequence_number INTEGER NOT NULL,
          measurement_ts_ms BIGINT NOT NULL,
          mac_address VARCHAR NOT NULL
        )
      """
        val stmt = conn.createStatement()
        try stmt.execute(createTableSQL)
        finally stmt.close()
      }
    yield ()

  private def validateTableName(name: String): ZIO[Any, Throwable, Unit] =
    ZIO.attempt {
      // Validate table name to prevent SQL injection
      // Allow only alphanumeric characters and underscores
      if !name.matches("^[a-zA-Z_][a-zA-Z0-9_]*$") then
        throw new IllegalArgumentException(
          s"Invalid table name: '$name'. Table name must start with a letter or underscore and contain only alphanumeric characters and underscores."
        )
    }

  private def insertRecordsBatch(
      conn: Connection,
      table: String,
      telemetryBatch: List[RuuviTelemetry]
  ): ZIO[Any, Throwable, Unit] =
    ZIO.attemptBlocking {
      val insertSQL = s"""
        INSERT INTO $table (
          temperature_millicelsius,
          humidity,
          pressure,
          battery_potential,
          tx_power,
          movement_counter,
          measurement_sequence_number,
          measurement_ts_ms,
          mac_address
        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
      """
      val pstmt = conn.prepareStatement(insertSQL)
      try
        telemetryBatch.foreach { telemetry =>
          pstmt.setInt(1, telemetry.temperatureMillicelsius)
          pstmt.setInt(2, telemetry.humidity)
          pstmt.setInt(3, telemetry.pressure)
          pstmt.setInt(4, telemetry.batteryPotential)
          pstmt.setInt(5, telemetry.txPower)
          pstmt.setInt(6, telemetry.movementCounter)
          pstmt.setInt(7, telemetry.measurementSequenceNumber)
          pstmt.setLong(8, telemetry.measurementTsMs)
          // Store MAC address in standard format (e.g., "FE:26:88:7A:66:66")
          val macAddress = telemetry.macAddress
            .map(b => f"${b & 0xff}%02X")
            .mkString(":")
          pstmt.setString(9, macAddress)
          pstmt.addBatch()
        }
        pstmt.executeBatch()
      finally pstmt.close()
    }

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
    debugLogging: Boolean
) extends SensorValuesSink:

  def make: ZSink[Any, Throwable, RuuviTelemetry, Nothing, Unit] =
    ZSink.foreach { telemetry =>
      for
        _ <- ZIO
          .logDebug(
            s"Writing telemetry to DuckDB (table: $tableName): ${telemetry.macAddress.mkString(",")}"
          )
          .when(debugLogging)
        _ <- insertTelemetry(dbPath, tableName, telemetry)
      yield ()
    }

  private def insertTelemetry(
      path: String,
      table: String,
      telemetry: RuuviTelemetry
  ): ZIO[Any, Throwable, Unit] =
    ZIO.scoped {
      for
        conn <- acquireConnection(path)
        _ <- ensureTableExists(conn, table)
        _ <- insertRecord(conn, table, telemetry)
      yield ()
    }

  private def acquireConnection(
      path: String
  ): ZIO[Scope, Throwable, Connection] =
    ZIO.acquireRelease(
      ZIO.attemptBlocking {
        // Create parent directories if needed (for file-based databases)
        if (path != ":memory:")
          val dbFilePath = Paths.get(path)
          Option(dbFilePath.getParent).foreach(Files.createDirectories(_))

        // Load DuckDB JDBC driver
        Class.forName("org.duckdb.DuckDBDriver")
        // Connect to DuckDB (file-based or in-memory)
        val jdbcUrl =
          if (path == ":memory:") "jdbc:duckdb:" else s"jdbc:duckdb:$path"
        DriverManager.getConnection(jdbcUrl)
      }
    )(conn => ZIO.attemptBlocking(conn.close()).orDie)

  private def ensureTableExists(
      conn: Connection,
      table: String
  ): ZIO[Any, Throwable, Unit] =
    ZIO.attemptBlocking {
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

  private def insertRecord(
      conn: Connection,
      table: String,
      telemetry: RuuviTelemetry
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
        pstmt.setInt(1, telemetry.temperatureMillicelsius)
        pstmt.setInt(2, telemetry.humidity)
        pstmt.setInt(3, telemetry.pressure)
        pstmt.setInt(4, telemetry.batteryPotential)
        pstmt.setInt(5, telemetry.txPower)
        pstmt.setInt(6, telemetry.movementCounter)
        pstmt.setInt(7, telemetry.measurementSequenceNumber)
        pstmt.setLong(8, telemetry.measurementTsMs)
        pstmt.setString(9, telemetry.macAddress.mkString(","))
        pstmt.executeUpdate()
      finally pstmt.close()
    }

package sinks

import zio.*
import zio.test.*
import zio.stream.*
import dto.RuuviTelemetry
import java.sql.{DriverManager, ResultSet}
import java.nio.file.{Files, Paths}

object DuckDBSensorValuesSinkSpec extends ZIOSpecDefault:

  def spec = suite("DuckDBSensorValuesSinkSpec")(
    test(
      "DuckDBSensorValuesSink should write telemetry to in-memory database"
    ) {
      // Note: For in-memory testing, we need to use a file-based database
      // because DuckDB in-memory databases are per-connection and don't persist
      // between different connection instances
      val tempFile = Files.createTempFile("ruuvi-test-memory-", ".db")
      Files.deleteIfExists(tempFile)

      val testTelemetry1 = RuuviTelemetry(
        batteryPotential = 2335,
        humidity = 653675,
        macAddress = Seq(254, 38, 136, 122, 102, 102),
        measurementTsMs = 1693460525699L,
        measurementSequenceNumber = 53300,
        movementCounter = 2,
        pressure = 100755,
        temperatureMillicelsius = -29020,
        txPower = 4
      )
      val testTelemetry2 = RuuviTelemetry(
        batteryPotential = 2176,
        humidity = 576425,
        macAddress = Seq(213, 18, 52, 102, 20, 20),
        measurementTsMs = 1693460525701L,
        measurementSequenceNumber = 1589,
        movementCounter = 79,
        pressure = 100556,
        temperatureMillicelsius = 22080,
        txPower = 4
      )

      val testData = List(testTelemetry1, testTelemetry2)
      val sink =
        DuckDBSensorValuesSink(
          tempFile.toString,
          "test_telemetry",
          debugLogging = false
        )

      val writeAndRead = for
        // Write test data to database
        _ <- ZStream.fromIterable(testData).run(sink.make)

        // Read data from database
        records <- ZIO.attemptBlocking {
          Class.forName("org.duckdb.DuckDBDriver")
          val conn =
            DriverManager.getConnection(s"jdbc:duckdb:${tempFile.toString}")
          val stmt = conn.createStatement()
          val rs = stmt.executeQuery(
            "SELECT * FROM test_telemetry ORDER BY measurement_ts_ms"
          )

          val results = scala.collection.mutable.ListBuffer[RuuviTelemetry]()
          while rs.next() do
            results += RuuviTelemetry(
              temperatureMillicelsius = rs.getInt("temperature_millicelsius"),
              humidity = rs.getInt("humidity"),
              pressure = rs.getInt("pressure"),
              batteryPotential = rs.getInt("battery_potential"),
              txPower = rs.getInt("tx_power"),
              movementCounter = rs.getInt("movement_counter"),
              measurementSequenceNumber =
                rs.getInt("measurement_sequence_number"),
              measurementTsMs = rs.getLong("measurement_ts_ms"),
              macAddress =
                rs.getString("mac_address").split(",").map(_.toShort).toSeq
            )

          rs.close()
          stmt.close()
          conn.close()
          results.toList
        }

        // Clean up
        _ <- ZIO.attempt(Files.deleteIfExists(tempFile))
      yield records

      assertZIO(writeAndRead)(
        Assertion.hasSameElements(testData)
      )
    },
    test("DuckDBSensorValuesSink should create database file and table") {
      val tempFile = Files.createTempFile("ruuvi-test-", ".db")
      Files.deleteIfExists(tempFile) // Delete it so DuckDB can create it fresh

      val testTelemetry = RuuviTelemetry(
        batteryPotential = 2335,
        humidity = 653675,
        macAddress = Seq(254, 38, 136, 122, 102, 102),
        measurementTsMs = 1693460525699L,
        measurementSequenceNumber = 53300,
        movementCounter = 2,
        pressure = 100755,
        temperatureMillicelsius = -29020,
        txPower = 4
      )

      val sink =
        DuckDBSensorValuesSink(
          tempFile.toString,
          "telemetry",
          debugLogging = false
        )

      val writeAndVerify = for
        _ <- ZStream.fromIterable(List(testTelemetry)).run(sink.make)

        // Verify file exists
        fileExists <- ZIO.attempt(Files.exists(tempFile))

        // Read data back
        record <- ZIO.attemptBlocking {
          Class.forName("org.duckdb.DuckDBDriver")
          val conn =
            DriverManager.getConnection(s"jdbc:duckdb:${tempFile.toString}")
          val stmt = conn.createStatement()
          val rs = stmt.executeQuery("SELECT * FROM telemetry LIMIT 1")

          rs.next()
          val result = RuuviTelemetry(
            temperatureMillicelsius = rs.getInt("temperature_millicelsius"),
            humidity = rs.getInt("humidity"),
            pressure = rs.getInt("pressure"),
            batteryPotential = rs.getInt("battery_potential"),
            txPower = rs.getInt("tx_power"),
            movementCounter = rs.getInt("movement_counter"),
            measurementSequenceNumber =
              rs.getInt("measurement_sequence_number"),
            measurementTsMs = rs.getLong("measurement_ts_ms"),
            macAddress =
              rs.getString("mac_address").split(",").map(_.toShort).toSeq
          )

          rs.close()
          stmt.close()
          conn.close()
          result
        }

        // Clean up
        _ <- ZIO.attempt(Files.deleteIfExists(tempFile))
      yield (fileExists, record)

      assertZIO(writeAndVerify) {
        Assertion.equalTo((true, testTelemetry))
      }
    },
    test(
      "DuckDBSensorValuesSink should create parent directories if they don't exist"
    ) {
      val tempDir = Files.createTempDirectory("ruuvi-test-dir-")
      val nestedPath = tempDir.resolve("nested/path/telemetry.db")

      val testTelemetry = RuuviTelemetry(
        batteryPotential = 2335,
        humidity = 653675,
        macAddress = Seq(254, 38, 136, 122, 102, 102),
        measurementTsMs = 1693460525699L,
        measurementSequenceNumber = 53300,
        movementCounter = 2,
        pressure = 100755,
        temperatureMillicelsius = -29020,
        txPower = 4
      )

      val sink =
        DuckDBSensorValuesSink(
          nestedPath.toString,
          "telemetry",
          debugLogging = false
        )

      val writeAndVerify = for
        _ <- ZStream.fromIterable(List(testTelemetry)).run(sink.make)

        fileExists <- ZIO.attempt(Files.exists(nestedPath))

        record <- ZIO.attemptBlocking {
          Class.forName("org.duckdb.DuckDBDriver")
          val conn =
            DriverManager.getConnection(s"jdbc:duckdb:${nestedPath.toString}")
          val stmt = conn.createStatement()
          val rs = stmt.executeQuery("SELECT * FROM telemetry LIMIT 1")

          rs.next()
          val result = RuuviTelemetry(
            temperatureMillicelsius = rs.getInt("temperature_millicelsius"),
            humidity = rs.getInt("humidity"),
            pressure = rs.getInt("pressure"),
            batteryPotential = rs.getInt("battery_potential"),
            txPower = rs.getInt("tx_power"),
            movementCounter = rs.getInt("movement_counter"),
            measurementSequenceNumber =
              rs.getInt("measurement_sequence_number"),
            measurementTsMs = rs.getLong("measurement_ts_ms"),
            macAddress =
              rs.getString("mac_address").split(",").map(_.toInt.toShort).toSeq
          )

          rs.close()
          stmt.close()
          conn.close()
          result
        }

        // Clean up (delete in correct order: file, then directories from innermost to outermost)
        _ <- ZIO.attempt {
          Files.deleteIfExists(nestedPath)
          Files.deleteIfExists(nestedPath.getParent)
          Files.deleteIfExists(nestedPath.getParent.getParent)
          Files.deleteIfExists(tempDir)
        }
      yield (fileExists, record)

      assertZIO(writeAndVerify) {
        Assertion.equalTo((true, testTelemetry))
      }
    },
    test("DuckDBSensorValuesSink should append to existing database") {
      val tempFile = Files.createTempFile("ruuvi-test-append-", ".db")
      Files.deleteIfExists(tempFile)

      val firstBatch = List(
        RuuviTelemetry(
          batteryPotential = 2335,
          humidity = 653675,
          macAddress = Seq(254, 38, 136, 122, 102, 102),
          measurementTsMs = 1693460525699L,
          measurementSequenceNumber = 53300,
          movementCounter = 2,
          pressure = 100755,
          temperatureMillicelsius = -29020,
          txPower = 4
        )
      )

      val secondBatch = List(
        RuuviTelemetry(
          batteryPotential = 2176,
          humidity = 576425,
          macAddress = Seq(213, 18, 52, 102, 20, 20),
          measurementTsMs = 1693460525701L,
          measurementSequenceNumber = 1589,
          movementCounter = 79,
          pressure = 100556,
          temperatureMillicelsius = 22080,
          txPower = 4
        )
      )

      val sink =
        DuckDBSensorValuesSink(
          tempFile.toString,
          "telemetry",
          debugLogging = false
        )

      val writeAndRead = for
        // Write first batch
        _ <- ZStream.fromIterable(firstBatch).run(sink.make)

        // Write second batch (should append)
        _ <- ZStream.fromIterable(secondBatch).run(sink.make)

        // Read all records
        records <- ZIO.attemptBlocking {
          Class.forName("org.duckdb.DuckDBDriver")
          val conn =
            DriverManager.getConnection(s"jdbc:duckdb:${tempFile.toString}")
          val stmt = conn.createStatement()
          val rs = stmt.executeQuery(
            "SELECT * FROM telemetry ORDER BY measurement_ts_ms"
          )

          val results = scala.collection.mutable.ListBuffer[RuuviTelemetry]()
          while rs.next() do
            results += RuuviTelemetry(
              temperatureMillicelsius = rs.getInt("temperature_millicelsius"),
              humidity = rs.getInt("humidity"),
              pressure = rs.getInt("pressure"),
              batteryPotential = rs.getInt("battery_potential"),
              txPower = rs.getInt("tx_power"),
              movementCounter = rs.getInt("movement_counter"),
              measurementSequenceNumber =
                rs.getInt("measurement_sequence_number"),
              measurementTsMs = rs.getLong("measurement_ts_ms"),
              macAddress =
                rs.getString("mac_address").split(",").map(_.toInt.toShort).toSeq
            )

          rs.close()
          stmt.close()
          conn.close()
          results.toList
        }

        // Clean up
        _ <- ZIO.attempt(Files.deleteIfExists(tempFile))
      yield records

      assertZIO(writeAndRead)(
        Assertion.hasSameElements(firstBatch ++ secondBatch)
      )
    },
    test("DuckDBSensorValuesSink should reject invalid table names") {
      val invalidTableNames = List(
        "telemetry; DROP TABLE users--",
        "telemetry'--",
        "telemetry OR 1=1",
        "123invalid",
        "table-name",
        "table.name"
      )

      val testTelemetry = RuuviTelemetry(
        batteryPotential = 2335,
        humidity = 653675,
        macAddress = Seq(254, 38, 136, 122, 102, 102),
        measurementTsMs = 1693460525699L,
        measurementSequenceNumber = 53300,
        movementCounter = 2,
        pressure = 100755,
        temperatureMillicelsius = -29020,
        txPower = 4
      )

      val tests = ZIO.foreach(invalidTableNames) { invalidName =>
        val sink = DuckDBSensorValuesSink(
          ":memory:",
          invalidName,
          debugLogging = false
        )

        ZStream
          .fromIterable(List(testTelemetry))
          .run(sink.make)
          .flip
          .map(_.getMessage)
      }

      assertZIO(tests)(
        Assertion.forall(
          Assertion.containsString("Invalid table name")
        )
      )
    }
  )

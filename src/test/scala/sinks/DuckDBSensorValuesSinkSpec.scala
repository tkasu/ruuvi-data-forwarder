package sinks

import zio.*
import zio.test.*
import zio.stream.*
import dto.RuuviTelemetry
import _root_.config.{DuckLakeConfig, CatalogType}
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
          debugLogging = false,
          desiredBatchSize = 5,
          desiredMaxBatchLatencySeconds = 30
        )

      val writeAndRead = for
        // Write test data to database (apply batching as the forwarder would)
        _ <- ZStream
          .fromIterable(testData)
          .groupedWithin(
            sink.desiredBatchSize,
            zio.Duration.fromSeconds(sink.desiredMaxBatchLatencySeconds.toLong)
          )
          .run(sink.make)

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
              macAddress = rs
                .getString("mac_address")
                .split(":")
                .map(Integer.parseInt(_, 16).toShort)
                .toSeq
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
          debugLogging = false,
          desiredBatchSize = 5,
          desiredMaxBatchLatencySeconds = 30
        )

      val writeAndVerify = for
        _ <- ZStream
          .fromIterable(List(testTelemetry))
          .groupedWithin(
            sink.desiredBatchSize,
            zio.Duration.fromSeconds(sink.desiredMaxBatchLatencySeconds.toLong)
          )
          .run(sink.make)

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
            macAddress = rs
              .getString("mac_address")
              .split(":")
              .map(Integer.parseInt(_, 16).toShort)
              .toSeq
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
          debugLogging = false,
          desiredBatchSize = 5,
          desiredMaxBatchLatencySeconds = 30
        )

      val writeAndVerify = for
        _ <- ZStream
          .fromIterable(List(testTelemetry))
          .groupedWithin(
            sink.desiredBatchSize,
            zio.Duration.fromSeconds(sink.desiredMaxBatchLatencySeconds.toLong)
          )
          .run(sink.make)

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
            macAddress = rs
              .getString("mac_address")
              .split(":")
              .map(Integer.parseInt(_, 16).toShort)
              .toSeq
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
          debugLogging = false,
          desiredBatchSize = 5,
          desiredMaxBatchLatencySeconds = 30
        )

      val writeAndRead = for
        // Write first batch
        _ <- ZStream
          .fromIterable(firstBatch)
          .groupedWithin(
            sink.desiredBatchSize,
            zio.Duration.fromSeconds(sink.desiredMaxBatchLatencySeconds.toLong)
          )
          .run(sink.make)

        // Write second batch (should append)
        _ <- ZStream
          .fromIterable(secondBatch)
          .groupedWithin(
            sink.desiredBatchSize,
            zio.Duration.fromSeconds(sink.desiredMaxBatchLatencySeconds.toLong)
          )
          .run(sink.make)

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
              macAddress = rs
                .getString("mac_address")
                .split(":")
                .map(Integer.parseInt(_, 16).toShort)
                .toSeq
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
          debugLogging = false,
          desiredBatchSize = 5,
          desiredMaxBatchLatencySeconds = 30
        )

        ZStream
          .fromIterable(List(testTelemetry))
          .groupedWithin(
            sink.desiredBatchSize,
            zio.Duration.fromSeconds(sink.desiredMaxBatchLatencySeconds.toLong)
          )
          .run(sink.make)
          .flip
          .map(_.getMessage)
      }

      assertZIO(tests)(
        Assertion.forall(
          Assertion.containsString("Invalid table name")
        )
      )
    },
    test("DuckDBSensorValuesSink should batch multiple records efficiently") {
      val tempFile = Files.createTempFile("ruuvi-test-batch-", ".db")
      Files.deleteIfExists(tempFile)

      // Create a batch of 10 telemetry records
      val testBatch = (1 to 10).map { i =>
        RuuviTelemetry(
          batteryPotential = 2000 + i,
          humidity = 500000 + i * 1000,
          macAddress = Seq(254, 38, 136, 122, 102, i.toShort),
          measurementTsMs = 1693460525699L + i,
          measurementSequenceNumber = 53300 + i,
          movementCounter = i,
          pressure = 100000 + i * 100,
          temperatureMillicelsius = 20000 + i * 100,
          txPower = 4
        )
      }.toList

      val sink =
        DuckDBSensorValuesSink(
          tempFile.toString,
          "telemetry",
          debugLogging = false,
          desiredBatchSize = 5,
          desiredMaxBatchLatencySeconds = 30
        )

      val writeAndVerify = for
        // Write all records in one batch
        _ <- ZStream
          .fromIterable(testBatch)
          .groupedWithin(
            sink.desiredBatchSize,
            zio.Duration.fromSeconds(sink.desiredMaxBatchLatencySeconds.toLong)
          )
          .run(sink.make)

        // Verify all records were written
        count <- ZIO.attemptBlocking {
          Class.forName("org.duckdb.DuckDBDriver")
          val conn =
            DriverManager.getConnection(s"jdbc:duckdb:${tempFile.toString}")
          val stmt = conn.createStatement()
          val rs = stmt.executeQuery("SELECT COUNT(*) as count FROM telemetry")

          rs.next()
          val result = rs.getInt("count")

          rs.close()
          stmt.close()
          conn.close()
          result
        }

        // Clean up
        _ <- ZIO.attempt(Files.deleteIfExists(tempFile))
      yield count

      assertZIO(writeAndVerify)(Assertion.equalTo(10))
    },
    test(
      "DuckDBSensorValuesSink should insert multiple records in a batch"
    ) {
      val tempFile = Files.createTempFile("ruuvi-test-direct-batch-", ".db")
      Files.deleteIfExists(tempFile)

      val testBatch = List(
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
        ),
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
        ),
        RuuviTelemetry(
          batteryPotential = 2500,
          humidity = 600000,
          macAddress = Seq(100, 200, 50, 75, 125, 150),
          measurementTsMs = 1693460525702L,
          measurementSequenceNumber = 2000,
          movementCounter = 100,
          pressure = 101000,
          temperatureMillicelsius = 25000,
          txPower = 4
        )
      )

      val sink =
        DuckDBSensorValuesSink(
          tempFile.toString,
          "telemetry",
          debugLogging = false,
          desiredBatchSize = 5,
          desiredMaxBatchLatencySeconds = 30
        )

      val writeAndRead = for
        // Insert batch using the sink's make method with groupedWithin
        _ <- ZStream
          .fromIterable(testBatch)
          .groupedWithin(
            sink.desiredBatchSize,
            zio.Duration.fromSeconds(sink.desiredMaxBatchLatencySeconds.toLong)
          )
          .run(sink.make)

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
              macAddress = rs
                .getString("mac_address")
                .split(":")
                .map(Integer.parseInt(_, 16).toShort)
                .toSeq
            )

          rs.close()
          stmt.close()
          conn.close()
          results.toList
        }

        // Clean up
        _ <- ZIO.attempt(Files.deleteIfExists(tempFile))
      yield records

      assertZIO(writeAndRead)(Assertion.hasSameElements(testBatch))
    },
    test(
      "DuckDBSensorValuesSink with groupedWithin should batch records by size and time"
    ) {
      val tempFile = Files.createTempFile("ruuvi-test-grouped-within-", ".db")
      Files.deleteIfExists(tempFile)

      // Create a small batch of 3 records (less than batch size of 5)
      // This tests the timeout-based flushing
      val testBatch = List(
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
        ),
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
        ),
        RuuviTelemetry(
          batteryPotential = 2500,
          humidity = 600000,
          macAddress = Seq(100, 200, 50, 75, 125, 150),
          measurementTsMs = 1693460525702L,
          measurementSequenceNumber = 2000,
          movementCounter = 100,
          pressure = 101000,
          temperatureMillicelsius = 25000,
          txPower = 4
        )
      )

      val sink =
        DuckDBSensorValuesSink(
          tempFile.toString,
          "telemetry",
          debugLogging = false,
          desiredBatchSize = 5,
          desiredMaxBatchLatencySeconds = 1 // 1 second timeout for test
        )

      val writeWithBatching = for
        // Simulate stream processing with groupedWithin
        _ <- ZStream
          .fromIterable(testBatch)
          .groupedWithin(
            sink.desiredBatchSize,
            zio.Duration.fromSeconds(sink.desiredMaxBatchLatencySeconds.toLong)
          )
          .run(sink.make)

        // Verify all records were written
        count <- ZIO.attemptBlocking {
          Class.forName("org.duckdb.DuckDBDriver")
          val conn =
            DriverManager.getConnection(s"jdbc:duckdb:${tempFile.toString}")
          val stmt = conn.createStatement()
          val rs = stmt.executeQuery("SELECT COUNT(*) as count FROM telemetry")

          rs.next()
          val result = rs.getInt("count")

          rs.close()
          stmt.close()
          conn.close()
          result
        }

        // Clean up
        _ <- ZIO.attempt(Files.deleteIfExists(tempFile))
      yield count

      assertZIO(writeWithBatching)(Assertion.equalTo(3))
    },
    test(
      "DuckDBSensorValuesSink with DuckLake (DuckDB catalog) should write telemetry"
    ) {
      val tempCatalog =
        Files.createTempFile("ruuvi-test-ducklake-catalog-", ".ducklake")
      val tempDataDir = Files.createTempDirectory("ruuvi-test-ducklake-data-")
      Files.deleteIfExists(tempCatalog)

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

      val ducklakeConfig = DuckLakeConfig(
        catalogType = CatalogType.DuckDB,
        catalogPath = tempCatalog.toString,
        dataPath = tempDataDir.toString
      )

      val sink = DuckDBSensorValuesSink(
        dbPath = "", // Not used in DuckLake mode
        tableName = "telemetry",
        debugLogging = false,
        desiredBatchSize = 5,
        desiredMaxBatchLatencySeconds = 30,
        ducklakeEnabled = true,
        ducklakeConfig = Some(ducklakeConfig)
      )

      val writeAndRead = for
        _ <- ZStream
          .fromIterable(List(testTelemetry))
          .groupedWithin(
            sink.desiredBatchSize,
            zio.Duration.fromSeconds(sink.desiredMaxBatchLatencySeconds.toLong)
          )
          .run(sink.make)

        // Read data from DuckLake
        record <- ZIO.attemptBlocking {
          Class.forName("org.duckdb.DuckDBDriver")
          val conn = DriverManager.getConnection("jdbc:duckdb:")

          // Install and load extensions
          val installStmt = conn.createStatement()
          installStmt.execute("INSTALL ducklake")
          installStmt.execute("LOAD ducklake")
          installStmt.close()

          // Attach DuckLake
          val attachStmt = conn.createStatement()
          attachStmt.execute(
            s"ATTACH 'ducklake:${tempCatalog.toString}' AS ducklake (DATA_PATH '${tempDataDir.toString}')"
          )
          attachStmt.close()

          val stmt = conn.createStatement()
          val rs = stmt.executeQuery("SELECT * FROM ducklake.telemetry LIMIT 1")

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
            macAddress = rs
              .getString("mac_address")
              .split(":")
              .map(Integer.parseInt(_, 16).toShort)
              .toSeq
          )

          rs.close()
          stmt.close()
          conn.close()
          result
        }

        // Clean up
        _ <- ZIO.attempt {
          Files.deleteIfExists(tempCatalog)
          // Clean up data directory and its contents
          Files
            .walk(tempDataDir)
            .sorted(java.util.Comparator.reverseOrder())
            .forEach(Files.deleteIfExists(_))
        }
      yield record

      assertZIO(writeAndRead)(Assertion.equalTo(testTelemetry))
    },
    test(
      "DuckDBSensorValuesSink with DuckLake should append to existing data"
    ) {
      val tempCatalog =
        Files.createTempFile("ruuvi-test-ducklake-append-catalog-", ".ducklake")
      val tempDataDir =
        Files.createTempDirectory("ruuvi-test-ducklake-append-data-")
      Files.deleteIfExists(tempCatalog)

      val firstTelemetry = RuuviTelemetry(
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

      val secondTelemetry = RuuviTelemetry(
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

      val ducklakeConfig = DuckLakeConfig(
        catalogType = CatalogType.DuckDB,
        catalogPath = tempCatalog.toString,
        dataPath = tempDataDir.toString
      )

      val sink = DuckDBSensorValuesSink(
        dbPath = "",
        tableName = "telemetry",
        debugLogging = false,
        desiredBatchSize = 5,
        desiredMaxBatchLatencySeconds = 30,
        ducklakeEnabled = true,
        ducklakeConfig = Some(ducklakeConfig)
      )

      val writeAndRead = for
        // Write first record
        _ <- ZStream
          .fromIterable(List(firstTelemetry))
          .groupedWithin(
            sink.desiredBatchSize,
            zio.Duration.fromSeconds(sink.desiredMaxBatchLatencySeconds.toLong)
          )
          .run(sink.make)

        // Write second record
        _ <- ZStream
          .fromIterable(List(secondTelemetry))
          .groupedWithin(
            sink.desiredBatchSize,
            zio.Duration.fromSeconds(sink.desiredMaxBatchLatencySeconds.toLong)
          )
          .run(sink.make)

        // Read all records
        count <- ZIO.attemptBlocking {
          Class.forName("org.duckdb.DuckDBDriver")
          val conn = DriverManager.getConnection("jdbc:duckdb:")

          val installStmt = conn.createStatement()
          installStmt.execute("INSTALL ducklake")
          installStmt.execute("LOAD ducklake")
          installStmt.close()

          val attachStmt = conn.createStatement()
          attachStmt.execute(
            s"ATTACH 'ducklake:${tempCatalog.toString}' AS ducklake (DATA_PATH '${tempDataDir.toString}')"
          )
          attachStmt.close()

          val stmt = conn.createStatement()
          val rs = stmt.executeQuery(
            "SELECT COUNT(*) as count FROM ducklake.telemetry"
          )

          rs.next()
          val result = rs.getInt("count")

          rs.close()
          stmt.close()
          conn.close()
          result
        }

        // Clean up
        _ <- ZIO.attempt {
          Files.deleteIfExists(tempCatalog)
          Files
            .walk(tempDataDir)
            .sorted(java.util.Comparator.reverseOrder())
            .forEach(Files.deleteIfExists(_))
        }
      yield count

      assertZIO(writeAndRead)(Assertion.equalTo(2))
    }
  )

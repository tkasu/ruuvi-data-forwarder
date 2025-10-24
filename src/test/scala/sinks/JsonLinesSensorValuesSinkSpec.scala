package sinks

import zio.*
import zio.json.*
import zio.test.*
import zio.stream.*
import dto.RuuviTelemetry
import java.nio.file.{Files, Paths}
import scala.io.Source

object JsonLinesSensorValuesSinkSpec extends ZIOSpecDefault:

  def spec = suite("JsonLinesSensorValuesSinkSpec")(
    test("JsonLinesSensorValuesSink should write telemetry to file") {
      val tempFile = Files.createTempFile("ruuvi-test-", ".jsonl")

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
      val sink = JsonLinesSensorValuesSink(tempFile.toString, debugLogging = false)

      val writeAndRead = for
        // Write test data to file
        _ <- ZStream.fromIterable(testData).run(sink.make)

        // Read file contents
        fileContent <- ZIO.attempt {
          val source = Source.fromFile(tempFile.toFile)
          try source.getLines().toList
          finally source.close()
        }

        // Parse JSON lines
        parsedTelemetry <- ZIO.foreach(fileContent)(line =>
          ZIO.fromEither(line.fromJson[RuuviTelemetry])
            .mapError(err => new RuntimeException(s"Failed to parse JSON: $err"))
        )

        // Clean up temp file
        _ <- ZIO.attempt(Files.deleteIfExists(tempFile))
      yield parsedTelemetry

      assertZIO(writeAndRead)(
        Assertion.hasSameElements(testData)
      )
    },

    test("JsonLinesSensorValuesSink should create parent directories if they don't exist") {
      val tempDir = Files.createTempDirectory("ruuvi-test-dir-")
      val nestedPath = tempDir.resolve("nested/path/telemetry.jsonl")

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

      val sink = JsonLinesSensorValuesSink(nestedPath.toString, debugLogging = false)

      val writeAndVerify = for
        _ <- ZStream.fromIterable(List(testTelemetry)).run(sink.make)

        fileExists <- ZIO.attempt(Files.exists(nestedPath))

        fileContent <- ZIO.attempt {
          val source = Source.fromFile(nestedPath.toFile)
          try source.getLines().toList
          finally source.close()
        }

        parsedTelemetry <- ZIO.fromEither(
          fileContent.head.fromJson[RuuviTelemetry]
        ).mapError(err => new RuntimeException(s"Failed to parse JSON: $err"))

        // Clean up (delete in correct order: file, then directories from innermost to outermost)
        _ <- ZIO.attempt {
          Files.deleteIfExists(nestedPath)
          Files.deleteIfExists(nestedPath.getParent)
          Files.deleteIfExists(nestedPath.getParent.getParent)
          Files.deleteIfExists(tempDir)
        }
      yield (fileExists, parsedTelemetry)

      assertZIO(writeAndVerify) {
        Assertion.equalTo((true, testTelemetry))
      }
    },

    test("JsonLinesSensorValuesSink should append to existing file") {
      val tempFile = Files.createTempFile("ruuvi-test-append-", ".jsonl")

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

      val sink = JsonLinesSensorValuesSink(tempFile.toString, debugLogging = false)

      val writeAndRead = for
        // Write first batch
        _ <- ZStream.fromIterable(firstBatch).run(sink.make)

        // Write second batch (should append)
        _ <- ZStream.fromIterable(secondBatch).run(sink.make)

        // Read all lines
        fileContent <- ZIO.attempt {
          val source = Source.fromFile(tempFile.toFile)
          try source.getLines().toList
          finally source.close()
        }

        // Parse all lines
        parsedTelemetry <- ZIO.foreach(fileContent)(line =>
          ZIO.fromEither(line.fromJson[RuuviTelemetry])
            .mapError(err => new RuntimeException(s"Failed to parse JSON: $err"))
        )

        // Clean up
        _ <- ZIO.attempt(Files.deleteIfExists(tempFile))
      yield parsedTelemetry

      assertZIO(writeAndRead)(
        Assertion.hasSameElements(firstBatch ++ secondBatch)
      )
    }
  )

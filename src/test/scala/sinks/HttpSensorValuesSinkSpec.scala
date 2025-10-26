package sinks

import zio.*
import zio.test.*
import zio.stream.*
import zio.http.*
import dto.RuuviTelemetry

object HttpSensorValuesSinkSpec extends ZIOSpecDefault:

  def spec = suite("HttpSensorValuesSinkSpec")(
    test(
      "should convert telemetry to correct API format with 7 measurement types"
    ) {
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

      val sink = HttpSensorValuesSink(
        "http://localhost:8081",
        "test-sensor",
        debugLogging = false,
        timeoutSeconds = 30,
        maxRetries = 3
      )

      val result = sink.convertToApiFormat(testTelemetry)

      assertTrue(result.length == 7) &&
      assertTrue(result.exists(_.telemetry_type == "temperature")) &&
      assertTrue(result.exists(_.telemetry_type == "humidity")) &&
      assertTrue(result.exists(_.telemetry_type == "pressure")) &&
      assertTrue(result.exists(_.telemetry_type == "battery")) &&
      assertTrue(result.exists(_.telemetry_type == "tx_power")) &&
      assertTrue(result.exists(_.telemetry_type == "movement_counter")) &&
      assertTrue(
        result.exists(_.telemetry_type == "measurement_sequence_number")
      )
    },
    test(
      "should convert temperature with correct value and MAC address format"
    ) {
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

      val sink = HttpSensorValuesSink(
        "http://localhost:8081",
        "test-sensor",
        debugLogging = false
      )

      val result = sink.convertToApiFormat(testTelemetry)
      val tempData = result.find(_.telemetry_type == "temperature")
      val measurement = tempData.flatMap(_.data.headOption)

      assertTrue(measurement.isDefined) &&
      assertTrue(measurement.get.value == -29.02) &&
      assertTrue(measurement.get.sensor_name == "FE:26:88:7A:66:66") &&
      assertTrue(measurement.get.timestamp == 1693460525699L)
    },
    test("should convert humidity with correct value") {
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

      val sink = HttpSensorValuesSink(
        "http://localhost:8081",
        "test-sensor",
        debugLogging = false
      )

      val result = sink.convertToApiFormat(testTelemetry)
      val humidityData = result.find(_.telemetry_type == "humidity")
      val measurement = humidityData.flatMap(_.data.headOption)

      assertTrue(measurement.isDefined) &&
      assertTrue(measurement.get.value == 65.3675)
    },
    test("should convert battery with correct value") {
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

      val sink = HttpSensorValuesSink(
        "http://localhost:8081",
        "test-sensor",
        debugLogging = false
      )

      val result = sink.convertToApiFormat(testTelemetry)
      val batteryData = result.find(_.telemetry_type == "battery")
      val measurement = batteryData.flatMap(_.data.headOption)

      assertTrue(measurement.isDefined) &&
      assertTrue(measurement.get.value == 2.335)
    },
    test("should reject invalid MAC address length") {
      val testTelemetry = RuuviTelemetry(
        batteryPotential = 2335,
        humidity = 653675,
        macAddress = Seq(254, 38, 136, 122, 102), // Only 5 bytes instead of 6
        measurementTsMs = 1693460525699L,
        measurementSequenceNumber = 53300,
        movementCounter = 2,
        pressure = 100755,
        temperatureMillicelsius = -29020,
        txPower = 4
      )

      val sink = HttpSensorValuesSink(
        "http://localhost:8081",
        "test-sensor",
        debugLogging = false
      )

      val result = ZIO.attempt(sink.convertToApiFormat(testTelemetry))

      assertZIO(result.flip)(
        Assertion.isSubtype[IllegalArgumentException](
          Assertion.hasMessage(
            Assertion.containsString(
              "Invalid MAC address length: 5, expected 6"
            )
          )
        )
      )
    },
    test("should handle all measurement types with correct sensor_name") {
      val testTelemetry = RuuviTelemetry(
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

      val sink = HttpSensorValuesSink(
        "http://localhost:8081",
        "test-sensor",
        debugLogging = false
      )

      val result = sink.convertToApiFormat(testTelemetry)

      // All measurements should have the same MAC address
      val allMacAddresses = result.flatMap(_.data.map(_.sensor_name)).distinct

      assertTrue(allMacAddresses.length == 1) &&
      assertTrue(allMacAddresses.head == "D5:12:34:66:14:14")
    }
  )

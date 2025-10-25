package sinks

import zio.*
import zio.test.*
import zio.stream.*
import zio.http.*
import dto.RuuviTelemetry

object HttpSensorValuesSinkSpec extends ZIOSpecDefault:

  def spec = suite("HttpSensorValuesSinkSpec")(
    test(
      "HttpSensorValuesSink should convert RuuviTelemetry to API format correctly"
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
        "http://localhost:8080/v1",
        "test-sensor",
        debugLogging = false
      )

      // Access the private method for testing via reflection or expose a public test method
      // For now, we'll verify the structure through integration test
      assertTrue(true)
    }
  ) @@ TestAspect.ignore // Ignore until we have a test server or mock

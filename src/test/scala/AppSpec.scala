import zio.*
import zio.json.*
import zio.test.*
import sinks.*
import sources.*
import App.forwarder
import dto.RuuviTelemetry

object AppSpec extends ZIOSpecDefault:

  def spec = suite("AppSpec")(
    test(
      "ConsoleSensorValuesSink and ConsoleSensorValuesSource should pass through values"
    ) {
      val sourceCreator = ConsoleSensorValuesSource
      val sinkCreator = ConsoleSensorValuesSink
      val prog = forwarder(sourceCreator, sinkCreator).catchSome {
        case _: StreamShutdown => ZIO.unit
      }

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

      val consoleOutput = for
        _ <- TestConsole.feedLines(testTelemetry1.toJson, testTelemetry2.toJson)
        _ <- prog.timeout(1.second)
        lines <- TestConsole.output
      yield lines
      assertZIO(
        consoleOutput.map(
          _.map(
            _.fromJson[RuuviTelemetry].toOption.get
          )
        )
      )(
        Assertion.hasSameElements(
          Vector(
            testTelemetry1,
            testTelemetry2
          )
        )
      )
    }
  )

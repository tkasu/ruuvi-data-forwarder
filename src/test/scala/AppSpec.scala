import zio.*
import zio.test.*
import sinks.*
import sources.*
import App.forwarder

object AppSpec extends ZIOSpecDefault:

  def spec = suite("AppSpec")(
    test(
      "ConsoleSensorValuesSink and ConsoleSensorValuesSource should pass through values"
    ) {
      val sourceCreator = ConsoleSensorValuesSource
      val sinkCreator = ConsoleSensorValuesSink
      val prog = forwarder(sourceCreator, sinkCreator).catchSome {
        // EOF is expected when no more input from TestConsole
        case _: java.io.EOFException => ZIO.unit
      }

      val testLine1 =
        "{\"battery_potential\":2335,\"humidity\":653675,\"mac_address\":[254,38,136,122,102,102],\"measurement_sequence_number\":53300,\"movement_counter\":2,\"pressure\":100755,\"temperature_millicelsius\":-29020,\"tx_power\":4}"
      val testLine2 =
        "{\"battery_potential\":2176,\"humidity\":576425,\"mac_address\":[213,18,52,102,20,20],\"measurement_sequence_number\":1589,\"movement_counter\":79,\"pressure\":100556,\"temperature_millicelsius\":22080,\"tx_power\":4}"
      val consoleOutput = for
        _ <- TestConsole.feedLines(testLine1, testLine2)
        _ <- prog.timeout(1.second)
        lines <- TestConsole.output
      yield lines
      assertZIO(
        consoleOutput.map(_.map(_.trim)) // trim to remove trailing newline
      )(
        Assertion.hasSameElements(
          Vector(
            testLine1,
            testLine2
          )
        )
      )
    }
  )

import zio.*

import sinks.*
import sources.*

object App extends ZIOAppDefault:

  def forwarder(
      sourceCreator: SensorValuesSource,
      sinkCreator: SensorValuesSink
  ) =
    val source = sourceCreator.make
    val sink = sinkCreator.make
    (source >>> sink).forever

  def run = for
    _ <- Console.printLine("Reading StdIn")
    _ <- forwarder(ConsoleSensorValuesSource, ConsoleSensorValuesSink)
    _ <- Console.printLine("Reading done")
  yield ()

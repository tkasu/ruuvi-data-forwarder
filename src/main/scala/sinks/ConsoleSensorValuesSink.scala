package sinks

import zio.*
import zio.stream.*

object ConsoleSensorValuesSink extends SensorValuesSink:
  def make: ZSink[Any, java.io.IOException, String, Nothing, Unit] =
    ZSink.foreach(Console.printLine(_))

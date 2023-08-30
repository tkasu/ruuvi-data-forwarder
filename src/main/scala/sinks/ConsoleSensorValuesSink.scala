package sinks

import dto.RuuviTelemetry
import zio.*
import zio.json.*
import zio.stream.*

object ConsoleSensorValuesSink extends SensorValuesSink:
  def make: ZSink[Any, java.io.IOException, RuuviTelemetry, Nothing, Unit] =
    ZSink.foreach(telemetry => Console.printLine(telemetry.toJson))

package sinks

import dto.RuuviTelemetry
import zio.stream.ZSink

trait SensorValuesSink:
  def make: ZSink[Any, Any, RuuviTelemetry, Nothing, Unit]

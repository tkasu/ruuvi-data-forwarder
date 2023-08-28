package sinks

import zio.stream.ZSink

trait SensorValuesSink:
  def make: ZSink[Any, Any, String, Nothing, Unit]

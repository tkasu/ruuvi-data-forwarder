package sinks

import dto.RuuviTelemetry
import zio.Chunk
import zio.stream.ZSink

trait SensorValuesSink:
  def make: ZSink[Any, Any, Chunk[RuuviTelemetry], Nothing, Unit]
  def desiredBatchSize: Int
  def desiredMaxBatchLatencySeconds: Int

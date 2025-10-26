package sinks

import dto.RuuviTelemetry
import zio.*
import zio.json.*
import zio.stream.*

object ConsoleSensorValuesSink extends SensorValuesSink:
  // Console sink processes one record at a time (batch size of 1)
  val desiredBatchSize: Int = 1
  // Very long wait time for console sink (24 hours in seconds)
  val desiredMaxBatchLatencySeconds: Int = 86400

  def make
      : ZSink[Any, java.io.IOException, Chunk[RuuviTelemetry], Nothing, Unit] =
    ZSink.foreach { chunk =>
      ZIO.foreachDiscard(chunk) { telemetry =>
        Console.printLine(telemetry.toJson)
      }
    }

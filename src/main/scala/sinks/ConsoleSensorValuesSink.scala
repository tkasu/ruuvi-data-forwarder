package sinks

import dto.RuuviTelemetry
import zio.*
import zio.json.*
import zio.stream.*

object ConsoleSensorValuesSink extends SensorValuesSink:
  // Console sink processes one record at a time (batch size of 1)
  val desiredBatchSize: Int = 1
  // Infinite wait time for console sink
  val desiredMaxBatchLatencySeconds: Int = Int.MaxValue

  def make
      : ZSink[Any, java.io.IOException, Chunk[RuuviTelemetry], Nothing, Unit] =
    ZSink.foreach { chunk =>
      ZIO.foreachDiscard(chunk) { telemetry =>
        Console.printLine(telemetry.toJson)
      }
    }

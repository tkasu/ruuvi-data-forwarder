package sinks

import dto.RuuviTelemetry
import zio.*
import zio.json.*
import zio.stream.*
import zio.logging.*
import java.nio.file.{Files, Paths, StandardOpenOption}
import java.nio.charset.StandardCharsets

class JsonLinesSensorValuesSink(
    filePath: String,
    debugLogging: Boolean
) extends SensorValuesSink:

  // JSON Lines sink processes one record at a time (batch size of 1)
  val desiredBatchSize: Int = 1
  // Infinite wait time for JSON Lines sink
  val desiredMaxBatchLatencySeconds: Int = Int.MaxValue

  def make
      : ZSink[Any, java.io.IOException, Chunk[RuuviTelemetry], Nothing, Unit] =
    // Note: Sequential processing is suitable for typical sensor telemetry rates.
    // For high-throughput scenarios, consider batched writes to improve performance.
    ZSink.foreach { chunk =>
      ZIO.foreachDiscard(chunk) { telemetry =>
        for
          json <- ZIO.succeed(telemetry.toJson)
          _ <- ZIO
            .logDebug(s"Writing telemetry to $filePath: $json")
            .when(debugLogging)
          _ <- writeJsonLine(filePath, json)
        yield ()
      }
    }

  private def writeJsonLine(
      path: String,
      json: String
  ): ZIO[Any, java.io.IOException, Unit] =
    ZIO.attemptBlockingIO {
      val filePath = Paths.get(path)
      // Create parent directories if they don't exist (idempotent operation)
      Option(filePath.getParent).foreach(Files.createDirectories(_))
      // Append the JSON line with newline
      val content = s"$json\n"
      Files.write(
        filePath,
        content.getBytes(StandardCharsets.UTF_8),
        StandardOpenOption.CREATE,
        StandardOpenOption.APPEND
      )
      ()
    }

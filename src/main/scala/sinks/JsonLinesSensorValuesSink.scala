package sinks

import dto.RuuviTelemetry
import zio.*
import zio.json.*
import zio.stream.*
import zio.logging.*
import java.nio.file.{Files, Paths, StandardOpenOption}
import java.nio.charset.StandardCharsets

class JsonLinesSensorValuesSink(filePath: String, debugLogging: Boolean)
    extends SensorValuesSink:

  def make: ZSink[Any, java.io.IOException, RuuviTelemetry, Nothing, Unit] =
    ZSink.foreach { telemetry =>
      for
        json <- ZIO.succeed(telemetry.toJson)
        _ <- ZIO.logDebug(s"Writing telemetry to $filePath: $json").when(
          debugLogging
        )
        _ <- writeJsonLine(filePath, json)
      yield ()
    }

  private def writeJsonLine(
      path: String,
      json: String
  ): ZIO[Any, java.io.IOException, Unit] =
    ZIO.attemptBlockingIO {
      val filePath = Paths.get(path)
      // Create parent directories if they don't exist
      Option(filePath.getParent).foreach(p =>
        if !Files.exists(p) then Files.createDirectories(p)
      )
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

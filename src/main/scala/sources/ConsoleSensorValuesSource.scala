package sources

import dto.RuuviTelemetry
import zio.*
import zio.json.*
import zio.stream.*

object ConsoleSensorValuesSource extends SensorValuesSource:
  def make: ZStream[Any, SourceError, RuuviTelemetry] =
    val consoleParser: ZIO[Any, Option[SourceError], RuuviTelemetry] = (for
      inputString <- Console.readLine.mapError {
        case _: java.io.EOFException => None
        case other => Some(RuuviParseError(other.getMessage, other))
      }
      maybeJson = inputString.fromJson[RuuviTelemetry]
      json <- ZIO
        .fromEither(maybeJson)
        .mapError(err => Some(RuuviParseError(err)): Option[SourceError])
    yield json)

    ZStream.repeatZIOOption(consoleParser)

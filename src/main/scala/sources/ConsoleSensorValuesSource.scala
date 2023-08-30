package sources

import dto.RuuviTelemetry
import zio.*
import zio.json.*
import zio.stream.*

object ConsoleSensorValuesSource extends SensorValuesSource:
  def make: ZStream[Any, SourceError, RuuviTelemetry] =
    val consoleParser = for
      inputString <- Console.readLine.catchNonFatalOrDie {
        case _: java.io.EOFException => ZIO.fail(StreamShutdown())
      }
      maybeJson = inputString.fromJson[RuuviTelemetry]
      json <- ZIO.fromEither(maybeJson).mapError(err => RuuviParseError(err))
    yield json

    ZStream.fromZIO(consoleParser)

package config

import zio.Config
import zio.config.*
import zio.config.magnolia.*

enum SinkType:
  case Console, JsonLines, Http

object SinkType:
  given Config[SinkType] =
    Config.string.mapOrFail {
      case "console"   => Right(SinkType.Console)
      case "jsonlines" => Right(SinkType.JsonLines)
      case "http"      => Right(SinkType.Http)
      case other =>
        Left(
          Config.Error.InvalidData(message =
            s"Invalid sink type: $other. Must be 'console', 'jsonlines', or 'http'"
          )
        )
    }

case class JsonLinesConfig(
    path: String,
    @name("debug-logging")
    debugLogging: Boolean
)

object JsonLinesConfig:
  val descriptor: Config[JsonLinesConfig] = deriveConfig[JsonLinesConfig]

case class HttpConfig(
    @name("api-url")
    apiUrl: String,
    @name("sensor-name")
    sensorName: String,
    @name("debug-logging")
    debugLogging: Boolean
)

object HttpConfig:
  val descriptor: Config[HttpConfig] = deriveConfig[HttpConfig]

case class SinkConfig(
    @name("sink-type")
    sinkType: SinkType,
    @name("json-lines")
    jsonLines: Option[JsonLinesConfig],
    http: Option[HttpConfig]
)

object SinkConfig:
  val descriptor: Config[SinkConfig] = deriveConfig[SinkConfig]

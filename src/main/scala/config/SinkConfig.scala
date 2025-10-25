package config

import zio.Config
import zio.config.*
import zio.config.magnolia.*
import zio.config.magnolia.name

enum SinkType:
  case Console, JsonLines

object SinkType:
  given Config[SinkType] =
    Config.string.mapOrFail {
      case "console"   => Right(SinkType.Console)
      case "jsonlines" => Right(SinkType.JsonLines)
      case other =>
        Left(
          Config.Error.InvalidData(message =
            s"Invalid sink type: $other. Must be 'console' or 'jsonlines'"
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

case class SinkConfig(
    @name("sink-type")
    sinkType: SinkType,
    @name("json-lines")
    jsonLines: Option[JsonLinesConfig]
)

object SinkConfig:
  val descriptor: Config[SinkConfig] = deriveConfig[SinkConfig]

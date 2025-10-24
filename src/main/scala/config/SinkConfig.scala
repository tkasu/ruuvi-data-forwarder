package config

import zio.Config
import zio.config.*
import zio.config.magnolia.*

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
    debugLogging: Boolean
)

object JsonLinesConfig:
  implicit lazy val configDescriptor: Config[JsonLinesConfig] =
    deriveConfig[JsonLinesConfig]

case class SinkConfig(
    sinkType: SinkType,
    jsonLines: Option[JsonLinesConfig]
)

object SinkConfig:
  implicit lazy val configDescriptor: Config[SinkConfig] =
    deriveConfig[SinkConfig]

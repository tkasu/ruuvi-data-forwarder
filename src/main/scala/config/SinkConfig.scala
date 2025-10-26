package config

import zio.Config
import zio.config.*
import zio.config.magnolia.*

enum SinkType:
  case Console, JsonLines, DuckDB, Http

object SinkType:
  given Config[SinkType] =
    Config.string.mapOrFail {
      case "console"   => Right(SinkType.Console)
      case "jsonlines" => Right(SinkType.JsonLines)
      case "duckdb"    => Right(SinkType.DuckDB)
      case "http"      => Right(SinkType.Http)
      case other =>
        Left(
          Config.Error.InvalidData(message =
            s"Invalid sink type: $other. Must be 'console', 'jsonlines', 'duckdb', or 'http'"
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

case class DuckDBConfig(
    path: String,
    @name("table-name")
    tableName: String,
    @name("debug-logging")
    debugLogging: Boolean
)

object DuckDBConfig:
  val descriptor: Config[DuckDBConfig] = deriveConfig[DuckDBConfig]

case class HttpConfig(
    @name("api-url")
    apiUrl: String,
    @name("sensor-name")
    sensorName: String,
    @name("debug-logging")
    debugLogging: Boolean,
    @name("timeout-seconds")
    timeoutSeconds: Int = 30,
    @name("max-retries")
    maxRetries: Int = 3
)

object HttpConfig:
  val descriptor: Config[HttpConfig] = deriveConfig[HttpConfig]

case class SinkConfig(
    @name("sink-type")
    sinkType: SinkType,
    @name("json-lines")
    jsonLines: Option[JsonLinesConfig],
    duckdb: Option[DuckDBConfig],
    http: Option[HttpConfig]
)

object SinkConfig:
  val descriptor: Config[SinkConfig] = deriveConfig[SinkConfig]

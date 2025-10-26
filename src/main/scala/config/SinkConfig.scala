package config

import zio.Config
import zio.config.*
import zio.config.magnolia.*

enum SinkType:
  case Console, JsonLines, DuckDB

object SinkType:
  given Config[SinkType] =
    Config.string.mapOrFail {
      case "console"   => Right(SinkType.Console)
      case "jsonlines" => Right(SinkType.JsonLines)
      case "duckdb"    => Right(SinkType.DuckDB)
      case other =>
        Left(
          Config.Error.InvalidData(message =
            s"Invalid sink type: $other. Must be 'console', 'jsonlines', or 'duckdb'"
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
    debugLogging: Boolean,
    @name("desired-batch-size")
    desiredBatchSize: Int,
    @name("desired-max-batch-latency-seconds")
    desiredMaxBatchLatencySeconds: Int
)

object DuckDBConfig:
  val descriptor: Config[DuckDBConfig] = deriveConfig[DuckDBConfig]

case class SinkConfig(
    @name("sink-type")
    sinkType: SinkType,
    @name("json-lines")
    jsonLines: Option[JsonLinesConfig],
    duckdb: Option[DuckDBConfig]
)

object SinkConfig:
  val descriptor: Config[SinkConfig] = deriveConfig[SinkConfig]

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

enum CatalogType:
  case DuckDB, SQLite, Postgres

object CatalogType:
  given Config[CatalogType] =
    Config.string.mapOrFail {
      case "duckdb"   => Right(CatalogType.DuckDB)
      case "sqlite"   => Right(CatalogType.SQLite)
      case "postgres" => Right(CatalogType.Postgres)
      case other =>
        Left(
          Config.Error.InvalidData(message =
            s"Invalid catalog type: $other. Must be 'duckdb', 'sqlite', or 'postgres'"
          )
        )
    }

case class DuckLakeConfig(
    @name("catalog-type")
    catalogType: CatalogType,
    @name("catalog-path")
    catalogPath: String,
    @name("data-path")
    dataPath: String
)

object DuckLakeConfig:
  val descriptor: Config[DuckLakeConfig] = deriveConfig[DuckLakeConfig]

case class DuckDBConfig(
    path: String,
    @name("table-name")
    tableName: String,
    @name("debug-logging")
    debugLogging: Boolean,
    @name("desired-batch-size")
    desiredBatchSize: Int,
    @name("desired-max-batch-latency-seconds")
    desiredMaxBatchLatencySeconds: Int,
    @name("ducklake-enabled")
    ducklakeEnabled: Boolean,
    ducklake: Option[DuckLakeConfig]
)

object DuckDBConfig:
  val descriptor: Config[DuckDBConfig] = deriveConfig[DuckDBConfig]

case class HttpConfig(
    @name("api-url")
    apiUrl: String,
    @name("debug-logging")
    debugLogging: Boolean,
    @name("timeout-seconds")
    timeoutSeconds: Int = 30,
    @name("max-retries")
    maxRetries: Int = 3,
    @name("desired-batch-size")
    desiredBatchSize: Int = 5,
    @name("desired-max-batch-latency-seconds")
    desiredMaxBatchLatencySeconds: Int = 30
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

import zio.*
import zio.logging.*
import zio.logging.backend.SLF4J
import zio.config.typesafe.TypesafeConfigProvider

import sinks.*
import sources.*
import _root_.config.{
  AppConfig,
  SinkConfig,
  SinkType,
  JsonLinesConfig,
  DuckDBConfig,
  DuckLakeConfig,
  CatalogType,
  HttpConfig
}

object App extends ZIOAppDefault:

  // Configure TypesafeConfig provider to load from application.conf
  override val bootstrap: ZLayer[ZIOAppArgs, Any, Any] =
    Runtime.setConfigProvider(TypesafeConfigProvider.fromResourcePath())

  /** Creates a forwarder pipeline that reads from source and writes to sink.
    *
    * Applies batching based on the sink's configuration (desiredBatchSize and
    * desiredMaxBatchLatencySeconds) before piping to the sink.
    *
    * Note: Uses ZIO logging - ensure logging backend is configured in the
    * Runtime (see `run` method for SLF4J backend configuration).
    *
    * @param sourceCreator
    *   Source that provides sensor telemetry stream
    * @param sinkCreator
    *   Sink that consumes sensor telemetry chunks
    * @return
    *   ZIO effect that runs until the source stream completes, catching and
    *   logging parse errors
    */
  def forwarder(
      sourceCreator: SensorValuesSource,
      sinkCreator: SensorValuesSink
  ) =
    val source = sourceCreator.make
    val sink = sinkCreator.make
    source
      .groupedWithin(
        sinkCreator.desiredBatchSize,
        zio.Duration.fromSeconds(
          sinkCreator.desiredMaxBatchLatencySeconds.toLong
        )
      )
      .run(sink)
      .catchSome { case e: RuuviParseError =>
        ZIO.logError(s"Error parsing telemetry: ${e.getMessage}")
      }

  /** Selects and initializes the appropriate sink based on configuration.
    *
    * Note: Uses ZIO logging - ensure logging backend is configured in the
    * Runtime (see `run` method for SLF4J backend configuration).
    *
    * @param sinkConfig
    *   Configuration specifying which sink to use
    * @return
    *   ZIO effect that produces the configured sink instance
    */
  def selectSink(
      sinkConfig: SinkConfig
  ): ZIO[Any, Throwable, SensorValuesSink] =
    sinkConfig.sinkType match
      case SinkType.Console =>
        ZIO.logInfo("Using Console sink (stdout)") *>
          ZIO.succeed(ConsoleSensorValuesSink)

      case SinkType.JsonLines =>
        sinkConfig.jsonLines match
          case Some(jsonLinesConfig) =>
            ZIO.logInfo(s"Using JSON Lines sink: ${jsonLinesConfig.path}") *>
              ZIO.logInfo(
                s"Debug logging enabled: ${jsonLinesConfig.debugLogging}"
              ) *>
              ZIO.succeed(
                JsonLinesSensorValuesSink(
                  jsonLinesConfig.path,
                  jsonLinesConfig.debugLogging
                )
              )
          case None =>
            ZIO.fail(
              RuntimeException(
                "JSON Lines sink selected but configuration is missing"
              )
            )

      case SinkType.DuckDB =>
        sinkConfig.duckdb match
          case Some(duckdbConfig) =>
            val logDuckLake =
              if duckdbConfig.ducklakeEnabled then
                duckdbConfig.ducklake match
                  case Some(dlConfig) =>
                    ZIO.logInfo("DuckLake mode enabled") *>
                      ZIO.logInfo(
                        s"Catalog type: ${dlConfig.catalogType}"
                      ) *>
                      ZIO.logInfo(s"Catalog path: ${dlConfig.catalogPath}") *>
                      ZIO.logInfo(s"Data path: ${dlConfig.dataPath}")
                  case None =>
                    ZIO.logWarning(
                      "DuckLake enabled but configuration is missing, falling back to standard DuckDB mode"
                    )
              else ZIO.logInfo("Using standard DuckDB mode")

            ZIO.logInfo(s"Using DuckDB sink: ${duckdbConfig.path}") *>
              ZIO.logInfo(s"Table name: ${duckdbConfig.tableName}") *>
              ZIO.logInfo(
                s"Debug logging enabled: ${duckdbConfig.debugLogging}"
              ) *>
              ZIO.logInfo(
                s"Batch size: ${duckdbConfig.desiredBatchSize}"
              ) *>
              ZIO.logInfo(
                s"Batch latency: ${duckdbConfig.desiredMaxBatchLatencySeconds}s"
              ) *>
              logDuckLake *>
              ZIO.succeed(
                DuckDBSensorValuesSink(
                  duckdbConfig.path,
                  duckdbConfig.tableName,
                  duckdbConfig.debugLogging,
                  duckdbConfig.desiredBatchSize,
                  duckdbConfig.desiredMaxBatchLatencySeconds,
                  duckdbConfig.ducklakeEnabled,
                  duckdbConfig.ducklake
                )
              )
          case None =>
            ZIO.fail(
              RuntimeException(
                "DuckDB sink selected but configuration is missing"
              )
            )

      case SinkType.Http =>
        sinkConfig.http match
          case Some(httpConfig) =>
            ZIO.logInfo(s"Using HTTP sink: ${httpConfig.apiUrl}") *>
              ZIO.logInfo(
                s"Debug logging enabled: ${httpConfig.debugLogging}"
              ) *>
              ZIO.logInfo(
                s"Timeout: ${httpConfig.timeoutSeconds}s, Max retries: ${httpConfig.maxRetries}"
              ) *>
              ZIO.succeed(
                HttpSensorValuesSink(
                  httpConfig.apiUrl,
                  httpConfig.debugLogging,
                  httpConfig.timeoutSeconds,
                  httpConfig.maxRetries
                )
              )
          case None =>
            ZIO.fail(
              RuntimeException(
                "HTTP sink selected but configuration is missing"
              )
            )

  def run = (for
    appConfig <- ZIO.config(AppConfig.descriptor)
    _ <- ZIO.logInfo("Starting Ruuvi Data Forwarder")
    _ <- ZIO.logInfo("Reading from StdIn")
    sink <- selectSink(appConfig.sink)
    _ <- forwarder(ConsoleSensorValuesSource, sink)
      .catchSome { case _: StreamShutdown =>
        ZIO.logInfo("Stream completed - shutting down")
      }
  yield ()).provide(
    Runtime.removeDefaultLoggers >>> SLF4J.slf4j
  )

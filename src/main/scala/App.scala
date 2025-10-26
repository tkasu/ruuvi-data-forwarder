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
  DuckDBConfig
}

object App extends ZIOAppDefault:

  // Configure TypesafeConfig provider to load from application.conf
  override val bootstrap: ZLayer[ZIOAppArgs, Any, Any] =
    Runtime.setConfigProvider(TypesafeConfigProvider.fromResourcePath())

  /** Creates a forwarder pipeline that reads from source and writes to sink.
    *
    * Note: Uses ZIO logging - ensure logging backend is configured in the
    * Runtime (see `run` method for SLF4J backend configuration).
    *
    * @param sourceCreator
    *   Source that provides sensor telemetry stream
    * @param sinkCreator
    *   Sink that consumes sensor telemetry
    * @return
    *   ZIO effect that runs forever, catching and logging parse errors
    */
  def forwarder(
      sourceCreator: SensorValuesSource,
      sinkCreator: SensorValuesSink
  ) =
    val source = sourceCreator.make
    val sink = sinkCreator.make
    (source >>> sink).catchSome { case e: RuuviParseError =>
      ZIO.logError(s"Error parsing telemetry: ${e.getMessage}")
    }.forever

  /** Creates a batching forwarder pipeline for DuckDB with groupedWithin
    * support.
    *
    * @param sourceCreator
    *   Source that provides sensor telemetry stream
    * @param duckdbSink
    *   DuckDB sink with batching configuration
    * @return
    *   ZIO effect that runs forever, catching and logging parse errors
    */
  def batchingForwarder(
      sourceCreator: SensorValuesSource,
      duckdbSink: DuckDBSensorValuesSink
  ) =
    val source = sourceCreator.make
    source
      .groupedWithin(
        duckdbSink.desiredBatchSize,
        zio.Duration.fromSeconds(duckdbSink.desiredMaxBatchLatencySeconds.toLong)
      )
      .mapZIO { batch =>
        duckdbSink.insertBatchDirect(batch.toList)
      }
      .runDrain
      .catchSome { case e: RuuviParseError =>
        ZIO.logError(s"Error parsing telemetry: ${e.getMessage}")
      }
      .forever

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
              ZIO.succeed(
                DuckDBSensorValuesSink(
                  duckdbConfig.path,
                  duckdbConfig.tableName,
                  duckdbConfig.debugLogging,
                  duckdbConfig.desiredBatchSize,
                  duckdbConfig.desiredMaxBatchLatencySeconds
                )
              )
          case None =>
            ZIO.fail(
              RuntimeException(
                "DuckDB sink selected but configuration is missing"
              )
            )

  def run = (for
    appConfig <- ZIO.config(AppConfig.descriptor)
    _ <- ZIO.logInfo("Starting Ruuvi Data Forwarder")
    _ <- ZIO.logInfo("Reading from StdIn")
    sink <- selectSink(appConfig.sink)
    _ <- appConfig.sink.sinkType match
      case SinkType.DuckDB =>
        // Use batching forwarder for DuckDB
        batchingForwarder(ConsoleSensorValuesSource, sink.asInstanceOf[DuckDBSensorValuesSink])
          .catchSome { case _: StreamShutdown =>
            ZIO.logInfo("Stream completed - shutting down")
          }
      case _ =>
        // Use regular forwarder for other sinks
        forwarder(ConsoleSensorValuesSource, sink)
          .catchSome { case _: StreamShutdown =>
            ZIO.logInfo("Stream completed - shutting down")
          }
  yield ()).provide(
    Runtime.removeDefaultLoggers >>> SLF4J.slf4j
  )

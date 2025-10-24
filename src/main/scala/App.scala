import zio.*
import zio.logging.*
import zio.logging.backend.SLF4J

import sinks.*
import sources.*
import _root_.config.{AppConfig, SinkConfig, SinkType, JsonLinesConfig}

object App extends ZIOAppDefault:

  def forwarder(
      sourceCreator: SensorValuesSource,
      sinkCreator: SensorValuesSink
  ) =
    val source = sourceCreator.make
    val sink = sinkCreator.make
    (source >>> sink).catchSome { case e: RuuviParseError =>
      ZIO.logError(s"Error parsing telemetry: ${e.getMessage}")
    }.forever

  def selectSink(sinkConfig: SinkConfig): ZIO[Any, Throwable, SensorValuesSink] =
    sinkConfig.sinkType match
      case SinkType.Console =>
        ZIO.logInfo("Using Console sink (stdout)") *>
          ZIO.succeed(ConsoleSensorValuesSink)

      case SinkType.JsonLines =>
        sinkConfig.jsonLines match
          case Some(jsonLinesConfig) =>
            ZIO.logInfo(s"Using JSON Lines sink: ${jsonLinesConfig.path}") *>
              ZIO.logInfo(s"Debug logging enabled: ${jsonLinesConfig.debugLogging}") *>
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

  def run = (for
    appConfig <- ZIO.config[AppConfig]
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

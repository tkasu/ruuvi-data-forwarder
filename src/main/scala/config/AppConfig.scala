package config

import zio.Config
import zio.config.*
import zio.config.magnolia.*

case class AppConfig(
    sink: SinkConfig
)

object AppConfig:
  val descriptor: Config[AppConfig] =
    SinkConfig.descriptor.nested("sink").map(AppConfig.apply)

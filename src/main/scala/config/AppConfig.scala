package config

import zio.Config
import zio.config.*
import zio.config.magnolia.*
import zio.config.typesafe.*

case class AppConfig(
    sink: SinkConfig
)

object AppConfig:
  implicit lazy val configDescriptor: Config[AppConfig] = deriveConfig[AppConfig]

  val layer = TypesafeConfigProvider.fromResourcePath().nested("ruuvi-data-forwarder")

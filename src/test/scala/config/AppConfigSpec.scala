package config

import zio.test.*
import zio.config.typesafe.TypesafeConfigProvider
import zio.{Scope, ZIO}

object AppConfigSpec extends ZIOSpecDefault:

  override def spec: Spec[TestEnvironment & Scope, Any] =
    suite("AppConfigSpec")(
      test("load from application.conf") {
        val configProvider = TypesafeConfigProvider.fromResourcePath()
        val config = configProvider.load(AppConfig.descriptor)
        assertZIO(config.map(_.sink.sinkType))(
          Assertion.equalTo(SinkType.Console)
        )
      }
    )

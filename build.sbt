val scala3Version = "3.3.0"

lazy val zioVersion = "2.0.13"

lazy val root = project
  .in(file("."))
  .settings(
    name := "ruuvi-data-forwarder",
    version := "0.1.0-SNAPSHOT",

    scalaVersion := scala3Version,

    libraryDependencies ++= List(
      "dev.zio" %% "zio" % zioVersion,
      "dev.zio" %% "zio-streams" % zioVersion,
      "dev.zio" %% "zio-test" % zioVersion % Test,
      "dev.zio" %% "zio-test-sbt" % zioVersion % Test,
    )
  )

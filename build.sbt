val scala3Version = "3.5.2"

lazy val zioVersion = "2.1.14"

lazy val root = project
  .in(file("."))
  .settings(
    name := "ruuvi-data-forwarder",
    version := "0.1.0-SNAPSHOT",

    scalaVersion := scala3Version,

    libraryDependencies ++= List(
      "dev.zio" %% "zio" % zioVersion,
      "dev.zio" %% "zio-json" % "0.7.3",
      "dev.zio" %% "zio-streams" % zioVersion,
      "dev.zio" %% "zio-test" % zioVersion % Test,
      "dev.zio" %% "zio-test-sbt" % zioVersion % Test,
    )
  )

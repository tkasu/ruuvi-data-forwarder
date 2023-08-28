val scala3Version = "3.3.0"

lazy val root = project
  .in(file("."))
  .settings(
    name := "ruuvi-data-forwarder",
    version := "0.1.0-SNAPSHOT",

    scalaVersion := scala3Version,

    libraryDependencies += "dev.zio" %% "zio" % "2.0.13"
  )

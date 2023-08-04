lazy val play = (project in file("./api"))
  .enablePlugins(PlayScala)
  .settings(
    name := """api""",
    libraryDependencies ++= Seq(
      guice,
      "org.scalatestplus.play" %% "scalatestplus-play" % "5.1.0" % Test,
      "com.softwaremill.sttp.client3" %% "core" % "3.8.15",
      "com.softwaremill.sttp.client3" %% "play-json" % "3.8.15",
      "com.softwaremill.sttp.client3" %% "okhttp-backend" % "3.8.15",
    )
  )
sbtPlugin := true
organization := "com.github.platypii"
name := "sbt-typescript"
version := "4.2.2"

// Scala needs to match sbt
scalaVersion := "2.12.14"

updateOptions := updateOptions.value.withCachedResolution(true)

scalacOptions ++= Seq(
  "-feature",
  "-encoding", "UTF8",
  "-deprecation",
  "-unchecked",
  "-Xlint",
  "-Ywarn-dead-code",
  "-Ywarn-adapted-args"
)

libraryDependencies ++= Seq(
  "io.spray" %% "spray-json" % "1.3.6",
  "com.typesafe" %% "jse" % "1.2.4", // TODO: Remove me

  // js dependencies
  "org.webjars.npm" % "typescript" % "4.2.2",
  // Used by ...?
  "org.webjars.npm" % "fs-extra" % "9.0.1",
  "org.webjars.npm" % "es6-shim" % "0.35.6",
)

resolvers ++= Seq(
  Resolver.bintrayRepo("webjars", "maven"),
  Resolver.typesafeRepo("releases"),
  Resolver.sbtPluginRepo("releases"),
  Resolver.sonatypeRepo("releases"),
  Resolver.mavenLocal
)

addSbtPlugin("com.typesafe.sbt" % "sbt-web" % "1.4.4")

enablePlugins(SbtPlugin)
scriptedLaunchOpts := Seq(s"-Dproject.version=${version.value}")
scriptedBufferLog := false

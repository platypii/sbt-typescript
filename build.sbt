sbtPlugin := true
organization := "com.github.platypii"
name := "sbt-typescript"
version := "4.6.4"

// Scala needs to match sbt
scalaVersion := "2.12.18"

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
  // js dependencies
  "org.webjars.npm" % "typescript" % "4.6.4",
  // Used by ...?
  "org.webjars.npm" % "fs-extra" % "10.1.0",
  "org.webjars.npm" % "es6-shim" % "0.35.6",
)

resolvers += Resolver.mavenLocal

addSbtPlugin("com.github.sbt" % "sbt-js-engine" % "1.3.5")

enablePlugins(SbtPlugin)
scriptedLaunchOpts := Seq(s"-Dproject.version=${version.value}")
scriptedBufferLog := false

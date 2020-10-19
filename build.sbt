sbtPlugin := true
organization := "com.github.platypii"
name := "sbt-typescript"
version := "4.0.3"

// Scala needs to match sbt
scalaVersion := (CrossVersion partialVersion sbtCrossVersion.value match {
  case Some((0, 13)) => "2.10.6"
  case Some((1, _))  => "2.12.8"
  case _             => sys error s"Unhandled sbt version ${sbtCrossVersion.value}"
})

crossSbtVersions := Seq("1.3.10")

val sbtCrossVersion = sbtVersion in pluginCrossBuild

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
  "org.webjars.npm" % "typescript" % "4.0.3",
  "org.webjars.npm" % "fs-extra" % "9.0.1",
  "org.webjars.npm" % "es6-shim" % "0.35.5"
)

dependencyOverrides ++= Seq(
  "org.webjars" % "webjars-locator" % "0.36",
  "org.webjars" % "webjars-locator-core" % "0.38",

  "org.webjars" % "npm" % "4.4.4"
)

resolvers ++= Seq(
  Resolver.bintrayRepo("webjars","maven"),
  Resolver.typesafeRepo("releases"),
  Resolver.sbtPluginRepo("releases"),
  Resolver.sonatypeRepo("releases"),
  Resolver.mavenLocal
)

addSbtPlugin("com.typesafe.sbt" % "sbt-js-engine" % "1.2.3")
addSbtPlugin("com.typesafe.sbt" % "sbt-web" % "1.4.4")

enablePlugins(SbtPlugin)
scriptedLaunchOpts := Seq(s"-Dproject.version=${version.value}")
scriptedBufferLog := false

sbtPlugin := true
organization := "com.github.platypii"
name := "sbt-typescript"
version := "4.0.5"

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
  "org.webjars.npm" % "typescript" % "4.0.5",
  // Used by ...?
  "org.webjars.npm" % "fs-extra" % "9.0.1",
  "org.webjars.npm" % "es6-shim" % "0.35.5",

  // NPM and overrides for wildcard dependencies
  "org.webjars.npm" % "npm" % "6.14.8" exclude("org.webjars.npm", "spdx-exceptions"),
  "org.webjars.npm" % "aproba" % "2.0.0",
  "org.webjars.npm" % "debuglog" % "1.0.1",
  "org.webjars.npm" % "gauge" % "2.7.3",
  "org.webjars.npm" % "iferr" % "1.0.2",
  "org.webjars.npm" % "imurmurhash" % "0.1.4",
  "org.webjars.npm" % "meant" % "1.0.2",
  "org.webjars.npm" % "npm-profile" % "4.0.4",
  "org.webjars.npm" % "lodash._baseindexof" % "3.1.0",
  "org.webjars.npm" % "lodash._bindcallback" % "3.0.1",
  "org.webjars.npm" % "lodash._cacheindexof" % "3.0.2",
  "org.webjars.npm" % "lodash._createcache" % "3.1.2",
  "org.webjars.npm" % "lodash.restparam" % "3.6.1",
  "org.webjars.npm" % "lodash._getnative" % "3.9.1",
)

resolvers ++= Seq(
  Resolver.bintrayRepo("webjars", "maven"),
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

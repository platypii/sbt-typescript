sbtPlugin := true
organization := "com.github.platypii"
name := "sbt-typescript"
version := "4.1.3-SNAPSHOT"

scalaVersion := "2.12.12"

scalacOptions ++= Seq(
  "-feature",
  "-encoding", "UTF8",
  "-deprecation",
  "-unchecked",
  "-Xlint",
  "-Ywarn-dead-code",
  "-Ywarn-adapted-args"
)

// Enable SbtWeb to bundle assets
lazy val root = (project in file(".")).enablePlugins(SbtWeb)
enablePlugins(SbtPlugin)

libraryDependencies ++= Seq(
  "io.spray" %% "spray-json" % "1.3.6",
  "com.typesafe" %% "jse" % "1.2.4", // TODO: Remove me

  // js dependencies
  "org.webjars.npm" % "typescript" % "4.1.2",
  "org.webjars.npm" % "fs-extra" % "9.0.1",
  // Used by ...?
  "org.webjars.npm" % "types__fs-extra" % "9.0.2",
  "org.webjars.npm" % "types__node" % "14.14.6"
)

resolvers ++= Seq(
  Resolver.bintrayRepo("webjars", "maven"),
  Resolver.typesafeRepo("releases"),
  Resolver.sbtPluginRepo("releases"),
  Resolver.sonatypeRepo("releases"),
  Resolver.mavenLocal
)

addSbtPlugin("com.typesafe.sbt" % "sbt-web" % "1.4.4")

// Needed to get js into root of jar
publishMavenStyle := true
resourceDirectory in Compile := baseDirectory.value / "target" / "sbt-typescript"
publish := publish.dependsOn(typescript in Assets).map((u) => u).value
publishLocal := publishLocal.dependsOn(typescript in Assets).map((u) => u).value

// For Arp
import com.arpnetworking.sbt.typescript.Import.TypescriptKeys._
configFile := "tsconfig.json"

typescript / logLevel := Level.Debug

scriptedLaunchOpts := Seq(s"-Dproject.version=${version.value}")
scriptedBufferLog := false

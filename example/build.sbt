name := "sbt-typescript-example"

version := "1.0-SNAPSHOT"

scalaVersion := "2.13.1"

// More compiler warnings
scalacOptions ++= Seq("-deprecation", "-unchecked", "-feature", "-Ywarn-dead-code")

lazy val root = (project in file(".")).enablePlugins(PlayScala)

libraryDependencies ++= Seq(
  guice,
  "com.google.inject" % "guice" % "4.2.2",
  // WebJars
  "org.webjars" % "requirejs" % "2.3.6",
  "org.webjars.npm" % "types__jquery" % "3.3.33",
  "org.webjars.npm" % "types__sizzle" % "2.3.2" // needed for types__jquery
)

// typescript / typingsFile := Some(file("app/assets/ts/global.d.ts"))

MochaKeys.requires += "Setup"

// Make asset tests work in intellij:
unmanagedResourceDirectories in Test += baseDirectory.value / "target/web/public/test"


lazy val root = (project in file(".")).enablePlugins(SbtWeb)

libraryDependencies ++= Seq(
  "org.webjars.npm" % "types__moment" % "2.11.26-alpha",
  "org.webjars.npm" % "moment" % "2.11.2"
)
resolveFromWebjarsNodeModulesDir := true

typescript / logLevel := Level.Debug

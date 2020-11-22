
lazy val root = (project in file(".")).enablePlugins(SbtWeb)

logLevel in typescript := Level.Debug

assertCompilation in typescript := true

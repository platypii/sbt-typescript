
lazy val root = (project in file(".")).enablePlugins(SbtWeb)

typescript / logLevel := Level.Debug

typescript / assertCompilation := true

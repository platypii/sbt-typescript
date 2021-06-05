
lazy val root = (project in file(".")).enablePlugins(SbtWeb)

libraryDependencies ++= Seq(
  "org.webjars.npm" % "angular__core" % "2.4.10",
  "org.webjars.npm" % "angular__compiler" % "2.4.10",
  "org.webjars.npm" % "angular__platform-browser-dynamic" % "2.4.10",
  "org.webjars.npm" % "systemjs" % "0.19.40",
  "org.webjars.npm" % "rxjs" % "5.4.2",
  "org.webjars.npm" % "es6-promise" % "3.0.2",
  "org.webjars.npm" % "es6-shim" % "0.34.1",
  "org.webjars.npm" % "reflect-metadata" % "0.1.2",
  "org.webjars.npm" % "zone.js" % "0.7.4"
)

// typingsFile := Some(baseDirectory.value / "typings" / "browser.d.ts")
resolveFromWebjarsNodeModulesDir := true
typescript / logLevel := Level.Debug

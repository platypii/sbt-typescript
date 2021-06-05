
lazy val root = (project in file(".")).enablePlugins(SbtWeb)

typescript / logLevel := Level.Debug

typescript / assertCompilation := true

// compile our tests as commonjs instead of systemjs modules
typescript / projectTestFile := Some("tsconfig.test.json")

// jasmine / jasmineFilter := GlobFilter("*Test.js") | GlobFilter("*Spec.js") | GlobFilter("*.spec.js")
// jasmine / logLevel := Level.Info

libraryDependencies ++= Seq(
  "org.webjars.npm" % "types__jasmine" % "2.8.16"
)

resolveFromWebjarsNodeModulesDir := true

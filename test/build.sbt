
lazy val root = (project in file(".")).enablePlugins(SbtWeb)

logLevel in typescript := Level.Debug

assertCompilation in typescript := true

// compile our tests as commonjs instead of systemjs modules
(projectTestFile in typescript) := Some("tsconfig.test.json")

//jasmineFilter in jasmine := GlobFilter("*Test.js") | GlobFilter("*Spec.js") | GlobFilter("*.spec.js")
//logLevel in jasmine := Level.Info

libraryDependencies ++= Seq(
  "org.webjars.npm" % "types__jasmine" % "2.8.16"
)

resolveFromWebjarsNodeModulesDir := true

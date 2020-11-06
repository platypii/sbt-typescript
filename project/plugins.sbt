libraryDependencies += "org.scala-sbt" %% "scripted-plugin" % sbtVersion.value

addSbtPlugin("org.scalameta" % "sbt-scalafmt" % "2.4.2")

// Use self to generate js from ts
// addSbtPlugin("com.github.platypii" % "sbt-typescript" % "4.1.2")
addSbtPlugin("com.arpnetworking" % "sbt-typescript" % "0.4.4")
resolvers += Resolver.typesafeRepo("releases")

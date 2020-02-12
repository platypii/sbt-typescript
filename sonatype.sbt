// https://github.com/xerial/sbt-sonatype

sonatypeProfileName := "com.github.platypii"

// Open-source license of your choice
licenses := Seq("Apache-2.0" -> url("http://www.apache.org/licenses/LICENSE-2.0"))
homepage := Some(url("https://github.com/platypii/sbt-typescript"))
scmInfo := Some(ScmInfo(url("https://github.com/platypii/sbt-typescript"),
                            "git@github.com:platypii/sbt-typescript.git"))
developers := List(
  Developer(id="platypii", name="platypii", email="platypii@gmail.com", url=url("https://github.com/platypii/sbt-typescript"))
)

// Required to sync with Maven central
publishMavenStyle := true

publishTo := sonatypePublishToBundle.value

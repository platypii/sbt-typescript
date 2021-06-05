package com.platypii.typescript

import com.platypii.typescript.JsTaskImport.JsTaskKeys.{jsOptions, parallelism, shellFile, taskMessage}
import com.typesafe.sbt.web.Import.WebKeys._
import com.typesafe.sbt.web.PathMapping
import com.typesafe.sbt.web.SbtWeb.autoImport._
import com.typesafe.sbt.web.pipeline.Pipeline
import sbt.Keys._
import sbt.{Def, _}
import spray.json.{JsArray, JsBoolean, JsNumber, JsObject, JsString, JsValue, JsonParser}

/**
  * Typescript compilation can run during 'sbt assets' compilation or during Play 'sbt stage' as a sbt-web pipe
  */
sealed class CompileMode(val value: String) {
  override def toString: String = value
}

object CompileMode {

  case object Compile extends CompileMode("compile")

  case object Stage extends CompileMode("stage")

  val values: Set[CompileMode] = Set(Compile, Stage)
  val parse: Map[String, CompileMode] = values.map(v => v.value -> v).toMap
}

object SbtTypescript extends AutoPlugin with JsonProtocol {

  override def requires: SbtJsTask.type = SbtJsTask

  override def trigger = AllRequirements

  /**
    * The public api to a projects build.sbt
    */
  object autoImport {
    val typescript = TaskKey[Seq[File]]("typescript", "Run Typescript compiler")

    val projectFile =
      SettingKey[File]("typescript-projectfile", "The location of the tsconfig.json  Default: <basedir>/tsconfig.json")

    val projectTestFile = SettingKey[Option[String]](
      "typescript-test-projectfile",
      "The location of the tsconfig.json for test code.  For instance: <basedir>/tsconfig.test.json"
    )

    val typingsFile = SettingKey[Option[File]](
      "typescript-typings-file",
      "A file that refers to typings that the build needs. Default None."
    )

    val tsCodesToIgnore = SettingKey[List[Int]](
      "typescript-codes-to-ignore",
      "The tsc error codes (f.i. TS2307) to ignore. Default empty list."
    )

    val canNotFindModule = 2307 // see f.i. https://github.com/Microsoft/TypeScript/issues/3808

    val resolveFromWebjarsNodeModulesDir =
      SettingKey[Boolean]("typescript-resolve-modules-from-etc", "Will use the directory to resolve modules ")

    val typescriptPipe = Def.taskKey[Pipeline.Stage]("typescript-pipe")
    val outFile = SettingKey[String](
      "typescript-out-file",
      "the name of the outfile that the stage pipe will produce. Default 'main.js' "
    )

    val compileMode = SettingKey[CompileMode](
      "typescript-compile-mode",
      "the compile mode to use if no jvm argument is provided. Default 'Compile'"
    )

    val setupTscCompilation = TaskKey[Unit](
      "setup-tsc-compilation",
      "Setup tsc compilation. For example to get your IDE to compile typescript."
    )

    val assertCompilation =
      SettingKey[Boolean]("typescript-asserts", "for debugging purposes: asserts that tsc produces the expected files")

  }

  val getTsConfig = TaskKey[JsObject]("get-tsconfig", "parses the tsconfig.json file")

  val getCompileMode = TaskKey[CompileMode]("get-compile-mode", "determines required compile mode")

  import autoImport._

  override def buildSettings: Seq[Setting[_]] =
    inTask(typescript)(
      SbtJsTask.jsTaskSpecificUnscopedBuildSettings ++ Seq(
        moduleName := "typescript",
        shellFile := getClass.getClassLoader.getResource("typescript.js")
      )
    )

  override def projectSettings: Seq[Setting[_]] =
    Seq(
      // default settings
      tsCodesToIgnore := List.empty[Int],
      projectFile := baseDirectory.value / "tsconfig.json",
      projectTestFile := None, // baseDirectory.value / "tsconfig.test.json",
      typingsFile := None,
      resolveFromWebjarsNodeModulesDir := false,
      typescript / logLevel := Level.Info,
      typescriptPipe := typescriptPipeTask.value,
      parallelism := 1,
      compileMode := CompileMode.Compile,
      getCompileMode := getCompileModeTask.value,
      outFile := "main.js",
      setupTscCompilation := setupTsCompilationTask().value,
      assertCompilation := false
    ) ++ inTask(typescript)(
      SbtJsTask.jsTaskSpecificUnscopedProjectSettings ++
        inConfig(Assets)(typescriptUnscopedSettings(Assets)) ++
        inConfig(TestAssets)(typescriptUnscopedSettings(TestAssets)) ++
        Seq(
          Assets / taskMessage := "Typescript compiling",
          TestAssets / taskMessage := "Typescript test compiling"
        )
    ) ++ SbtJsTask.addJsSourceFileTasks(typescript) ++ Seq(
      Assets / typescript := (Assets / typescript).dependsOn(Assets / webJarsNodeModules).value,
      TestAssets / typescript := (TestAssets / typescript).dependsOn(TestAssets / webJarsNodeModules).value
    )

  def typescriptUnscopedSettings(config: Configuration): Seq[Setting[_]] = {

    def optionalFields(m: Map[String, Option[JsValue]]): Map[String, JsValue] = {
      m.flatMap {
        case (s, oj) =>
          oj match {
            case None => Map.empty[String, JsValue]
            case Some(jsValue) => Map(s -> jsValue)
          }
      }
    }

    def toJsArray(mainDir: String, testDir: String) = {
      if (config == Assets) {
        JsArray(JsString(mainDir))
      } else if (config == TestAssets) {
        JsArray(JsString(mainDir), JsString(testDir))
      } else {
        throw new IllegalStateException
      }
    }

    Seq(
      includeFilter := GlobFilter("*.ts") | GlobFilter("*.tsx"),
      excludeFilter := GlobFilter("*.d.ts"),
      // the options that we provide to our js task
      jsOptions := JsObject(
        Map(
          "logLevel" -> JsString(logLevel.value.toString),
          "tsconfig" -> parseTsConfig().value,
          "tsconfigDir" -> JsString(projectFile.value.getParent),
          "assetsDirs" -> toJsArray(
            mainDir = (Assets / sourceDirectory).value.getAbsolutePath,
            testDir = (TestAssets / sourceDirectory).value.getAbsolutePath
          ),
          "tsCodesToIgnore" -> JsArray(tsCodesToIgnore.value.toVector.map(JsNumber(_))),
          "nodeModulesDirs" -> toJsArray(
            mainDir = (Assets / webJarsNodeModulesDirectory).value.getAbsolutePath,
            testDir = (TestAssets / webJarsNodeModulesDirectory).value.getAbsolutePath
          ),
          "resolveFromNodeModulesDir" -> JsBoolean(resolveFromWebjarsNodeModulesDir.value),
          "runMode" -> JsString(getCompileMode.value.toString),
          "assertCompilation" -> JsBoolean(assertCompilation.value)
        ) ++ optionalFields(
          Map(
            "extraFiles" -> typingsFile.value.map(tf => JsArray(JsString(tf.getCanonicalPath)))
          )
        )
      ).toString
    )
  }

  /** a convenience task to copy webjar npms to the standard ./node_modules directory */
  def setupTsCompilationTask(): Def.Initialize[Task[Unit]] =
    Def.task {
      def copyPairs(baseDir: File, modules: Seq[File]): Seq[(File, File)] = {
        modules
          .flatMap(f => IO.relativizeFile(baseDir, f).map(rf => Seq((f, rf))).getOrElse(Seq.empty))
          .map { case (f, rf) => (f, baseDirectory.value / "node_modules" / rf.getPath) }
      }

      val assetCopyPairs = copyPairs(
        (Assets / webJarsNodeModulesDirectory).value,
        (Assets / webJarsNodeModules).value
      )

      val testAssetCopyPairs = copyPairs(
        (TestAssets / webJarsNodeModulesDirectory).value,
        (TestAssets / webJarsNodeModules).value
      )

      IO.copy(assetCopyPairs ++ testAssetCopyPairs)
      streams.value.log.info(s"Webjars copied to ./node_modules")
      ()
    }

  /**
    * Parse tsconfig.json and replace the properties that we manage. Ie outDir viz outFile
    */
  def parseTsConfig(): Def.Initialize[Task[JsObject]] =
    Def.task {

      /**
        * Remove comments and then parse tsconfig.json
        */
      def parseJson(tsConfigFile: File): JsValue = {
        val content = IO.read(tsConfigFile)
        val noComment = JsonCleaner.minify(content)
        JsonParser(noComment)
      }

      def fixJson(tsConfigFile: File): JsObject = {
        val tsConfigObject = parseJson(tsConfigFile).asJsObject
        val newTsConfigObject = for {
          coJsValue <- tsConfigObject.fields.get("compilerOptions") if getCompileMode.value == CompileMode.Stage

          co = coJsValue.asJsObject
          newCo = JsObject(co.fields - "outDir" ++ Map("outFile" -> JsString(outFile.value)))
        } yield JsObject(tsConfigObject.fields ++ Map("compilerOptions" -> newCo))
        newTsConfigObject.getOrElse(tsConfigObject)
      }

      val defaultTsConfig = fixJson(projectFile.value)
      val testTsConfigOverrides = projectTestFile.value
        .map(fileName => baseDirectory.value / fileName)
        .map(file => parseJson(file).asJsObject)
      testTsConfigOverrides
        .map(overrides => JsonUtil.merge(defaultTsConfig, overrides))
        .getOrElse(defaultTsConfig)
    }

  def getCompileModeTask: Def.Initialize[Task[CompileMode]] =
    Def.task {
      val modeOpt = for {
        s <- sys.props.get("tsCompileMode")
        cm <- CompileMode.parse.get(s)
      } yield cm

      modeOpt.getOrElse(compileMode.value)
    }

  def typescriptPipeTask: Def.Initialize[Task[Pipeline.Stage]] =
    Def.task {
      val s = streams.value
      val filter = (Assets / typescript / includeFilter).value
      inputMappings =>
        val isTypescript: PathMapping => Boolean = {
          case (file, _) => filter.accept(file)
        }
        val minustypescriptMappings = inputMappings.filterNot(isTypescript)
        s.log.debug(s"running typescript pipe")

        minustypescriptMappings
    }

}

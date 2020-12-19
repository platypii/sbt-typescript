package com.platypii.typescript

import akka.actor.ActorRef
import akka.pattern.ask
import akka.util.Timeout
import com.typesafe.jse.Engine.JsExecutionResult
import com.typesafe.jse.{Engine, LocalEngine, Node}
import com.typesafe.sbt.web.SbtWeb.withActorRefFactory
import com.typesafe.sbt.web.{CompileProblems, incremental, _}
import com.typesafe.sbt.web.incremental.{OpInputHash, OpInputHasher, OpResult}
import sbt.Keys._
import sbt.{Configuration, Def, File, _}
import scala.collection.immutable
import scala.concurrent.duration.{DurationInt, FiniteDuration}
import scala.concurrent.{Await, ExecutionContext, Future}
import spray.json.{JsArray, JsString, JsValue, JsonParser}
import xsbti.Problem

object JsTaskImport {
  object JsTaskKeys {
    val fileInputHasher = TaskKey[OpInputHasher[File]]("jstask-file-input-hasher", "A function that hashes a given file.")
    val jsOptions = TaskKey[String]("jstask-js-options", "The JSON options to be passed to the task.")
    val taskMessage = SettingKey[String]("jstask-message", "The message to output for a task")
    val shellFile = SettingKey[URL]("jstask-shell-url", "The url of the file to perform a given task.")
    val shellSource = TaskKey[File]("jstask-shell-source", "The target location of the js shell script to use.")
    val timeoutPerSource =
      SettingKey[FiniteDuration]("jstask-timeout-per-source", "The maximum amount of time to wait per source file processed by the JS task.")
    val sourceDependencies = SettingKey[Seq[TaskKey[Seq[File]]]]("jstask-source-dependencies", "Source dependencies between source file tasks.")
    val command = SettingKey[Option[File]]("jse-command", "An optional path to the command used to invoke the engine.")
    val parallelism = SettingKey[Int](
      "jse-parallelism",
      "The number of parallel tasks for the JavaScript engine. Defaults to the # of available processors + 1 to keep things busy."
    )
  }
}

/**
  * The commonality of JS task execution oriented plugins is captured by this class.
  */
object SbtJsTask extends AutoPlugin {

  override def requires: SbtWeb.type = SbtWeb

  override def trigger: PluginTrigger = AllRequirements

  val autoImport: JsTaskImport.type = JsTaskImport

  import SbtWeb.autoImport._
  import WebKeys._
  import autoImport._
  import JsTaskKeys._

  val jsTaskSpecificUnscopedConfigSettings = Seq(
    fileInputHasher := {
      val options = jsOptions.value
      OpInputHasher[File](f => OpInputHash.hashString(f.getAbsolutePath + "|" + options))
    },
    resourceManaged := target.value / moduleName.value
  )

  val jsTaskSpecificUnscopedProjectSettings: Seq[Def.Setting[_]] =
    inConfig(Assets)(jsTaskSpecificUnscopedConfigSettings) ++
      inConfig(TestAssets)(jsTaskSpecificUnscopedConfigSettings)

  val jsTaskSpecificUnscopedBuildSettings: Seq[Setting[_]] =
    Seq(
      shellSource := {
        SbtWeb.copyResourceTo(
          (target in Plugin).value / moduleName.value,
          shellFile.value,
          streams.value.cacheDirectory / "copy-resource"
        )
      }
    )

  override def projectSettings =
    Seq(
      jsOptions := "{}",
      timeoutPerSource := 2.hours,
      command := sys.props.get("sbt.jse.command").map(file),
      parallelism := java.lang.Runtime.getRuntime.availableProcessors() + 1
    )

  /**
    * Thrown when there is an unexpected problem to do with the task's execution.
    */
  class JsTaskFailure(m: String) extends RuntimeException(m)

  // node.js docs say *NOTHING* about what encoding is used when you write a string to stdout.
  // It seems that they have it hard coded to use UTF-8, some small tests I did indicate that changing the platform
  // encoding makes no difference on what encoding node uses when it writes strings to stdout.
  private val NodeEncoding = "UTF-8"
  // Used to signal when the script is sending back structured JSON data
  private val JsonEscapeChar: Char = 0x10

  private type FileOpResultMappings = Map[File, OpResult]

  private def FileOpResultMappings(s: (File, OpResult)*): FileOpResultMappings = Map(s: _*)

  private def executeJsOnEngine(engine: ActorRef, shellSource: File, args: Seq[String], stderrSink: String => Unit, stdoutSink: String => Unit)(implicit
    timeout: Timeout,
    ec: ExecutionContext
  ): Future[Seq[JsValue]] = {

    (engine ? Engine.ExecuteJs(
      shellSource,
      args.to[immutable.Seq],
      timeout.duration
    )).mapTo[JsExecutionResult].map { result =>
      // Stuff below probably not needed once jsengine is refactored to stream this

      // Dump stderr as is
      if (result.error.nonEmpty) {
        stderrSink(new String(result.error.toArray, NodeEncoding))
      }

      // Split stdout into lines
      val outputLines = new String(result.output.toArray, NodeEncoding).split("\r?\n")

      // Iterate through lines, extracting out JSON messages, and printing the rest out
      val results = outputLines.foldLeft(Seq.empty[JsValue]) { (results, line) =>
        if (line.indexOf(JsonEscapeChar) == -1) {
          stdoutSink(line)
          results
        } else {
          val (out, json) = line.span(_ != JsonEscapeChar)
          if (!out.isEmpty) {
            stdoutSink(out)
          }
          results :+ JsonParser(json.drop(1))
        }
      }

      if (result.exitValue != 0) {
        throw new JsTaskFailure(new String(result.error.toArray, NodeEncoding))
      }
      results
    }

  }

  private def executeSourceFilesJs(
    engine: ActorRef,
    shellSource: File,
    sourceFileMappings: Seq[PathMapping],
    target: File,
    options: String,
    stderrSink: String => Unit,
    stdoutSink: String => Unit
  )(implicit timeout: Timeout): Future[(FileOpResultMappings, Seq[Problem])] = {

    import ExecutionContext.Implicits.global

    val args = immutable.Seq(
      JsArray(sourceFileMappings.map(x => JsArray(JsString(x._1.getCanonicalPath), JsString(x._2))).toVector).toString(),
      target.getAbsolutePath,
      options
    )

    executeJsOnEngine(engine, shellSource, args, stderrSink, stdoutSink).map { results =>
      import JsTaskProtocol._
      val prp = results.foldLeft(ProblemResultsPair(Nil, Nil)) { (cumulative, result) =>
        val prp = result.convertTo[ProblemResultsPair]
        ProblemResultsPair(
          cumulative.results ++ prp.results,
          cumulative.problems ++ prp.problems
        )
      }
      (prp.results.map(sr => sr.source -> sr.result).toMap, prp.problems)
    }
  }

  /**
    * Primary means of executing a JavaScript shell script for processing source files. unmanagedResources is assumed
    * to contain the source files to filter on.
    * @param task The task to resolve js task settings from - relates to the concrete plugin sub class
    * @param config The sbt configuration to use e.g. Assets or TestAssets
    * @return A task object
    */
  def jsSourceFileTask(
    task: TaskKey[Seq[File]],
    config: Configuration
  ): Def.Initialize[Task[Seq[File]]] =
    Def.task {

      val nodeModulePaths = (nodeModuleDirectories in Plugin).value.map(_.getCanonicalPath)
      val engineProps = Node.props((command in task).value, stdEnvironment = LocalEngine.nodePathEnv(nodeModulePaths.to[immutable.Seq]))

      val sources =
        ((Keys.sources in task in config).value ** ((includeFilter in task in config).value -- (excludeFilter in task in config).value)).get.map(f =>
          new File(f.getCanonicalPath)
        )

      val logger: Logger = streams.value.log
      val taskMsg = (taskMessage in task in config).value
      val taskParallelism = (parallelism in task).value
      val currentState = state.value
      val taskTimeout = (timeoutPerSource in task in config).value
      val taskShellSource = (shellSource in task in config).value
      val taskSourceDirectories = (sourceDirectories in task in config).value
      val taskResources = (resourceManaged in task in config).value
      val options = (jsOptions in task in config).value

      implicit val opInputHasher: OpInputHasher[sbt.File] = (fileInputHasher in task in config).value
      val results: (Set[File], Seq[Problem]) = incremental.syncIncremental((streams in config).value.cacheDirectory / "run", sources) {
        modifiedSources: Seq[File] =>

          if (modifiedSources.nonEmpty) {
            logger.info(s"$taskMsg on ${modifiedSources.size} source(s)")

            val resultBatches: Seq[Future[(FileOpResultMappings, Seq[Problem])]] = {
              val sourceBatches = (modifiedSources grouped Math.max(modifiedSources.size / taskParallelism, 1)).toSeq
              sourceBatches.map { sourceBatch =>
                withActorRefFactory(currentState, this.getClass.getName) { arf =>
                  val engine = arf.actorOf(engineProps)
                  implicit val timeout: Timeout = Timeout(taskTimeout * modifiedSources.size)
                  executeSourceFilesJs(
                    engine,
                    taskShellSource,
                    sourceBatch.pair(Path.relativeTo(taskSourceDirectories)),
                    taskResources,
                    options,
                    m => logger.error(m),
                    m => logger.info(m)
                  )
                }
              }
            }

            import scala.concurrent.ExecutionContext.Implicits.global
            val pendingResults = Future.sequence(resultBatches)
            val completedResults = Await.result(pendingResults, taskTimeout * modifiedSources.size)

            completedResults.foldLeft((FileOpResultMappings(), Seq[Problem]())) { (allCompletedResults, completedResult) =>

              val (prevOpResults, prevProblems) = allCompletedResults

              val (nextOpResults, nextProblems) = completedResult

              (prevOpResults ++ nextOpResults, prevProblems ++ nextProblems)
            }

          } else {
            (FileOpResultMappings(), Nil)
          }
      }

      val (filesWritten, problems) = results

      CompileProblems.report((reporter in task).value, problems)

      filesWritten.toSeq
    }

  private def addUnscopedJsSourceFileTasks(sourceFileTask: TaskKey[Seq[File]]): Seq[Setting[_]] = {
    Seq(
      resourceGenerators += sourceFileTask.taskValue,
      managedResourceDirectories += (resourceManaged in sourceFileTask).value
    ) ++ inTask(sourceFileTask)(
      Seq(
        managedSourceDirectories ++= Def.settingDyn { sourceDependencies.value.map(resourceManaged in _).join }.value,
        managedSources ++= Def.taskDyn { sourceDependencies.value.join.map(_.flatten) }.value,
        sourceDirectories := unmanagedSourceDirectories.value ++ managedSourceDirectories.value,
        sources := unmanagedSources.value ++ managedSources.value
      )
    )
  }

  /**
    * Convenience method to add a source file task into the Asset and TestAsset configurations, along with adding the
    * source file tasks in to their respective collection.
    * @param sourceFileTask The task key to declare.
    * @return The settings produced.
    */
  def addJsSourceFileTasks(sourceFileTask: TaskKey[Seq[File]]): Seq[Setting[_]] = {
    Seq(
      sourceDependencies in sourceFileTask := Nil,
      sourceFileTask in Assets := jsSourceFileTask(sourceFileTask, Assets).dependsOn(nodeModules in Plugin).value,
      sourceFileTask in TestAssets := jsSourceFileTask(sourceFileTask, TestAssets).dependsOn(nodeModules in Plugin).value,
      resourceManaged in sourceFileTask in Assets := webTarget.value / sourceFileTask.key.label / "main",
      resourceManaged in sourceFileTask in TestAssets := webTarget.value / sourceFileTask.key.label / "test",
      sourceFileTask := (sourceFileTask in Assets).value
    ) ++
      inConfig(Assets)(addUnscopedJsSourceFileTasks(sourceFileTask)) ++
      inConfig(TestAssets)(addUnscopedJsSourceFileTasks(sourceFileTask))
  }

  /**
    * Execute some arbitrary JavaScript.
    *
    * This method is intended to assist in building SBT tasks that execute generic JavaScript.  For example:
    *
    * {{{
    * myTask := {
    *   executeJs(state.value, Seq((nodeModules in Plugin).value.getCanonicalPath,
    *     baseDirectory.value / "path" / "to" / "myscript.js", Seq("arg1", "arg2"), 30.seconds)
    * }
    * }}}
    *
    * @param state The SBT state.
    * @param command An optional path to the engine.
    * @param nodeModules The node modules to provide (if the JavaScript engine in use supports this).
    * @param shellSource The script to execute.
    * @param args The arguments to pass to the script.
    * @param timeout The maximum amount of time to wait for the script to finish.
    * @return A JSON status object if one was sent by the script.  A script can send a JSON status object by, as the
    *         last thing it does, sending a DLE character (0x10) followed by some JSON to std out.
    */
  def executeJs(
    state: State,
    command: Option[File],
    nodeModules: Seq[String],
    shellSource: File,
    args: Seq[String],
    timeout: FiniteDuration
  ): Seq[JsValue] = {
    val engineProps = Node.props(command, stdEnvironment = LocalEngine.nodePathEnv(nodeModules.to[immutable.Seq]))

    withActorRefFactory(state, this.getClass.getName) { arf =>
      val engine = arf.actorOf(engineProps)
      implicit val t: Timeout = Timeout(timeout)
      import ExecutionContext.Implicits.global
      Await.result(
        executeJsOnEngine(engine, shellSource, args, m => state.log.error(m), m => state.log.info(m)),
        timeout
      )
    }
  }

}

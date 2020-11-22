package com.platypii.typescript

import sbt._
import sbt.Keys._
import com.typesafe.sbt.web.SbtWeb

object JsEngineImport {

  object JsEngineKeys {
    val command = SettingKey[Option[File]]("jse-command", "An optional path to the command used to invoke the engine.")
    val parallelism = SettingKey[Int](
      "jse-parallelism",
      "The number of parallel tasks for the JavaScript engine. Defaults to the # of available processors + 1 to keep things busy."
    )
  }

}

/**
  * Declares the main parts of a WebDriver based plugin for sbt.
  */
object SbtJsEngine extends AutoPlugin {

  override def requires: SbtWeb.type = SbtWeb

  override def trigger: PluginTrigger = AllRequirements

  val autoImport: JsEngineImport.type = JsEngineImport

  import SbtWeb.autoImport._
  import WebKeys._
  import autoImport._
  import JsEngineKeys._

  private val NodeModules = "node_modules"

  private val jsEngineUnscopedSettings: Seq[Setting[_]] = Seq(
    nodeModuleDirectories += baseDirectory.value / NodeModules
  )

  override def projectSettings: Seq[Setting[_]] =
    Seq(
      command := sys.props.get("sbt.jse.command").map(file),
      parallelism := java.lang.Runtime.getRuntime.availableProcessors() + 1
    ) ++ inConfig(Assets)(jsEngineUnscopedSettings) ++ inConfig(TestAssets)(jsEngineUnscopedSettings)

}

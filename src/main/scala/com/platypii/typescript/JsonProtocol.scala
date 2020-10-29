package com.platypii.typescript

import spray.json.{DefaultJsonProtocol, JsonFormat}

/**
  * JSON parsers
  */
trait JsonProtocol extends DefaultJsonProtocol {
  implicit val coFormat: JsonFormat[CompilerOptions] = jsonFormat10(CompilerOptions)
  implicit val tscFormat: JsonFormat[TsConfig] = jsonFormat3(TsConfig)
}

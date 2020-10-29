package com.platypii.typescript

import spray.json.{JsArray, JsBoolean, JsNull, JsNumber, JsObject, JsString}

object JsonUtil {

  /**
    * Merge two json objects
    */
  def merge(tsConfig: JsObject, tsConfigOverride: JsObject): JsObject = {
    val keys = tsConfig.fields.keySet ++ tsConfigOverride.fields.keySet
    val merged = keys.map { key =>
      (for {
        v1 <- tsConfig.getFields(key).headOption
        v2 <- tsConfigOverride.getFields(key).headOption
      } yield {
        v2 match {
          case JsNull => key -> JsNull
          case v: JsString => key -> v
          case v: JsBoolean => key -> v
          case v: JsNumber => key -> v
          case v: JsArray =>
            v1 match {
              case JsArray(elements) => key -> JsArray(elements ++ v.elements)
              case other => throw new IllegalArgumentException(s"can't override $key with $v value with $other")
            }
          case v: JsObject => key -> new JsObject(v1.asJsObject.fields ++ v.fields)
        }

      }).orElse(tsConfig.getFields(key).headOption.map(key -> _))
        .orElse(tsConfigOverride.getFields(key).headOption.map(key -> _))
    }
    new JsObject(merged.flatten.toMap)
  }
}

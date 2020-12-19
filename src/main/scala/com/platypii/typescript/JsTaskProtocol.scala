package com.platypii.typescript

import com.typesafe.sbt.web.LineBasedProblem
import com.typesafe.sbt.web.incremental.{OpFailure, OpResult, OpSuccess}
import sbt.{File, file}
import spray.json.{DefaultJsonProtocol, JsNull, JsNumber, JsObject, JsString, JsValue, JsonFormat, RootJsonFormat, deserializationError}
import xsbti.Severity

/**
  * For automatic transformation of Json structures.
  */
object JsTaskProtocol extends DefaultJsonProtocol {

  implicit object FileFormat extends JsonFormat[File] {
    override def write(f: File): JsString = JsString(f.getCanonicalPath)

    override def read(value: JsValue): sbt.File =
      value match {
        case s: JsString => new File(s.convertTo[String])
        case x => deserializationError(s"String expected for a file, instead got $x")
      }
  }

  implicit val opSuccessFormat: RootJsonFormat[OpSuccess] = jsonFormat2(OpSuccess)

  implicit object LineBasedProblemFormat extends JsonFormat[LineBasedProblem] {
    override def write(p: LineBasedProblem): JsObject =
      JsObject(
        "message" -> JsString(p.message),
        "severity" -> {
          p.severity match {
            case Severity.Info => JsString("info")
            case Severity.Warn => JsString("warn")
            case Severity.Error => JsString("error")
          }
        },
        "lineNumber" -> JsNumber(p.position.line.get),
        "characterOffset" -> JsNumber(p.position.offset.get),
        "lineContent" -> JsString(p.position.lineContent),
        "source" -> FileFormat.write(p.position.sourceFile.get)
      )

    override def read(value: JsValue): LineBasedProblem =
      value match {
        case o: JsObject =>
          new LineBasedProblem(
            o.fields.get("message").fold("unknown message")(_.convertTo[String]),
            o.fields.get("severity").fold(Severity.Error) {
              case JsString("info") => Severity.Info
              case JsString("warn") => Severity.Warn
              case _ => Severity.Error
            },
            o.fields.get("lineNumber").fold(0)(_.convertTo[Int]),
            o.fields.get("characterOffset").fold(0)(_.convertTo[Int]),
            o.fields.get("lineContent").fold("unknown line content")(_.convertTo[String]),
            o.fields.get("source").fold(file(""))(_.convertTo[File])
          )
        case x => deserializationError(s"Object expected for the problem, instead got $x")
      }

  }

  implicit object OpResultFormat extends JsonFormat[OpResult] {

    override def write(r: OpResult): JsValue =
      r match {
        case OpFailure => JsNull
        case s: OpSuccess => opSuccessFormat.write(s)
      }

    override def read(value: JsValue): OpResult =
      value match {
        case o: JsObject => opSuccessFormat.read(o)
        case JsNull => OpFailure
        case x => deserializationError(s"Object expected for the op result, instead got $x")
      }
  }

  case class ProblemResultsPair(results: Seq[SourceResultPair], problems: Seq[LineBasedProblem])

  case class SourceResultPair(result: OpResult, source: File)

  implicit val sourceResultPairFormat: RootJsonFormat[SourceResultPair] = jsonFormat2(SourceResultPair)
  implicit val problemResultPairFormat: RootJsonFormat[ProblemResultsPair] = jsonFormat2(ProblemResultsPair)
}

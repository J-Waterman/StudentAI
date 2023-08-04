package models

case class ContextQuestion(question: String, optionalParagraph: Option[String])

object ContextQuestion {
  import play.api.libs.json.Json
  implicit val format = Json.format[ContextQuestion]
}

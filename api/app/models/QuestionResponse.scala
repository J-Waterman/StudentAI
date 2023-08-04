package models

case class QuestionResponse(reasoning: Seq[String], questions: Seq[ContextQuestion], rubric: Seq[String])

object QuestionResponse {
  import play.api.libs.json.Json
  implicit val format = Json.format[QuestionResponse]
}

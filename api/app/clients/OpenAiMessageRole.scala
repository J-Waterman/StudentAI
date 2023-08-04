package clients

import play.api.libs.json.{Format, Json}

object OpenAiMessageRole extends Enumeration {
  type OpenAiMessageRole = Value
  val User = Value("user")
  val Assistant = Value("assistant")
  val System = Value("system")

  implicit val enumFormat: Format[OpenAiMessageRole] = Json.formatEnum(OpenAiMessageRole)
}

package clients

import clients.OpenAiMessageRole.OpenAiMessageRole
import play.api.libs.json.{Format, Json}

case class OpenAiMessage(
                        role: OpenAiMessageRole,
                        content: String
                        )

object OpenAiMessage {
  implicit val format: Format[OpenAiMessage] = Json.format[OpenAiMessage]
}

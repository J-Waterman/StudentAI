package clients

import play.api.libs.json.{Format, Json}

case class OpenAIRequest(
                        model: String,
                        messages: Seq[OpenAiMessage],
                        temperature: Double
                        )

object OpenAIRequest {
  implicit val format: Format[OpenAIRequest] = Json.format[OpenAIRequest]
}

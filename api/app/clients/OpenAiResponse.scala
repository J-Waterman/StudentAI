package clients

import play.api.libs.json.JsonConfiguration.Aux
import play.api.libs.json.JsonNaming.SnakeCase
import play.api.libs.json.{Format, Json, JsonConfiguration}

case class OpenAiResponseChoices(
                                index: Int,
                                message: OpenAiMessage,
                                finishReason: String
                                )

case class OpenAiResponseUsage(
                              promptTokens: Int,
                              completionTokens: Int,
                              totalTokens: Int
                              )

case class OpenAiResponse(
                         id: String,
                         `object`: String,
                         created: Long,
                         choices: Seq[OpenAiResponseChoices],
                         usage: OpenAiResponseUsage
                         )

object OpenAiResponse {
  implicit val config: Aux[Json.MacroOptions] = JsonConfiguration(SnakeCase)

  implicit val formatChoices: Format[OpenAiResponseChoices] = Json.format[OpenAiResponseChoices]
  implicit val formatUsage: Format[OpenAiResponseUsage] = Json.format[OpenAiResponseUsage]
  implicit val formatResponse: Format[OpenAiResponse] = Json.format[OpenAiResponse]
}
package clients

import models.QuestionResponse
import play.api.libs.json.{Format, JsError, JsSuccess, Json}
import sttp.client3._
import sttp.client3.playJson.asJson
import sttp.model.Uri

import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}

object OpenAiClient {
  // NOTE: For prototype purposes, this is hardcoded. In production, this should be stored in a secret manager
  val OPENAI_API_KEY: String = "REDACTED"
  val model: String = "gpt-3.5-turbo"
  val endpoint: Uri = uri"https://api.openai.com/v1/chat/completions"
  val backend = HttpClientFutureBackend()

  type OpenAiSttpResponse = Response[Either[ResponseException[String, JsError], OpenAiResponse]]

  /**
   * Make an API call to the OpenAI API.
   *
   * @param input
   * @param ec
   * @return The response from the API.
   */
  def chatCall(input: Seq[OpenAiMessage])(implicit ec: ExecutionContext): Future[OpenAiSttpResponse] = {
    val body = Json.toJson(OpenAIRequest(
      model = model, messages = input, temperature = 0.7
    ))

    basicRequest
      .headers(
        Map(
          "Authorization" -> s"Bearer $OPENAI_API_KEY",
          "OpenAI-Organization" -> "REDACTED",
          "Content-Type" -> "application/json"
        )
      )
      .readTimeout(5.minutes)
      .body(body.toString())
      .response(asJson[OpenAiResponse])
      .post(endpoint)
      .send(backend)
  }

  /**
   * Given a list of messages, prompt the AI to generate a response.
   * @param messages
   * @param ec
   * @tparam A
   * @return The AI's response
   */
  def contentCall[A](messages: Seq[OpenAiMessage])(implicit ec: ExecutionContext): Future[Option[String]] = {
    OpenAiClient.chatCall(messages).map { response =>
      response.body match {
        case Left(error) => None
        case Right(response) =>
          println("-------")
          println(response.choices.head.message.content)
          Some(response.choices.head.message.content)
      }
    }
  }


  /**
   * Given a string, prompt the AI to generate a chain of thought.
   * @param content The string to prompt the AI with.
   * @param jsonFormatString Instructions for turning the AI's chain of thought into a parsable response.
   * @param ec
   * @param format
   * @tparam A Type that can be formatted as JSON
   * @return The AI's response, formatted as the type A.
   */
  def chainOfThoughtPrompt[A](content: String, jsonFormatString: String)(implicit ec: ExecutionContext, format: Format[A]):Future[Option[A]] = {
    val chainOfThoughtPrompt = contentCall(Seq(
      OpenAiMessage(
        role = OpenAiMessageRole.User,
        content = content
      )
    ))

    chainOfThoughtPrompt.flatMap {
      case Some(chainContent) =>
        contentCall(Seq(
          OpenAiMessage(
            role = OpenAiMessageRole.User,
            content = content
          ),
          OpenAiMessage(
            role = OpenAiMessageRole.System,
            content = jsonFormatString
          ),
          OpenAiMessage(
            role = OpenAiMessageRole.Assistant,
            content = chainContent
          ),
        )).map(_.flatMap(Json.parse(_).validate[A] match {
          case JsSuccess(validatedQuestions, _) =>
            Some(validatedQuestions)
          case JsError(_) =>
            None
        }))
      case None => Future(None)
    }
  }

  /**
   * Given the student interests, prompt the AI to generate questions and a rubric.
   * @param studentInterests
   * @param ec
   * @return A list of questions, a rubric, and the reasoning behind the selection.
   */
  def frqPrompt(studentInterests: String)(implicit ec: ExecutionContext) = {
    val jsonFormatString =
      s"""
         | Format the output using the following JSON schema: {"reasoning": string[], "questions": [{"question":"", optionalParagraph:""}], "rubric":string[]}
         | Reasoning should break down each step needed to come to the necessary conclusion.
         | Each question consists of the question itself, and optional paragraph that can be used to answer the question (e.g. A quote, a short paragraph).
         | If no paragraph is needed, exclude the optionalParagraph field.
         | This rubric should be returned as a list of columns explaining the grading criteria
         |""".stripMargin

    val frqString =
      s"""
         |Generate 8 Free Response Questions for a student, aimed at testing their ability to meet the 4th Grade Common Core Writing standard.
         |This standard involves drawing evidence from literary or informational texts to support analysis, reflection, and research.
         |The questions should be designed keeping in mind the following interests: $studentInterests.
         |
         |The questions should be a creative mix, some including quotes or paragraphs and some standing alone.
         |They should be of varying lengths and difficulties to engage the student and test their understanding at different levels.
         |
         |The format for questions with quotes or paragraphs is:
         |Question: <question>
         |Paragraph/Quote: <optional paragraph/quote to go with the question>
         |
         |When a question includes a quote or paragraph, it should be marked as follows <qp>, then the actual content listed separately. For example:
         |
         |Question: Thomas said, <qp>. Can you summarize this statement?
         |Paragraph/Quote: "Trains are cool"
         |
         |Ensure that each question directly relates to the provided quote or paragraph,
         |if applicable, and that the student does not need any external information to answer the question.
         |
         |Additionally, generate a comprehensive, holistic rubric for grading the responses to these questions.
         |This rubric should be returned as a list explaining the grading criteria,
         |designed to help the student understand how to improve their answers and meet the 4th Grade Common Core Writing standard.
         |
         |Example Rubric:
         |["The response demonstrates a clear understanding of the text.","The response provides specific evidence from the text to support analysis."]
         |
         |Walk me step by step through the process of how you generated the questions and rubric, focusing on making these a good fit for the 4th Grade Common Core Writing standard.
         |""".stripMargin

    chainOfThoughtPrompt[QuestionResponse](content = frqString, jsonFormatString = jsonFormatString)
  }

  /**
   * Given the questions and student interests, prompt the AI to verify the quality of the questions.
   * @param questionResponse
   * @param studentInterests
   * @param ec
   * @return A list of the best 2 questions, the rubric, and the reasoning behind the selection.
   */
  def qcPrompt(questionResponse: QuestionResponse, studentInterests: String)(implicit ec: ExecutionContext) = {
    val jsonFormatString =
      s"""
         | Format the output using the following JSON schema: {"reasoning": string[], "questions": [{"question":"", optionalParagraph:""}], "rubric":string[]}
         | Given a response from the AI, pull out and summarize the reasoning into steps stored in the "reasoning" field.
         | The questions should consist of the best 2 questions determined by the AI.
         | The rubric should be the same as the one provided by the AI.
         |""".stripMargin

    val qcString =
      s"""
         |Now that we have a set of questions and a rubric, let's perform a second pass to ensure quality control.
         |Review the generated questions to ensure they meet the following criteria:
         |
         |- Align with the 4th Grade Common Core Writing standard, which focuses on drawing evidence from literary or informational texts to support analysis, reflection, and research.
         |- Suitability for the age group, meaning the questions should be appropriately challenging and understandable for 4th grade students.
         |- Relevance to the student's stated interests, which are $studentInterests.
         |- Educational value, ensuring each question provides an opportunity for the student to learn and grow in their understanding of the subject matter.
         |- Does not require any external information to answer the question.
         |
         |Check that questions with quotes or paragraphs follow the format:
         |
         |Question: <question>
         |Paragraph/Quote: <optional paragraph/quote to go with the question>
         |
         |And verify that any questions with the quote/paragraph in the middle contains the following marker: <qp>.
         |
         |Also, validate the rubric to ensure it offers clear and constructive grading criteria that align with the 4th Grade Common Core
         |Writing standard and helps guide the student to improve their writing skills.
         |
         |Finally, select the best 2 questions that meet all these criteria.
         |Make any modifications necessary to ensure only the highest quality questions are presented to the students.
         |
         |Provide reasoning for your selections and modifications,
         |ensuring they are the most effective at gauging a 4th-grade student's ability to draw evidence from texts to support analysis, reflection, and research.
         |
         |Questions:
         |${questionResponse.questions.map(q => {
            s"""
             |Question: ${q.question}
             |Paragraph/Quote: ${q.optionalParagraph.getOrElse("")}
             |""".stripMargin
          })}
         |
         |Rubric:
         |${questionResponse.rubric.map(r => {
            s"""
             |${r}
             |""".stripMargin
          })}
         |
         |The rubric should be a list of clearly defined criteria for grading the student's response to the questions. e.g.
         |["The response demonstrates a clear understanding of the text.","The response provides specific evidence from the text to support analysis."]
         |""".stripMargin

    // Manually validate that only two questions are selected and the quotation marker is not present.
    chainOfThoughtPrompt[QuestionResponse](
      content = qcString,
      jsonFormatString = jsonFormatString
    ).map(_.map(qr =>
      qr.copy(questions = qr.questions.take(2).map(op => op.optionalParagraph match {
        case Some(value) => op.copy(optionalParagraph = Some(value.replace("<qp>", "")))
        case None => op
      }))
    ))
  }

  /**
   * Given the questions and answers, prompt the AI to provide feedback.
   * @param questionResponse
   * @param answers
   * @param ec
   * @return A list of all answers, in order, with feedback provided by the AI using the rubric.
   */
  def feedbackPrompt(questionResponse: QuestionResponse, answers: Seq[String])(implicit ec: ExecutionContext): Future[Seq[String]] = {
    val feedbackString =
      s"""
         |Now that we have our selected questions, answers provided by the student, and our grading rubric, it's time to provide detailed feedback to the student.
         |
         |For each answer, I'd like you to:
         |- Grade the answer according to the provided rubric.
         |- Highlight specific parts of the answer that align well with the rubric and exemplify good practices according to the 4th Grade Common Core Writing standard.
         |- Identify areas where the student's answer could improve, offering specific suggestions.
         |- Ensure the feedback is constructive and encouraging to foster a positive learning environment. (But do not shy from productive criticism!)
         |- Remember, our goal is not only to grade the student's performance but also to provide them with clear guidance on how to enhance their writing skills according to the 4th Grade Common Core Writing standard.
         |
         |Rubric:
         |${
              questionResponse.rubric.map(r => {
                  s"""
                     |${r}
                     |""".stripMargin
              })
            }
         |
         |Answers:
         |${
            questionResponse.questions.zip(answers).zipWithIndex.map(qa => {
              s"""
                 |Question ${qa._2}: ${qa._1._1.question}
                 |Answer: ${qa._1._2}
                 |""".stripMargin
            })
          }
         |
         |Please walk me through how you are evaluating each answer and providing feedback, so I understand the reasoning behind your comments and suggestions.
         |""".stripMargin

    chainOfThoughtPrompt[Seq[String]](
      content = feedbackString,
      jsonFormatString = "Return the feedback for each question in order as a list of strings in an array. E.g. [\"Feedback for Q1\", \"Feedback for Q2\"]"
    ).map {
      case Some(feedback) =>
        feedback
      case None => Seq("","")
    }
  }
}

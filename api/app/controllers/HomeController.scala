package controllers

import clients.{OpenAiClient, OpenAiMessage, OpenAiMessageRole}
import com.google.inject.{Inject, Singleton}
import models.QuestionResponse
import play.api.data.Form
import play.api.data.Forms.nonEmptyText
import play.api.libs.json.{JsError, JsSuccess, Json, OFormat}
import play.api.mvc.{Action, AnyContent, BaseController, ControllerComponents, Request}

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class HomeController @Inject() (
                                 val controllerComponents: ControllerComponents
                               )(implicit ec: ExecutionContext)
  extends BaseController {

  final val LIST_DIVIDER = "{{||}}"

  val interestForm: Form[String] = Form(
    "interests" -> nonEmptyText
  )

  def index: Action[AnyContent] = Action { implicit request: Request[AnyContent] =>
    Ok(views.html.index("Home"))
  }

  def displayQuestions(): Action[AnyContent] = Action { implicit request =>
    val feedback = request.flash.get("feedback").map(_.split("\\{\\{\\|\\|}}").toSeq).getOrElse(Seq("", ""))

    val interest = Json.parse(request.flash.get("questions").getOrElse("{}")).validate[QuestionResponse] match {
      case JsSuccess(value, _) => value
      case JsError(errors) =>
        QuestionResponse(Seq.empty, Seq.empty, Seq.empty)
    }

    Ok(views.html.questions(interest, feedback))
  }

  def generateQuestions: Action[AnyContent] = Action.async { implicit request =>
    //
    interestForm.bindFromRequest().fold(
      formWithErrors => {
        Future(BadRequest("Invalid form"))
      },
      interests => {
        OpenAiClient.frqPrompt(interests).flatMap(_.map { qResponse =>
          OpenAiClient.qcPrompt(qResponse, interests).map { finalResponse =>
            Redirect(routes.HomeController.displayQuestions).flashing("questions" -> Json.toJson(finalResponse).toString())
          }
        } match {
          case Some(value) => value
          case None => Future(InternalServerError)
        })
      })
  }

  def handleAnswers: Action[AnyContent] = Action.async { implicit req =>
    // This entire function is a hack for prototyping purposes, this should never make it to a production system.
    // Persisting state through a database would be optimal.
    val formUrlEncoded = req.body.asFormUrlEncoded
    val answers: Map[String, Seq[String]] = formUrlEncoded.getOrElse(Map.empty)
    val answerSeq: Seq[String] = Seq(answers.getOrElse("answer0", Seq("")).head, answers.getOrElse("answer1", Seq("")).head)

    val questionDataString: String = answers.getOrElse("questionResponse", Seq("{}")).head

    val qDataParsed = Json.parse(questionDataString).validate[QuestionResponse] match {
      case JsSuccess(value, _) => value
      case JsError(errors) =>
        QuestionResponse(Seq.empty, Seq.empty, Seq.empty)
    }

    OpenAiClient.feedbackPrompt(qDataParsed, answerSeq).map { feedback =>
      print(feedback)
      Redirect(routes.HomeController.displayQuestions).flashing("feedback" -> feedback.mkString(LIST_DIVIDER), "questions" -> questionDataString, "answers" -> answerSeq.mkString(LIST_DIVIDER))
    }
  }
}

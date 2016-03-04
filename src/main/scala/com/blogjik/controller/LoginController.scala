package com.blogjik.controller

import javax.inject.Inject

import com.blogjik.persistence.{DBMonad, AuthorDao}
import play.api.libs.json.{Writes, Json}
import play.api.mvc.{Request, Action, Controller}

import scala.concurrent.Future


class LoginController @Inject()(authorsDao: AuthorDao) extends Controller {

  import LoginController._
  import play.api.libs.concurrent.Execution.Implicits.defaultContext

  // TODO: clear mess with email/login - use either email either login
  def login() = Action.async(parse.urlFormEncoded) { request =>

    // Just to show that actions are composable as well as queries
    def action(email: String) =  for {
      author <- authorsDao.find(authorsDao.queries.byEmail(email))({ a => a })
      loginData <- author match {
        case Some(a) =>
          authorsDao.findWithDetails(authorsDao.queries.byId(a.id) ++ authorsDao.queries.byEmail(a.email)) {
            case Some((_, Some(details))) => Some(LoginData(a.email, details.password))
            case _ => None
          }
        case None => DBMonad.successful(None)
      }
    } yield loginData

    for {
      loginData <- Future { extractLoginData(request) }
      fromDB <- authorsDao.run(action(loginData.email))
    } yield {
      (loginData, fromDB) match {
        case (requestData, Some(dbData)) if encrypted(requestData) == encrypted(dbData) => Ok("logged in")
        case _ => BadRequest("user credentials are not correct")
      }
    }
  }

}


object LoginController {

  case class LoginData(email: String, password: String)
  implicit val loginDataWrites: Writes[LoginData] = Json.writes[LoginData]

  private def extractLoginData(request: Request[Map[String, Seq[String]]]): LoginData = {
    val requestBody: Map[String, Seq[String]] = request.body
    val email = requestBody.getOrElse("usermail", Seq("")).head
    val password = requestBody.getOrElse("password", Seq("")).head
    LoginData(email, password)
  }

  private def encrypted(loginData: LoginData): String =
    encrypted(loginData.email, loginData.password)

  private def encrypted(email: String, password: String): String =
    s"$email+._.+$password"
}

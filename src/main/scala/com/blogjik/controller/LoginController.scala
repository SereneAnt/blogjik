package com.blogjik.controller

import javax.inject.Inject

import com.blogjik.persistence.AuthorDao
import play.api.libs.json.{Writes, Json}
import play.api.mvc.{Request, Action, Controller}

import scala.concurrent.Future


class LoginController @Inject()(authorsDao: AuthorDao) extends Controller {

  import LoginController._
  import play.api.libs.concurrent.Execution.Implicits.defaultContext

  def login() = Action.async(parse.urlFormEncoded) { request =>
    for {
      loginData <- Future { extractLoginData(request) }
      fromDB <- authorsDao.findWithDetails(authorsDao.queries.getByEmail(loginData.email)) {
        case Some(pair@(author, Some(details))) => Some(LoginData(details.login, details.password))
        case _ => None
      }
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

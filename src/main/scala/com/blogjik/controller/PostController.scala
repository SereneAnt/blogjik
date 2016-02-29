package com.blogjik.controller

import java.time.{ZoneOffset, OffsetDateTime}
import java.time.format.DateTimeFormatter
import javax.inject.Inject

import com.blogjik.model.Post
import com.blogjik.persistence.PostDao
import play.api.mvc.Action

import play.api.libs.json._
import play.api.mvc._

import scala.util.{Success, Try}


class PostController @Inject()(postDao: PostDao) extends Controller {

  import PostController._
  import play.api.libs.concurrent.Execution.Implicits.defaultContext

  def list() = Action.async({ request =>
    postDao.list().map( seq => Ok(Json.toJson(seq)) )
  })

  def find(id: String) = Action.async({ request =>
    postDao.list().map( seq => Ok(Json.toJson(seq)) )
  })

//  def save() = {}
//  def update(id: String) = {}
}


object PostController {

  val dateTimeFormat = DateTimeFormatter.ISO_OFFSET_DATE_TIME

  implicit val dataFormat: Format[OffsetDateTime] = new Format[OffsetDateTime] {
    override def writes(o: OffsetDateTime): JsValue = JsString(dateTimeFormat.format(o.withOffsetSameInstant(ZoneOffset.UTC)))

    override def reads(json: JsValue): JsResult[OffsetDateTime] = {
      ((readString andThen parseDate andThen (_success orElse _error)) orElse _error)(json)
    }

    private def readString: PartialFunction[JsValue, String] = {
      case JsString(date) => date
    }

    private def parseDate: PartialFunction[String, Try[OffsetDateTime]] = {
      case date => Try(OffsetDateTime.from(dateTimeFormat.parse(date)))
    }

    private def _success: PartialFunction[Try[OffsetDateTime], JsResult[OffsetDateTime]] = {
      case Success(d) => JsSuccess(d)
    }

    private def _error: PartialFunction[Any, JsResult[OffsetDateTime]] = {
      case _ => JsError("date time should be in correct format - ISO Date Time format")
    }

  }

  implicit val postFormat: Format[Post] = Json.format[Post]
}
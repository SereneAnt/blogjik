package com.blogjik.util


import play.api.libs.json._
import play.api.mvc.{Results, Result}

import scala.util.{Success, Try}


object ControllerUtils {

  def readString: PartialFunction[JsValue, String] = {
    case JsString(date) => date
  }

  def _success[T]: PartialFunction[Try[T], JsResult[T]] = {
    case Success(d) => JsSuccess(d)
  }

  def _error[T]: PartialFunction[Any, JsResult[T]] = {
    case _ => JsError("date time should be in correct format - ISO Date Time format")
  }



  def _returnData[T](implicit writes: Writes[T]): PartialFunction[Option[T], Result] = {
    case Some(result) => Results.Ok(Json.toJson(result)(writes))
  }

  def _notFound[T]: PartialFunction[Option[T], Result] = {
    case _ => Results.NotFound
  }
}

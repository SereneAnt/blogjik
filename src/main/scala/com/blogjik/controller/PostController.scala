package com.blogjik.controller

import java.time.{ZoneOffset, OffsetDateTime}
import java.time.format.DateTimeFormatter
import javax.inject.Inject

import com.blogjik.model.{Author, Post}
import com.blogjik.persistence.{DBMonad, AuthorDao, PostDao}
import play.api.mvc.Action

import play.api.libs.json._
import play.api.mvc._

import scala.util.{Success, Try}


class PostController @Inject()(postDao: PostDao, authorDao: AuthorDao) extends Controller {

  import PostController._
  import play.api.libs.concurrent.Execution.Implicits.defaultContext

  def list() = Action.async({ request =>
    postDao.run(postDao.list(postDao.postQueries.*())).map(seq => Ok(Json.toJson(seq)) )
  })

  def listWithAuthors() = Action.async({ request =>
    val authorQ = postDao.authorQueries
    val postQ = postDao.postQueries
    postDao.run(postDao.listAuthorWithPosts(authorQ.*(), postQ.*()).map(toDto)).map(seq => Ok(Json.toJson(seq)))
  })

  def find(id: String) = Action.async({ request =>
    val postQ = postDao.postQueries
    postDao.run(postDao.find(postQ.byId(id))).map({
      case Some(post) => Ok(Json.toJson(post))
      case None => NotFound
    })
  })

  // example of composition in dao layer
  def findByAuthorId(id: String) = Action.async({ request =>
    val authorQ = postDao.authorQueries
    val postQ = postDao.postQueries
    postDao.run(postDao.findAuthorWithPost(authorQ.byAuthor(id), postQ.*())).map(toDto).map({
      case Some(dto) => Ok(Json.toJson(dto))
      case None => NotFound
    })
  })

  // example of composition outside dao layer
  def findByAuthorIdV2(id: String) = Action.async({ request =>
    val action = for {
      author <- authorDao.find(authorDao.queries.byId(id))
      posts <- author match {
        case Some(a) => postDao.list(postDao.postQueries.byAuthor(a.id))
        case _ => DBMonad.successful(Seq())
      }
    } yield author.map(a => (a ,posts))

    postDao.run(action).map(toDto).map({
      case Some(dto) => Ok(Json.toJson(dto))
      case None => NotFound
    })

  })

  def statistic() = Action.async({ request =>
    postDao.statistic().map({ seq => Ok(Json.toJson(seq.map(StatisticDto.tupled))) })
  })
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
  implicit val authorFormat: Format[Author] = Json.format[Author]
  implicit val authorPostFormat: Format[AuthorPostDto] = Json.format[AuthorPostDto]
  implicit val statisticFormat: Format[StatisticDto] = Json.format[StatisticDto]

  case class AuthorPostDto(author: Author, posts: Seq[Post])
  case class StatisticDto(postId: String, authorId: String, title: String, likes: Int, avgByAuthor: Double)

  def toDto(seq : Seq[(Author, Seq[Post])]) : Seq[AuthorPostDto] = {
    seq.map({case (author, posts) => AuthorPostDto(author= author, posts = posts) })
  }

  def toDto(seq : Option[(Author, Seq[Post])]) : Option[AuthorPostDto] = {
    seq.map({case (author, posts) => AuthorPostDto(author= author, posts = posts) })
  }
}
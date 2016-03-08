package com.blogjik.controller

import java.time.{ZoneOffset, OffsetDateTime}
import java.time.format.DateTimeFormatter
import javax.inject.Inject

import com.blogjik.model.{Author, Post}
import com.blogjik.persistence.{DBMonad, AuthorDao, PostDao}
import play.api.mvc.Action

import play.api.libs.json._
import play.api.mvc._

import scala.util.Try


class PostController @Inject()(postDao: PostDao, authorDao: AuthorDao) extends Controller {

  import PostController._
  import com.blogjik.util.ControllerUtils._
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
    postDao.run(postDao.find(postQ.byId(id))).map({ _returnData[Post] orElse _notFound })
  })

  /**
    * Preparing statement: select "author_id", "name", "email" from "authors" where "author_id" = '1' limit 1
    * Execution of prepared statement took 3ms
    *   - /-----------+------+---------------\
    *   - | 1         | 2    | 3             |
    *   - | author_id | name | email         |
    *   - |-----------+------+---------------|
    *   - | 1         | Bob  | bob@gmail.com |
    *   - \-----------+------+---------------/
    *   - Preparing statement: select "author_id", "likes", "title", "post_id", "updated", "created", "body" from "posts" where "author_id" = '1'
    *   - Execution of prepared statement took 1ms
    *   - /-----------+-------+--------------+---------+----------------------+----------------------+---------------------\
    *   - | 1         | 2     | 3            | 4       | 5                    | 6                    | 7                   |
    *   - | author_id | likes | title        | post_id | updated              | created              | body                |
    *   - |-----------+-------+--------------+---------+----------------------+----------------------+---------------------|
    *   - | 1         | 1     | first blog!  | 1       | 2016-02-26 17:09:... | 2016-02-26 17:09:... | this is first blog  |
    *   - | 1         | 7     | second blog! | 2       | 2016-03-01 11:17:... | 2016-03-01 11:17:... | this is second blog |
    *   - | 1         | 12    | third        | 3       | 2016-03-01 16:07:... | 2016-03-01 16:07:... | thir                |
    *   - \-----------+-------+--------------+---------+----------------------+----------------------+---------------------/
    */
  // example of composition outside dao layer
  def findByAuthorIdV2(id: String) = Action.async({ request =>
    val action = for {
      author <- authorDao.find(authorDao.queries.byId(id))
      posts <- author match {
        case Some(a) => postDao.list(postDao.postQueries.byAuthor(a.id))
        case _ => DBMonad.successful(Seq())
      }
    } yield author.map(a => (a, posts))

    postDao.run(action).map(toDto).map({ _returnData[AuthorPostDto] orElse _notFound })
  })

  /**
    *   - Preparing statement: select x2.x3, x4."body", x4."author_id", x4."post_id", x2.x5, x2.x6, x4."created", x4."updated", x4."title", x4."likes" from (select "author_id" as x6, "name" as x3, "email" as x5 from "authors" where "author_id" = '1' limit 1) x2 left outer join "posts" x4 on x2.x6 = x4."author_id"
    *   - Execution of prepared statement took 1ms
    *   - /-----+---------------------+-----------+---------+---------------+----+----------------------+----------------------+--------------+-------\
    *   - | 1   | 2                   | 3         | 4       | 5             | 6  | 7                    | 8                    | 9            | 10    |
    *   - | x3  | body                | author_id | post_id | x5            | x6 | created              | updated              | title        | likes |
    *   - |-----+---------------------+-----------+---------+---------------+----+----------------------+----------------------+--------------+-------|
    *   - | Bob | this is first blog  | 1         | 1       | bob@gmail.com | 1  | 2016-02-26 17:09:... | 2016-02-26 17:09:... | first blog!  | 1     |
    *   - | Bob | this is second blog | 1         | 2       | bob@gmail.com | 1  | 2016-03-01 11:17:... | 2016-03-01 11:17:... | second blog! | 7     |
    *   - | Bob | thir                | 1         | 3       | bob@gmail.com | 1  | 2016-03-01 16:07:... | 2016-03-01 16:07:... | third        | 12    |
    *   - \-----+---------------------+-----------+---------+---------------+----+----------------------+----------------------+--------------+-------/
    */
  // example of composition in dao layer
  def findByAuthorId(id: String) = Action.async({ request =>
    val authorQ = postDao.authorQueries
    val postQ = postDao.postQueries
    postDao.run(postDao.findAuthorWithPost(authorQ.byAuthor(id), postQ.*()))
      .map(toDto).map({ _returnData[AuthorPostDto] orElse _notFound })
  })

  def statistic() = Action.async({ request =>
    postDao.statistic().map({ seq => Ok(Json.toJson(seq.map(StatisticDto.tupled))) })
  })
}


object PostController {
  import com.blogjik.util.ControllerUtils._

  val dateTimeFormat = DateTimeFormatter.ISO_OFFSET_DATE_TIME

  implicit val dataFormat: Format[OffsetDateTime] = new Format[OffsetDateTime] {
    override def writes(o: OffsetDateTime): JsValue = JsString(dateTimeFormat.format(o.withOffsetSameInstant(ZoneOffset.UTC)))

    override def reads(json: JsValue): JsResult[OffsetDateTime] = {
      ((readString andThen parseDate andThen (_success[OffsetDateTime] orElse _error[OffsetDateTime])) orElse _error[OffsetDateTime])(json)
    }

    private def parseDate: PartialFunction[String, Try[OffsetDateTime]] = {
      case date => Try(OffsetDateTime.from(dateTimeFormat.parse(date)))
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

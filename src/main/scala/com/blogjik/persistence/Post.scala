package com.blogjik.persistence

import java.time.OffsetDateTime
import javax.inject.Inject

import com.blogjik.model.{Author, Post}
import com.blogjik.persistence.driver.PostgresDriver
import com.google.inject.ImplementedBy
import play.api.db.slick.{DatabaseConfigProvider, HasDatabaseConfigProvider, HasDatabaseConfig}

import scala.concurrent.Future


trait PostComponent {
  self: HasDatabaseConfig[PostgresDriver] =>

  import driver.api._


  class PostTable(tag: Tag) extends Table[Post](tag, "posts") {

    import PostTable._

    def id = column[String]("post_id", O.PrimaryKey)
    def authorId = column[String]("author_id", O.Length(150, varying = true))
    def title = column[String]("title", O.Length(250, varying = true))
    def body = column[String]("body", O.Length(2000, varying = true))
    def created = column[OffsetDateTime]("created")
    def updated = column[Option[OffsetDateTime]]("updated")

    def allColumns = (id, authorId, title, body, created, updated)

    // Every table needs a * projection with the same type as the table's type parameter
    def * = allColumns.shaped <> (mapRowTupled, unMapRow)
  }

  object PostTable {
    def mapRow(id: String, authorId: String, title: String, body: String,
               created: OffsetDateTime, updated: Option[OffsetDateTime]): Post = {
      Post(id, authorId, title, body, created, updated)
    }

    val mapRowTupled = (mapRow _).tupled

    def unMapRow(p: Post) = {
      val tuple = (p.id, p.authorId, p.title, p.body, p.created, p.updated)
      Some(tuple)
    }
  }

  val posts = TableQuery[PostTable]
}

@ImplementedBy(classOf[PostDaoImpl])
trait PostDao {
  def list(): Future[Seq[Post]]
  def listWithAuthors[T](block: (Seq[(Author, Option[Post])]) => Seq[T]): Future[Seq[T]]
  def save(p: Post): Future[Unit]
}

class PostDaoImpl @Inject() (protected val dbConfigProvider: DatabaseConfigProvider)
  extends PostDao
  with PostComponent
  with AuthorComponent
  with HasDatabaseConfigProvider[PostgresDriver] {

  import driver.api._
  import play.api.libs.concurrent.Execution.Implicits.defaultContext

  override def list(): Future[Seq[Post]] = db.run(listAction(posts))

  override def save(post: Post): Future[Unit] = db.run(DBIO.seq(posts += post))

  override def listWithAuthors[T](block: (Seq[(Author, Option[Post])]) => Seq[T]): Future[Seq[T]] = {
    val action = for {
      seq <- listWithAuthorsAction(authors, posts)
      rez <- DBIO.successful(block(seq))
    } yield rez
    db.run(action)
  }

  private def listAction(postsQuery: Query[PostTable, Post, Seq]): DBIO[Seq[Post]] = {
    val query = for {
      p <- postsQuery
    } yield p.allColumns

    query.result.map(seq => seq.map { post => PostTable.mapRowTupled(post) })
  }

  private def listWithAuthorsAction(authorsQuery: Query[AuthorTable, Author, Seq],
                                    postsQuery: Query[PostTable, Post, Seq]): DBIO[Seq[(Author, Option[Post])]] = {
    val query = for {
      (a, p) <- authorsQuery joinLeft postsQuery on (_.id === _.authorId)
    } yield (a.allColumns, p.map(_.allColumns))

    query.result.map(seq => seq.map {
      case (author, post) => (AuthorTable.mapRowTupled(author), post.map(PostTable.mapRowTupled))
    })
  }

}

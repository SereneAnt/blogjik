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
    def likes = column[Int]("likes")

    def allColumns = (id, authorId, title, body, created, updated, likes)

    // Every table needs a * projection with the same type as the table's type parameter
    def * = allColumns.shaped <> (mapRowTupled, unMapRow)
  }

  object PostTable {
    def mapRow(id: String, authorId: String, title: String, body: String,
               created: OffsetDateTime, updated: Option[OffsetDateTime],
               likes: Int): Post = {
      Post(id, authorId, title, body, created, updated, likes)
    }

    val mapRowTupled = (mapRow _).tupled

    def unMapRow(p: Post) = {
      val tuple = (p.id, p.authorId, p.title, p.body, p.created, p.updated, p.likes)
      Some(tuple)
    }
  }

  val posts = TableQuery[PostTable]
}

@ImplementedBy(classOf[PostDaoImpl])
trait PostDao {
  type PostQuery
  type AuthorsQuery
  def list[T](query: PostQuery)(block: (Seq[Post]) => Seq[T]): Future[Seq[T]]
  def find[T](query: PostQuery)(block: (Option[Post]) => Option[T]): Future[Option[T]]
  def listAuthorWithPosts[T](authorsQuery: AuthorsQuery, query: PostQuery)(block: (Seq[(Author, Seq[Post])]) => Seq[T]): Future[Seq[T]]
  def findAuthorWithPost[T](authorsQuery: AuthorsQuery, query: PostQuery)(block: (Option[(Author, Seq[Post])]) => Option[T]): Future[Option[T]]


  def statistic(): Future[Seq[(String, String, String, Int, Double)]]

  def authorQueries: AuthorQ
  def postQueries: PostQ
  trait PostQ {
    def *(): PostQuery
    def byId(id: String): PostQuery
    def byTitle(title: String): PostQuery
  }
  trait AuthorQ {
    def *(): AuthorsQuery
    def byAuthor(authorId: String): AuthorsQuery
  }
}

class PostDaoImpl @Inject() (protected val dbConfigProvider: DatabaseConfigProvider)
  extends PostDao
  with PostComponent
  with AuthorComponent
  with HasDatabaseConfigProvider[PostgresDriver] {

  import driver.api._
  import play.api.libs.concurrent.Execution.Implicits.defaultContext

  type PostQuery = Query[PostTable, Post, Seq]
  type AuthorsQuery = Query[AuthorTable, Author, Seq]


  override def list[T](query: PostQuery)(block: (Seq[Post]) => Seq[T]): Future[Seq[T]] = {
    val action = for {
      seq <- listAction(query)
      rez <- DBIO.successful(block(seq))
    } yield rez

    db.run(action.transactionally)
  }

  def find[T](query: PostQuery)(block: (Option[Post]) => Option[T]): Future[Option[T]] = {
    val action = for {
      seq <- listAction(query.take(1))
      rez <- DBIO.successful(block(seq.headOption))
    } yield rez

    db.run(action.transactionally)
  }

  def listAuthorWithPosts[T](authorsQuery: AuthorsQuery, query: PostQuery)(block: (Seq[(Author, Seq[Post])]) => Seq[T]): Future[Seq[T]] = {
    val action = for {
      seq <- listAuthorWithPostsAction(authorsQuery, query)
      rez <- DBIO.successful(block(seq))
    } yield rez
    db.run(action.transactionally)
  }

  def findAuthorWithPost[T](authorsQuery: AuthorsQuery, query: PostQuery)(block: (Option[(Author, Seq[Post])]) => Option[T]): Future[Option[T]] = {
    val action = for {
      seq <- listAuthorWithPostsAction(authorsQuery.take(1), query)
      rez <- DBIO.successful(block(seq.headOption))
    } yield rez
    db.run(action.transactionally)
  }

  override lazy val postQueries: PostQ = new PostQ {
    def *(): PostQuery = posts
    def byId(id: String): PostQuery = posts.filter(_.id === id)
    def byTitle(title: String): PostQuery = posts.filter(_.title like title)
    def byAuthor(authorId: String): PostQuery = posts.filter(_.authorId === authorId)
  }

  override lazy val authorQueries: AuthorQ = new AuthorQ {
    def *(): AuthorsQuery = authors
    def byAuthor(authorId: String): AuthorsQuery = authors.filter(_.id === authorId)
  }

  override def statistic(): Future[Seq[(String, String, String, Int, Double)]] = {
    db.run(sql"SELECT post_id, author_id, title, likes, avg(likes) OVER(PARTITION BY author_id) from posts".as[(String, String, String, Int, Double)])
  }

  private def listAction(postsQuery: Query[PostTable, Post, Seq]): DBIO[Seq[Post]] = {
    val query = for {
      p <- postsQuery
    } yield p.allColumns

    query.result.map(seq => seq.map { post => PostTable.mapRowTupled(post) })
  }

  private def listAuthorWithPostsAction(authorsQuery: Query[AuthorTable, Author, Seq],
                                    postsQuery: Query[PostTable, Post, Seq]): DBIO[Seq[(Author, Seq[Post])]] = {
    val query = for {
      (a, p) <- authorsQuery joinLeft postsQuery on (_.id === _.authorId)
    } yield (a.allColumns, p.map(_.allColumns))

    // TODO describe this magic
    query.result.map( seq => seq.groupBy(_._1._1).map({
      case (author_id, queryResult) =>
        (AuthorTable.mapRowTupled(queryResult.head._1),
          queryResult.collect({ case (_, Some(post)) => PostTable.mapRowTupled(post) }))
    }).toSeq )

  }
}

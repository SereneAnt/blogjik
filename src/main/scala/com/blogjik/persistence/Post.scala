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

  protected type P
  protected type A

  type PostQuery = DBQuery[P, Post, Seq]
  type AuthorsQuery = DBQuery[A, Author, Seq]

  def list(query: PostQuery): DBMonad[Seq[Post]]
  def find(query: PostQuery): DBMonad[Option[Post]]
  def listAuthorWithPosts(authorsQuery: AuthorsQuery, query: PostQuery): DBMonad[Seq[(Author, Seq[Post])]]
  def findAuthorWithPost(authorsQuery: AuthorsQuery, query: PostQuery): DBMonad[Option[(Author, Seq[Post])]]
  def run[T](action: DBMonad[T]): Future[T]

  def statistic(): Future[Seq[(String, String, String, Int, Double)]]

  def authorQueries: AuthorQ
  def postQueries: PostQ

  trait PostQ {
    def *(): PostQuery
    def byId(id: String): PostQuery
    def byTitle(title: String): PostQuery
    def byAuthor(authorId: String): PostQuery
  }

  trait AuthorQ {
    def *(): AuthorsQuery
    def byAuthor(authorId: String): AuthorsQuery
  }
}

class PostDaoImpl @Inject() (protected val dbConfigProvider: DatabaseConfigProvider) extends PostDao
    with PostComponent
    with AuthorComponent
    with HasDatabaseConfigProvider[PostgresDriver] {

  import driver.api._
  import play.api.libs.concurrent.Execution.Implicits.defaultContext

  override protected type P = PostTable
  override protected type A = AuthorTable

  override def list(query: PostQuery): DBMonad[Seq[Post]] = {
    val action = for {
      seq <- listAction(query.underling)
      rez <- DBIO.successful(seq)
    } yield rez

    DBMonad(action)
  }

  override def find(query: PostQuery): DBMonad[Option[Post]] = {
    val action = for {
      seq <- listAction(query.underling.take(1))
      rez <- DBIO.successful(seq.headOption)
    } yield rez

    DBMonad(action)
  }

  override def listAuthorWithPosts(authorsQuery: AuthorsQuery, query: PostQuery): DBMonad[Seq[(Author, Seq[Post])]] = {
    val action = for {
      seq <- listAuthorWithPostsAction(authorsQuery.underling, query.underling)
      rez <- DBIO.successful(seq)
    } yield rez

    DBMonad(action)
  }

  override def findAuthorWithPost(authorsQuery: AuthorsQuery, query: PostQuery): DBMonad[Option[(Author, Seq[Post])]] = {
    val action = for {
      seq <- listAuthorWithPostsAction(authorsQuery.underling.take(1), query.underling)
      rez <- DBIO.successful(seq.headOption)
    } yield rez

    DBMonad(action)
  }

  override def run[T](action: DBMonad[T]): Future[T] = {
    print(action.underling)
    db.run(action.underling.transactionally)
  }

  override lazy val postQueries: PostQ = new PostQ {
    def *(): PostQuery = DBQuery(posts)
    def byId(id: String): PostQuery = DBQuery(posts.filter(_.id === id))
    def byTitle(title: String): PostQuery = DBQuery(posts.filter(_.title like title))
    def byAuthor(authorId: String): PostQuery = DBQuery(posts.filter(_.authorId === authorId))
  }

  override lazy val authorQueries: AuthorQ = new AuthorQ {
    def *(): AuthorsQuery = DBQuery(authors)
    def byAuthor(authorId: String): AuthorsQuery = DBQuery(authors.filter(_.id === authorId))
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

    // TODO describe group by logic
    query.result.map( seq => seq.groupBy(_._1._1).map({
      case (author_id, queryResult) =>
        (AuthorTable.mapRowTupled(queryResult.head._1),
          queryResult.collect({ case (_, Some(post)) => PostTable.mapRowTupled(post) }))
    }).toSeq )

  }
}

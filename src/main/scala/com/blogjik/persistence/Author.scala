package com.blogjik.persistence

import javax.inject.Inject

import com.blogjik.model.{Author, Details}
import com.blogjik.persistence.driver.PostgresDriver
import com.google.inject.ImplementedBy
import play.api.db.slick.{HasDatabaseConfig, HasDatabaseConfigProvider, DatabaseConfigProvider}
import scala.concurrent.Future
import scala.language.higherKinds


trait AuthorComponent {
  self: HasDatabaseConfig[PostgresDriver] =>

  import driver.api._

  class AuthorTable(tag: Tag) extends Table[Author](tag, "authors") {

    import AuthorTable._

    def id = column[String]("author_id", O.PrimaryKey)
    def name = column[String]("name", O.Length(250, varying = true))
    def email = column[String]("email", O.Length(250, varying = true))

    def allColumns = (id, name, email)

    // Every table needs a * projection with the same type as the table's type parameter
    def * = allColumns.shaped <> (mapRowTupled, unMapRow)
  }

  object AuthorTable {
    def mapRow(id: String, name: String, email: String): Author = {
      Author(id, name, email)
    }

    val mapRowTupled = (mapRow _).tupled

    def unMapRow(p: Author) = {
      val tuple = (p.id, p.name, p.email)
      Some(tuple)
    }
  }

  class DetailTable(tag: Tag) extends Table[Details](tag, "details") {

    import DetailTable._

    def authorId = column[String]("author_id", O.PrimaryKey, O.Length(250, varying = true))
    def login = column[String]("login", O.Length(250, varying = true))
    def password = column[String]("password", O.Length(250, varying = true))

    def allColumns = (authorId, login, password)

    // Every table needs a * projection with the same type as the table's type parameter
    def * = allColumns.shaped <> (mapRowTupled, unMapRow)
  }

  object DetailTable {
    def mapRow(authorId: String, login: String, password: String): Details = {
      Details(authorId, login, password)
    }

    val mapRowTupled = (mapRow _).tupled

    def unMapRow(p: Details) = {
      val tuple = (p.authorId, p.login, p.password)
      Some(tuple)
    }
  }

  val authors = TableQuery[AuthorTable]
  val details = TableQuery[DetailTable]
}


@ImplementedBy(classOf[AuthorDaoImpl])
trait AuthorDao {

  protected type A
  type AuthorQuery = DBQuery[A, Author, Seq]

  def list(query: AuthorQuery): DBMonad[Seq[Author]]
  def find(query: AuthorQuery): DBMonad[Option[Author]]
  def save(author: Author): DBMonad[Author]
  def findWithDetails(query: AuthorQuery): DBMonad[Option[(Author, Option[Details])]]
  def listWithDetails(query: AuthorQuery): DBMonad[Seq[(Author, Option[Details])]]
  def run[T](action: DBMonad[T]): Future[T]


  def queries: Q

  trait Q {
    def *(): AuthorQuery
    def byId(id: String): AuthorQuery
    def byEmail(email: String): AuthorQuery
  }
}

class AuthorDaoImpl @Inject() (protected val dbConfigProvider: DatabaseConfigProvider) extends AuthorDao
    with AuthorComponent
    with HasDatabaseConfigProvider[PostgresDriver]   {

  import driver.api._
  import play.api.libs.concurrent.Execution.Implicits.defaultContext

  override protected type A = AuthorTable

  override def list(query: AuthorQuery): DBMonad[Seq[Author]] = {
    DBMonad(listAction(query.underling))
  }

  override def find(query: AuthorQuery): DBMonad[Option[Author]] = {
    val action = for {
      seq <- listAction(query.underling.take(1))
      rez <- DBIO.successful(seq.headOption)
    } yield rez

    DBMonad(action)
  }

  override def save(author: Author): DBMonad[Author] = {
    val action = for {
      _ <- DBIO.seq(authors += author)
      rez <- DBIO.successful(author)
    } yield rez

    DBMonad(action)
  }

  override def listWithDetails(query: AuthorQuery): DBMonad[Seq[(Author, Option[Details])]] = {
    DBMonad(listWithDetailsAction(query.underling, details))
  }

  override def findWithDetails(query: AuthorQuery): DBMonad[Option[(Author, Option[Details])]] = {
    val action = for {
      seq <- listWithDetailsAction(query.underling.take(1), details)
      rez <- DBIO.successful(seq.headOption)
    } yield rez

    DBMonad(action)
  }

  override lazy val queries: Q = new Q {
    override def *(): AuthorQuery = DBQuery(authors)

    override def byId(id: String): AuthorQuery = {
      DBQuery(authors.filter(_.id === id))
    }

    override def byEmail(email: String): AuthorQuery = {
      DBQuery(authors.filter(_.email === email))
    }
  }

  override def run[T](action: DBMonad[T]): Future[T] = db.run(action.underling.transactionally)

  private def listAction(authorsQuery: Query[AuthorTable, Author, Seq]): DBIO[Seq[Author]] = {
    val query = for {
      author <- authorsQuery
    } yield author.allColumns

    query.result.map(seq => seq.map { author => AuthorTable.mapRowTupled(author) })
  }

  private def listWithDetailsAction(authorsQuery: Query[AuthorTable, Author, Seq],
                                    detailsQuery: Query[DetailTable, Details, Seq]): DBIO[Seq[(Author, Option[Details])]] = {
    val query = for {
      (a, d) <- authorsQuery joinLeft detailsQuery on (_.id === _.authorId)
    } yield (a.allColumns, d.map(_.allColumns))

    query.result.map(seq => seq.map {
      case (author, d) => (AuthorTable.mapRowTupled(author), d.map(DetailTable.mapRowTupled))
    })
  }
}

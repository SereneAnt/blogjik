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
trait AuthorDao extends AuthorComponent with HasDatabaseConfigProvider[PostgresDriver]  {

  type AuthorQuery = DBQuery[AuthorTable, Author, Seq]

  def list[T](query: AuthorQuery)(block: (Seq[Author]) => Seq[T]): DBMonad[Seq[T]]
  def find[T](query: AuthorQuery)(block: (Option[Author]) => Option[T]): DBMonad[Option[T]]
  def save(author: Author): Future[Unit]
  def findWithDetails[T](query: AuthorQuery)(block: Option[(Author, Option[Details])] => Option[T]): DBMonad[Option[T]]
  def listWithDetails[T](query: AuthorQuery)(block: (Seq[(Author, Option[Details])]) => Seq[T]): DBMonad[Seq[T]]
  def run[T](action: DBMonad[T]): Future[T]

  def queries: Q
  trait Q {
    def *(): AuthorQuery
    def byId(id: String): AuthorQuery
    def byEmail(email: String): AuthorQuery
  }
}

class AuthorDaoImpl @Inject() (protected val dbConfigProvider: DatabaseConfigProvider) extends AuthorDao {

  import driver.api._
  import play.api.libs.concurrent.Execution.Implicits.defaultContext

  override def list[T](query: AuthorQuery)(block: (Seq[Author]) => Seq[T]): DBMonad[Seq[T]] = {
    val action = for {
      seq <- listAction(query.underling)
      rez <- DBIO.successful(block(seq))
    } yield rez

    DBMonad(action)
  }

  override def find[T](query: AuthorQuery)(block: (Option[Author]) => Option[T]): DBMonad[Option[T]] = {
    val action = for {
      seq <- listAction(query.underling.take(1))
      rez <- DBIO.successful(block(seq.headOption))
    } yield rez

    DBMonad(action)
  }

  override def save(author: Author): Future[Unit] = db.run(DBIO.seq(authors += author))

  override def listWithDetails[T](query: AuthorQuery)(block: (Seq[(Author, Option[Details])]) => Seq[T]): DBMonad[Seq[T]] = {
    val action = for {
      seq <- listWithDetailsAction(query.underling, details)
      rez <- DBIO.successful(block(seq))
    } yield rez

    DBMonad(action)
  }

  override def findWithDetails[T](query: AuthorQuery)(block: (Option[(Author, Option[Details])]) => Option[T]): DBMonad[Option[T]] = {
    val action = for {
      seq <- listWithDetailsAction(query.underling.take(1), details)
      rez <- DBIO.successful(block(seq.headOption))
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

  def run[T](action: DBMonad[T]): Future[T] = db.run(action.underlined.transactionally)

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

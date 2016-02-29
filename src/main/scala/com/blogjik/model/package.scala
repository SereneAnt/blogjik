package com.blogjik

import java.time.OffsetDateTime


package object model {

  case class Author(id: String, name: String, email: String)
  case class Post(id: String, authorId: String, title: String, body: String, created: OffsetDateTime, updated: Option[OffsetDateTime])
  case class Details(authorId: String, login: String, password: String)
}

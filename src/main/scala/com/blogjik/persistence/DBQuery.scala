package com.blogjik.persistence

import slick.lifted.Query

import scala.language.higherKinds


class DBQuery[+E, U, C[_]](private[persistence] val underling: Query[E, U, C]) {

  def ++[O >: E, R, D[_]](other: DBQuery[O, U, D]): DBQuery[O, U, C] = DBQuery(underling ++ other.underling)
}

object DBQuery {

  def apply[E, U, C[_]](underling: Query[E, U, C]): DBQuery[E, U, C] = new DBQuery[E, U, C](underling)
}

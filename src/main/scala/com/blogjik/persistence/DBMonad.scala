package com.blogjik.persistence

import slick.dbio.DBIO

import scala.concurrent.ExecutionContext


class DBMonad[+R](private[persistence] val underling: DBIO[R])  {

  def flatMap[R2](f: (R) => DBMonad[R2])(implicit executor: ExecutionContext): DBMonad[R2] = DBMonad(underling.flatMap(f andThen(_.underling)))
  def map[R2](f: R => R2)(implicit executor: ExecutionContext): DBMonad[R2] = DBMonad(underling.map(f))
  def filter(p: R => Boolean)(implicit executor: ExecutionContext): DBMonad[R] = DBMonad(underling.filter(p))
  def withFilter(p: R => Boolean)(implicit executor: ExecutionContext): DBMonad[R] = DBMonad(underling.withFilter(p))
}

object DBMonad {

  def apply[R](underlined: DBIO[R]) = new DBMonad[R](underlined)
  def successful[R](v: R): DBMonad[R] = DBMonad(DBIO.successful(v))
  def failed(t: Throwable): DBMonad[Nothing] = DBMonad(DBIO.failed(t))
}

package com.blogjik.persistence.driver

import com.github.tminglei.slickpg._

trait PostgresDriver
  extends ExPostgresDriver
  with PgArraySupport
  with PgDate2Support
  with PgHStoreSupport
  with PgLTreeSupport
  with PgNetSupport
  with PgRangeSupport
{
  override val api = MyAPI

  object MyAPI
    extends API
    with ArrayImplicits
    with DateTimeImplicits
    with Date2DateTimePlainImplicits
    with HStoreImplicits
    with LTreeImplicits
    with NetImplicits
    with RangeImplicits
}

object PostgresDriver extends PostgresDriver

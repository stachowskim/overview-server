package controllers.backend

import scala.collection.immutable
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import slick.lifted.RunnableCompiled

import com.overviewdocs.database.Database
import models.pagination.{Page,PageInfo,PageRequest}

trait DbBackend {
  protected val database: Database

  import database.api._

  /** Returns a Page[T] based on an item query, uncompiled.
    *
    * This is the only way to handle `WHERE ... IN (...)` queries. It takes
    * more CPU than `RunnableCompiled` queries.
    */
  def page[T](itemsQ: Rep[Seq[T]], countQ: Rep[Int], pageRequest: PageRequest): Future[Page[T]] = {
    // Sequential, so Postgres can benefit from a hot cache on the second query
    val action = for {
      items <- itemsQ.result
      count <- countQ.result
    } yield Page(items.toIndexedSeq, PageInfo(pageRequest, count))

    database.run(action)
  }

  /** Returns a Page[T] based on item and count queries.
    *
    * The itemsQ is compiled, so it must already include
    * `.drop(offset).take(limit)`. That makes `pageRequest` seems a redundant,
    * but let's not lose sleep over it.
    */
  def page[T](itemsQ: RunnableCompiled[_, Seq[T]], countQ: RunnableCompiled[_, Int], pageRequest: PageRequest): Future[Page[T]] = {
    // Sequential, so Postgres can benefit from a hot cache on the second query
    val action = for {
      items <- itemsQ.result
      count <- countQ.result
    } yield Page(items.toIndexedSeq, PageInfo(pageRequest, count))

    database.run(action)
  }

  def emptyPage[T](pageRequest: PageRequest) = Future.successful(Page(Vector[T](), PageInfo(pageRequest, 0)))
}

/**
 * DocumentSetIndexer.scala
 * Given a list of DoucmentAtURL obejcts, retrieve the text of each, index, build tree, add to the DB
 *
 * Overview Project, created August 2012
 *
 * @author Jonathan Stray
 *
 */

package overview.clustering

import java.sql.Connection
import overview.clustering.ClusterTypes._
import overview.http._
import overview.util.{ DocumentConsumer, Logger, WorkerActorSystem }
import overview.util.DocumentSetCreationJobStateDescription._
import overview.util.Progress._
import persistence.{ DocumentWriter, NodeWriter }
import database.DB

import akka.actor._
import akka.dispatch.{ Future, Promise, Await }
import akka.util.Timeout

class DocumentSetIndexer(nodeWriter: NodeWriter, documentWriter: DocumentWriter, progAbort: ProgressAbortFn) extends DocumentConsumer {

  // --- private ---
  val t0 = System.nanoTime()
  private var fractionFetched = 0
  private val fetchingFraction = 0.9 // what percent done do we say when we're all done fetching docs?
  private val savingFraction = 0.99

  private val vectorGen = new DocumentVectorGenerator

  // When we get the document text back, we add the document to the database and feed the text to the vector generator
  def processDocument(documentId: Long, text: String): Unit = {
    vectorGen.addDocument(documentId.toInt, Lexer.makeTerms(text))
  }

  private def addDocumentDescriptions(docTree: DocTreeNode)(implicit c: Connection) {
    if (docTree.docs.size == 1 && docTree.description != "")
      documentWriter.updateDescription(docTree.docs.head, docTree.description)
    else docTree.children.foreach(addDocumentDescriptions)
  }

  def productionComplete() {
    Logger.logElapsedTime("Retrieved " + vectorGen.numDocs, t0)
    
    progAbort(Progress(fetchingFraction, Clustering))
    val t1 = System.nanoTime()
    val docVecs = vectorGen.documentVectors()
    val docTree = BuildDocTree(docVecs, makeNestedProgress(progAbort, fetchingFraction, savingFraction))
    DB.withConnection { implicit connection => addDocumentDescriptions(docTree) }

    Logger.logElapsedTime("Clustered documents", t1)

    // Save tree to database
    progAbort(Progress(savingFraction, Saving))
    val t2 = System.nanoTime()
    DB.withTransaction { implicit connection =>
      nodeWriter.write(docTree)
    }
    Logger.logElapsedTime("Saved DocumentSet to DB", t2)

    progAbort(Progress(1, Done))
  }
}

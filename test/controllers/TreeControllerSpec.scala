package controllers

import play.api.mvc.Flash
import org.specs2.specification.Scope
import org.specs2.matcher.Matcher

import org.overviewproject.tree.orm.{DocumentSet, DocumentSetCreationJob, Tree}

class TreeControllerSpec extends ControllerSpecification {
  trait BaseScope extends Scope {
    val mockStorage = mock[TreeController.Storage]

    val controller = new TreeController {
      override val storage = mockStorage
    }
    def show(documentSetId: Long, treeId: Long) = controller.show(documentSetId, treeId)(fakeAuthorizedRequest)
  }

  "TreeController.create" should {
    trait CreateScope extends BaseScope {
      val documentSetId = 1L
      def formBody : Seq[(String,String)] = Seq("lang" -> "en", "supplied_stop_words" -> "", "important_words" -> "")
      def request = fakeAuthorizedRequest.withFormUrlEncodedBody(formBody: _*)
      def create(documentSetId: Long) = controller.create(documentSetId)(request)
      def result = create(documentSetId)
    }

    "store a DocumentSetCreationJob" in new CreateScope {
      h.status(result) // store result
      def beJobWithDocumentSetId(id: Long) : Matcher[DocumentSetCreationJob] = beLike{ case (j: DocumentSetCreationJob) => j.documentSetId must beEqualTo(id) }
      there was one(mockStorage).insertJob(argThat(beJobWithDocumentSetId(documentSetId)))
    }

    "redirect to /documentsets" in new CreateScope {
      h.redirectLocation(result) must beSome("/documentsets")
    }

    "flash event -> tree-create" in new CreateScope {
      h.flash(result) must beLike { case f: Flash => f.get("event") must beSome("tree-create") }
    }

    "return a BAD_REQUEST if the form is filled in badly" in new CreateScope {
      override def formBody = Seq()
      h.status(result) must beEqualTo(h.BAD_REQUEST)
    }
  }

  "TreeController.show" should {
    trait ValidShowScope extends BaseScope {
      val mockTree = mock[Tree]
      mockTree.documentSetId returns 1

      mockStorage.findDocumentSet(1) returns Some(mock[DocumentSet])
      mockStorage.findTree(2) returns Some(mockTree)

      lazy val result = show(1, 2)
    }

    "return NotFound when the DocumentSet is not present" in new BaseScope {
      mockStorage.findDocumentSet(1) returns None
      mockStorage.findTree(2) returns Some(mock[Tree])

      h.status(show(1, 2)) must beEqualTo(h.NOT_FOUND)
    }

    "return NotFound when the DocumentSet is present but the Tree is not" in new BaseScope {
      mockStorage.findDocumentSet(1) returns Some(mock[DocumentSet])
      mockStorage.findTree(2) returns None

      h.status(show(1, 2)) must beEqualTo(h.NOT_FOUND)
    }

    "return NotFound when the Tree does not belong to the DocumentSet" in new BaseScope {
      val mockTree = mock[Tree]
      mockTree.documentSetId returns 2L

      mockStorage.findDocumentSet(1) returns Some(mock[DocumentSet])
      mockStorage.findTree(2) returns None

      h.status(show(1, 2)) must beEqualTo(h.NOT_FOUND)
    }

    "return Ok when okay" in new ValidShowScope {
      h.status(result) must beEqualTo(h.OK)
    }

    "return some HTML when okay" in new ValidShowScope {
      h.contentType(result) must beSome("text/html")
    }

    "return data-is-searchable=false when it is not" in new ValidShowScope {
      mockStorage.isDocumentSetSearchable(any[DocumentSet]) returns false
      h.contentAsString(result) must contain("""<div id="main" data-is-searchable="false"></div>""")
    }

    "return data-is-searchable=true when it is" in new ValidShowScope {
      mockStorage.isDocumentSetSearchable(any[DocumentSet]) returns true
      h.contentAsString(result) must contain("""<div id="main" data-is-searchable="true"></div>""")
    }
  }
}

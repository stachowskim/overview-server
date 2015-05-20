package org.overviewproject.jobhandler.filegroup.task.step


import scala.concurrent.Future
import scala.slick.jdbc.JdbcBackend.Session
import org.overviewproject.test.SlickSpecification
import org.overviewproject.test.SlickClientInSession
import org.overviewproject.models.GroupedFileUpload

class FindUploadedFileSpec extends SlickSpecification {

  "FindUploadedFile" should {

    "find file and pass it on the the next step" in new UploadedFileScope {
       val step = await(findUploadedFile.execute)
       
       step must beLike {
         case NextStep(f) => f.id must be equalTo uploadedFile.id 
       }
    }

    "fail if file cannot be found" in new MissingUploadScope {
      val r = findUploadedFile.execute
      
      r.failed must haveClass[NoSuchElementException].await
    }
  }
  
  case class NextStep(uploadedFile: GroupedFileUpload) extends TaskStep {
    override protected def doExecute: Future[TaskStep] = Future.successful(this)
  }

  trait UploadedFileScope extends DbScope {
    val fileGroup = factory.fileGroup()
    val uploadedFile = factory.groupedFileUpload(fileGroupId = fileGroup.id)

    val findUploadedFile = new TestFindUploadedFile(uploadedFileId)

    def uploadedFileId = uploadedFile.id
    
    class TestFindUploadedFile(override protected val uploadedFileId: Long)(implicit val session: Session) 
    extends FindUploadedFile with SlickClientInSession {
      override protected val documentSetId = 1l
      
      override protected val nextStep = NextStep
    }
  }
  
  trait MissingUploadScope extends UploadedFileScope {
    override def uploadedFileId = 0l
  }
}

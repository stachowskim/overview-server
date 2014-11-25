package org.overviewproject.blobstorage

import com.typesafe.config.{Config,ConfigFactory}
import org.specs2.mock.Mockito
import org.specs2.mutable.Specification
import org.specs2.specification.Scope

class BlobStorageConfigSpec extends Specification with Mockito {
  trait BaseScope extends Scope {
    val sysConfig: Config
    lazy val subject = new BlobStorageConfig {
      override protected val config = sysConfig
    }
  }

  "#getPreferredPrefix" should {
    "get a preferred prefix" in new BaseScope {
      override val sysConfig = ConfigFactory.parseString("""blobStorage.preferredPrefixes.pageData: "file:page-data" """)
      subject.getPreferredPrefix(BlobBucketId.PageData) must beEqualTo("file:page-data")
    }
  }

  "#fileBaseDirectory" should {
    "get the file base directory" in new BaseScope {
      override val sysConfig = ConfigFactory.parseString("""blobStorage.file.baseDirectory: "/tmp/overview-files" """)
      subject.fileBaseDirectory must beEqualTo("/tmp/overview-files")
    }
  }

  "#awsCredentials" should {
    "get the credentials" in new BaseScope {
      override val sysConfig = ConfigFactory.parseString("""
        blobStorage: {
          s3: {
            accessKeyId: "access-key-id"
            secretKey: "secret-key"
          }
        }
      """)
      val credentials = subject.awsCredentials
      credentials.getAWSAccessKeyId must beEqualTo("access-key-id")
      credentials.getAWSSecretKey must beEqualTo("secret-key")
    }
  }
}

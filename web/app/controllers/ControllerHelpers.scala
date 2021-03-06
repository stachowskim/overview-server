package controllers

import java.nio.ByteBuffer
import java.util.UUID
import play.api.libs.json.JsValue
import play.api.mvc.{Request,RequestHeader}
import scala.collection.immutable
import scala.util.control.Exception.catching

import models.pagination.PageRequest
import models.IdList

/** Utilities that don't depend on the environment. */
trait ControllerHelpers {
  private def queryStringParamToUnsignedInt(request: RequestHeader, param: String): Option[Int] = {
    request.queryString
      .getOrElse(param, Seq())
      .headOption
      .flatMap((s) => catching(classOf[NumberFormatException]).opt(s.toInt))
      .filter((i) => i >= 0)
  }

  private def queryStringParamToBoolean(request: RequestHeader, param: String): Option[Boolean] = {
    request.queryString
      .getOrElse(param, Seq())
      .headOption
      .map(_ == "true")
  }

  /** Returns a Map[String,String]: like requestData() but only the first
    * String.
    */
  protected def flatRequestData(request: Request[_]): Map[String,String] = {
    requestData(request)
      .mapValues(_.headOption)
      .collect { case (k, Some(v)) => (k -> v) }
  }

  /** Mimics Play's Form.bindFromRequest(). */
  protected def requestData(request: Request[_]): Map[String,Seq[String]] = {
    import play.api.mvc.{AnyContent,MultipartFormData}
    //import play.api.data.FormUtils
    // FormUtils is private[data]. Stupid.
    import play.api.libs.json._
    def fromJson(prefix: String = "", js: JsValue): Map[String, String] = js match {
      case JsObject(fields) => {
        fields.map { case (key, value) => fromJson(Option(prefix).filterNot(_.isEmpty).map(_ + ".").getOrElse("") + key, value) }.foldLeft(Map.empty[String, String])(_ ++ _) }
      case JsArray(values) => {
        values.zipWithIndex.map { case (value, i) => fromJson(prefix + "[" + i + "]", value) }.foldLeft(Map.empty[String, String])(_ ++ _) }
      case JsNull => Map.empty
      case JsUndefined() => Map.empty
      case JsBoolean(value) => Map(prefix -> value.toString)
      case JsNumber(value) => Map(prefix -> value.toString)
      case JsString(value) => Map(prefix -> value.toString)
    }

    val postData = request.body match {
      case body: AnyContent if body.asFormUrlEncoded.isDefined => body.asFormUrlEncoded.get
      case body: AnyContent if body.asMultipartFormData.isDefined => body.asMultipartFormData.get.asFormUrlEncoded
      case body: AnyContent if body.asJson.isDefined => fromJson(js = body.asJson.get).mapValues(Seq(_))
      case body: Map[_, _] => body.asInstanceOf[Map[String, Seq[String]]]
      case body: MultipartFormData[_] => body.asFormUrlEncoded
      case body: JsValue => fromJson(js = body).mapValues(Seq(_))
      case _ => Map.empty[String, Seq[String]]
    }

    request.queryString ++ postData
  }

  protected case class RequestData(request: Request[_]) {
    val data = requestData(request)

    /** Returns Some("foo") if query param key="foo"; returns Some("") if the
      * key is set but empty; returns None otherwise.
      */
    def getString(key: String): Option[String] = {
      data.get(key).flatMap(_.headOption)
    }

    /** Returns true if query param key="true"; returns false if the key is set
      * to anything else; returns None otherwise.
      */
    def getBoolean(key: String): Option[Boolean] = {
      getString(key)
        .map(_ == "true")
    }

    /** Returns a UUID if query param key=[valid UUID]; returns None otherwise.
      */
    def getUUID(key: String): Option[UUID] = {
      getString(key)
        .flatMap((s) => catching(classOf[IllegalArgumentException]).opt(UUID.fromString(s)))
    }

    /** Returns an Int if it exists and is valid; None otherwise.
      */
    def getInt(key: String): Option[Int] = {
      getString(key)
        .flatMap((s) => catching(classOf[IllegalArgumentException]).opt(s.toInt))
    }

    /** Returns an Int if it exists and is &gt;0, 1 otherwise.
      */
    def getRequestedPageBase1: Int = {
      getInt("page")
        .map { i => if (i <= 0) 1 else i }
        .getOrElse(1)
    }

    /** Returns a Seq[Long] if key is set to something like "1,2,3"; returns
      * an empty Seq otherwise.
      */
    def getLongs(key: String): immutable.Seq[Long] = {
      val s = getString(key).getOrElse("")
      IdList.longs(s).ids
    }

    /** Decodes a BitSet from an encoded input; returns None if decoding
      * fails or the key is not specified.
      */
    def getBase64BitSet(key: String): Option[immutable.BitSet] = {
      getString(key) match {
        case None => None
        case Some(s) => {
          val normalized = s
            .replace('-', '+')
            .replace('_', '/')
          val bytes = try {
            java.util.Base64.getDecoder.decode(normalized)
          } catch {
            case e: IllegalArgumentException => return None
          }

          val byteBuffer = ByteBuffer
            .allocate((7 + bytes.length) / 8 * 8) // Pad it so .asLongBuffer works
          val longBuffer = byteBuffer.asLongBuffer
          byteBuffer.put(bytes)
          val longs = Array.ofDim[Long](longBuffer.capacity)
          longBuffer.get(longs)

          // Scala's BitSet stores the least-significant integer at the last
          // bit. For instance, if BitSet's internal Long is 0b0000...00011,
          // that stores the numbers 1 and 2. But over the wire, "1 and 2" is
          // sent as 0b11000000. So we need to reverse each Long -- but not
          // the order of the Longs themselves.
          val reversedLongs = longs.map(i => java.lang.Long.reverse(i))
          Some(immutable.BitSet.fromBitMaskNoCopy(reversedLongs))
        }
      }
    }
  }

  protected def pageRequest(request: RequestHeader, maxLimit: Int): PageRequest = {
    val requestOffset = queryStringParamToUnsignedInt(request, "offset")
    val requestLimit = queryStringParamToUnsignedInt(request, "limit")
    val requestReverse = queryStringParamToBoolean(request, "reverse")

    val offset = requestOffset.getOrElse(0)
    val limit = math.max(1, math.min(maxLimit, requestLimit.getOrElse(maxLimit)))
    val reverse = requestReverse.getOrElse(false)
    PageRequest(offset, limit, reverse)
  }

  protected def jsonError(code: String, message: String): JsValue = views.json.api.error(code, message)
}

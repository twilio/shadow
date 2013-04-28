package com.twilio.shadow

import org.scalatest.FunSpec
import spray.httpx.RequestBuilding._
import spray.http.{HttpHeaders, HttpEntity, StatusCodes, HttpResponse}
import org.scalatest.matchers.ShouldMatchers

class UISuite extends FunSpec with ShouldMatchers {

  val shadowJson = """{"request":{"original":{"url":"/","headers":{},"post":{},"method":"GET","get":{}},"modified":{"url":"/","headers":{},"post":{},"method":"GET","get":{}}},"results":[{"headers":{"Connection":"close"},"status_code":200,"type":"http_response","body":"OK","elapsed_time":100},{"headers":{"Connection":"close"},"status_code":500,"type":"http_response","body":"GG","elapsed_time":200}]}"""

  val shadowEntry = ShadowEntry(
    Get("/"),
    (
      (HttpResponse(
        StatusCodes.OK,
        HttpEntity("OK"),
        HttpHeaders.Connection("close") :: Nil), 100),
      (HttpResponse(
        StatusCodes.InternalServerError,
        HttpEntity("GG"),
        HttpHeaders.Connection("close") :: Nil), 200)
      )
  )

  describe ("JsonUtil") {
    it("should correctly serialize a shadowEntry") {
      import org.json4s.native.JsonMethods._

      compact(render(JsonUtil.shadowEntryJson(shadowEntry))) should be (shadowJson)
    }
  }
}

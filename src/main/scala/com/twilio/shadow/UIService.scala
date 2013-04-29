package com.twilio.shadow

import akka.actor.{Props, Actor}
import spray.routing._
import spray.http._
import spray.util.SprayActorLogging
import spray.http.MediaTypes._
import spray.can.server.HttpServer
import spray.http.HttpResponse
import spray.routing.RequestContext
import spray.http.ChunkedResponseStart
import org.json4s.native.JsonMethods._
import org.json4s.JsonDSL._
import com.codahale.metrics.MetricRegistry
import com.codahale.metrics.json.MetricsModule
import java.util.concurrent.TimeUnit
import com.fasterxml.jackson.databind.ObjectMapper


class UIActor(val metricRegistry: MetricRegistry) extends Actor with UIService {

  def actorRefFactory = context

  def receive = runRoute(myRoute)

}

object JsonUtil {
  implicit val defaultFormats = org.json4s.DefaultFormats
  import spray.httpx.unmarshalling._

  def shadowEntryJson(entry: ShadowEntry) = {
    val req = httpRequestToJson(entry.request)

    val json =
      ("request" -> (
        ("original" -> req) ~
          ("modified" -> req)
        )) ~
        ("results" -> List(entry.responses._1, entry.responses._2).map( x => httpResponseToJson(x._1) ~ ("elapsed_time" -> x._2) ))

    json
  }

  def httpRequestToJson(httpRequest: HttpRequest) = {

    val request = httpRequest.parseAll

    val formParams = request.entity.as[FormData].fold( fa => Map[String, String](), fb => fb.fields)

    val queryParams = request.queryParams

    val json =
      ("url" -> request.path) ~
        ("headers" -> request.headers.map { x => x.name -> x.value }.toMap ) ~
        ("post" -> formParams) ~
        ("method" -> request.method.value) ~
        ("get" -> queryParams)
    json
  }

  def httpResponseToJson(httpResponse: HttpResponse) = {

    val json =
      ("headers" -> httpResponse.headers.map { x => (
        (x.name -> x.value)
        ) }.toMap) ~
        ("status_code" -> httpResponse.status.value) ~
        ("type" -> "http_response") ~
        ("body" -> httpResponse.entity.asString)
    json
  }
}

class ResponseStreamActor(val ctx: RequestContext) extends Actor with SprayActorLogging {
  context.system.eventStream.subscribe(self, classOf[ShadowEntry])

  val responseStart = HttpResponse(
    entity = HttpBody(CustomMediaType("text/event-stream"), EmptyEntity.buffer),
    headers = List(HttpHeaders.`Cache-Control`(CacheDirectives.`no-cache`), HttpHeaders.Connection("Keep-Alive")))

  ctx.responder ! ChunkedResponseStart(responseStart)

  def receive = {
    case shadowEntry: ShadowEntry => {
      val jsonString = compact(render(JsonUtil.shadowEntryJson(shadowEntry)))
      ctx.responder ! MessageChunk(s"data: $jsonString\n\n")
    }

    case HttpServer.Closed(_, reason) =>
      log.warning("Stopping response streaming due to {}", reason)
      context.stop(self)
  }

}

trait UIService extends HttpService {

  val metricRegistry: MetricRegistry

  val objectMapper = new ObjectMapper()

  objectMapper.registerModule(new MetricsModule(TimeUnit.SECONDS, TimeUnit.SECONDS, false))

  val prettyJsonPrinter = objectMapper.writerWithDefaultPrettyPrinter()


  val myRoute =
    path("stream") {
      get { ctx =>
          actorRefFactory.actorOf(Props(new ResponseStreamActor(ctx)))
      }
    } ~
    path("stats") {
      get {
        complete {
          prettyJsonPrinter.writeValueAsString(metricRegistry)
        }
      }
    } ~
    path("") {
      get {
        getFromResource("static/index.html")
      }
    } ~
    pathPrefix("static/") {
      get {
        getFromResourceDirectory("static")
      }
    }
}



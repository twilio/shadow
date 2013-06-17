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

class ResponseStreamActor(val ctx: RequestContext) extends Actor with SprayActorLogging {
  context.system.eventStream.subscribe(self, classOf[ShadowEntry])

  val responseStart = HttpResponse(
    entity = HttpBody(CustomMediaType("text/event-stream"), EmptyEntity.buffer),
    headers = List(HttpHeaders.`Cache-Control`(CacheDirectives.`no-cache`), HttpHeaders.Connection("Keep-Alive")))

  ctx.responder ! ChunkedResponseStart(responseStart)

  def receive = {
    case shadowEntry: ShadowEntry => {
      val jsonString = compact(render(shadowEntry.json))
      ctx.responder ! MessageChunk(s"data: $jsonString\n\n")
    }

    case HttpServer.Closed(_, reason) =>
      log.warning("Stopping response streaming due to {}", reason)
      context.system.eventStream.unsubscribe(self)
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
      get {
        ctx =>
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



package com.twilio.shadow

import akka.actor.{ActorSystem, ActorRef, Actor}
import spray.routing._
import spray.can.client.HttpDialog
import scala.concurrent.{Promise, Future}
import spray.http._
import com.codahale.metrics.{Timer, MetricRegistry}
import spray.httpx.marshalling._
import spray.httpx.unmarshalling._
import spray.httpx.marshalling.BasicMarshallers._
import spray.http.HttpResponse
import scala.util.Try
import java.net.URI
import org.slf4j.LoggerFactory
import org.json4s.native.JsonMethods._
import ch.qos.logback.classic.{Level, PatternLayout, LoggerContext, Logger}
import ch.qos.logback.core.FileAppender
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.classic.encoder.PatternLayoutEncoder
import ch.qos.logback.core.encoder.Encoder

class ProxyActor(
                  val httpClient: ActorRef,
                  val metricsRegistry: MetricRegistry,
                  val shadowConfig: ShadowConfig) extends Actor with ProxyService {

  def actorRefFactory = context

  def actorSystem = context.system

  def receive = runRoute(route)

}

case class ShadowEntry(request: HttpRequest, responses: ((HttpResponse, Long), (HttpResponse, Long))) {

  implicit val defaultFormats = org.json4s.DefaultFormats

  import org.json4s.JsonDSL._
  import spray.httpx.unmarshalling._

  private def shadowEntryJson(entry: ShadowEntry) = {
    val req = httpRequestToJson(entry.request)

    ("request" -> (
      ("original" -> req) ~
        ("modified" -> req)
      )) ~
      ("results" -> List(entry.responses._1, entry.responses._2)
        .map(x => httpResponseToJson(x._1) ~ ("elapsed_time" -> x._2))
        )
  }

  private def httpRequestToJson(httpRequest: HttpRequest) = {

    val request = httpRequest.parseAll

    val formParams = request.entity.as[FormData].fold(fa => Map[String, String](), fb => fb.fields)

    val queryParams = request.queryParams

    ("url" -> request.path) ~
      ("headers" -> request.headers.map {
        x => x.name -> x.value
      }.toMap) ~
      ("post" -> formParams) ~
      ("method" -> request.method.value) ~
      ("get" -> queryParams)
  }

  private def httpResponseToJson(httpResponse: HttpResponse) = {
    ("headers" -> httpResponse.headers.map {
      x => (
        (x.name -> x.value)
        )
    }.toMap) ~
      ("status_code" -> httpResponse.status.value) ~
      ("type" -> "http_response") ~
      ("body" -> httpResponse.entity.asString)
  }

  val json = shadowEntryJson(this)
}

case class ShadowServerConfig(host: String, port: Int, queryOverride: Map[String, Seq[String]], formOverride: Map[String, Seq[String]])

case class ShadowConfig(
                         trueServer: ShadowServerConfig,
                         shadowServer: ShadowServerConfig,
                         resultsLogFile: String
                         )

trait ProxyService extends HttpService {

  val httpClient: ActorRef
  val metricsRegistry: MetricRegistry
  val shadowConfig: ShadowConfig

  def actorSystem: ActorSystem

  val trueTimer: Timer = metricsRegistry.timer(MetricRegistry.name(getClass, "true-server-resp"))
  val shadowTimer: Timer = metricsRegistry.timer(MetricRegistry.name(getClass, "shadow-server-resp"))

  val loggingContext = LoggerFactory.getILoggerFactory.asInstanceOf[LoggerContext]

  val fileAppender = new FileAppender[ILoggingEvent]()

  fileAppender.setContext(loggingContext)
  fileAppender.setFile(shadowConfig.resultsLogFile)

  val encoder = new PatternLayoutEncoder()

  encoder.setContext(loggingContext)
  encoder.setPattern("%m%n")
  encoder.start()

  fileAppender.setEncoder(encoder.asInstanceOf[Encoder[ILoggingEvent]])
  fileAppender.start()

  val rootLogger = loggingContext.getLogger("ROOT")

  val consoleAppender = rootLogger.getAppender("console")

  val jsonLogger = loggingContext.getLogger("JsonLogger")

  jsonLogger.setLevel(Level.TRACE)

  jsonLogger.addAppender(fileAppender)

  rootLogger.detachAppender(consoleAppender)
  jsonLogger.detachAppender(consoleAppender)


  def time[A](timer: Timer, future: Future[A]): Future[Long] = {
    val ctx = timer.time()
    val promise = Promise[Long]()
    future.onComplete {
      x =>
        val timeElapsed = ctx.stop()
        promise.success(timeElapsed)
    }
    promise.future
  }

  def overrideValues(original: Map[String, String], overrides: Map[String, Seq[String]]) = {
    overrides.foldLeft(original) {
      case (orig, (name, values)) =>
        values.foldLeft(orig) {
          case (x, y) => x.updated(name, y)
        }
    }
  }

  def buildNewRequest(request: HttpRequest, config: ShadowServerConfig): HttpRequest = {

    val newUri = Try({
      val origUri = URI.create(request.uri)
      val queryParams = overrideValues(request.queryParams, config.queryOverride)
      new URI(origUri.getScheme, origUri.getUserInfo, origUri.getHost, origUri.getPort, origUri.getPath, marshal(FormData(queryParams)).right.get.asString, origUri.getFragment).toASCIIString
    }).getOrElse(request.uri)


    val newEntity = request.header[HttpHeaders.`Content-Type`] match {
      case Some(_) => {
        Try({
          val form = request.entity.as[FormData].right.get
          val formParams = form.copy(fields = overrideValues(form.fields, config.formOverride))
          marshal(formParams).right.get
        }).getOrElse(request.entity)

      }
      case None => request.entity
    }

    HttpRequest(request.method, newUri, request.headers, newEntity, request.protocol)
  }

  val route: Route =
    noop {
      ctx => {

        val req = ctx.request.parseQuery

        val trueReq = buildNewRequest(req, shadowConfig.trueServer)
        val shadowReq = buildNewRequest(req, shadowConfig.shadowServer)

        val trueRespF = HttpDialog(httpClient, shadowConfig.trueServer.host, shadowConfig.trueServer.port).send(trueReq).end
        val shadowRespF = HttpDialog(httpClient, shadowConfig.shadowServer.host, shadowConfig.shadowServer.port).send(shadowReq).end

        val trueTimeF = time(trueTimer, trueRespF)
        val shadowTimeF = time(shadowTimer, shadowRespF)

        for {
          trueResp <- trueRespF
          trueRespTime <- trueTimeF
          shadowResp <- shadowRespF
          shadowRespTime <- shadowTimeF
        } yield {
          val entry = ShadowEntry(req, ((trueResp, trueRespTime), (shadowResp, shadowRespTime)))
          jsonLogger.trace(compact(render(entry.json)))
          actorSystem.eventStream.publish(entry)
        }

        ctx.complete {
          trueRespF
        }

      }
    }

}

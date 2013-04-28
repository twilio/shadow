package com.twilio.shadow

import akka.actor.{ActorRef, Actor}
import spray.util.SprayActorLogging
import scala.collection.mutable
import spray.http.{FormData, HttpRequest, HttpResponse}
import spray.httpx.unmarshalling._
import org.json4s.JsonDSL._
import org.json4s.native.JsonMethods._

case class Register(eventSourceActor: ActorRef)
case class UnRegister(eventSourceActor: ActorRef)
case class Broadcast(message: AnyRef)

class HubActor extends Actor with SprayActorLogging {

  val eventSources = mutable.HashSet[ActorRef]()

  def receive = {
    case Register(sseActor) => {
      eventSources += sseActor

      log.info(s"Registering actor $sseActor")
    }
    case UnRegister(sseActor) => {
      eventSources -= sseActor
      log.info(s"UnRegistering actor $sseActor")
    }

    case Broadcast(entry: ShadowEntry) => {
      val jsonStr = compact(render(shadowEntryJson(entry)))
      eventSources.foreach( _ ! jsonStr )
    }
  }


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

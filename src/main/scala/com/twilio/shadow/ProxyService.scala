package com.twilio.shadow

import akka.actor.{ActorSystem, ActorRef, Actor}
import spray.routing._
import spray.can.client.HttpDialog
import scala.concurrent.{Promise, Future}
import spray.http.{HttpResponse, HttpRequest}
import com.codahale.metrics.{Timer, MetricRegistry}


class ProxyActor(
    val httpClient: ActorRef,
    val metricsRegistry: MetricRegistry,
    val shadowConfig: ShadowConfig) extends Actor with ProxyService {

  def actorRefFactory = context

  def actorSystem = context.system

  def receive = runRoute(route)

}

case class ShadowEntry(request: HttpRequest, responses: ((HttpResponse, Long), (HttpResponse, Long)))

case class ShadowConfig(trueHost: String, truePort: Int, shadowHost: String, shadowPort: Int)

trait ProxyService extends HttpService {

  val httpClient: ActorRef
  val metricsRegistry: MetricRegistry
  val shadowConfig: ShadowConfig
  def actorSystem: ActorSystem

  val trueTimer: Timer = metricsRegistry.timer(MetricRegistry.name(getClass, "true-server-resp"))
  val shadowTimer: Timer = metricsRegistry.timer(MetricRegistry.name(getClass, "shadow-server-resp"))

  def time[A](timer: Timer, future: Future[A]): Future[Long] = {
    val ctx = timer.time()
    val promise = Promise[Long]()
    future.onComplete { x =>
      val timeElapsed = ctx.stop()
      promise.success(timeElapsed)
    }
    promise.future
  }

  val route: Route =
      noop {
        ctx => {

          val req = ctx.request

          val trueRespF = HttpDialog(httpClient, shadowConfig.trueHost, shadowConfig.truePort).send(req).end
          val shadowRespF = HttpDialog(httpClient, shadowConfig.shadowHost, shadowConfig.shadowPort).send(req).end

          val trueTimeF = time(trueTimer, trueRespF)
          val shadowTimeF = time(shadowTimer, shadowRespF)

          for {
            trueResp <- trueRespF
            trueRespTime <- trueTimeF
            shadowResp <- shadowRespF
            shadowRespTime <- shadowTimeF
          } yield {
            actorSystem.eventStream.publish(ShadowEntry(req, ((trueResp, trueRespTime), (shadowResp, shadowRespTime))))
          }

          ctx.complete {
            trueRespF
          }

        }
    }

}

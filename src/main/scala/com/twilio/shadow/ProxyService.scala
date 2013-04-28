package com.twilio.shadow

import akka.actor.{ActorSystem, ActorRef, Actor}
import spray.routing._
import spray.routing.directives.LoggingMagnet
import spray.can.client.HttpDialog
import com.yammer.metrics.core.{MetricsRegistry, Clock, Timer}
import scala.concurrent.{Promise, Future}
import spray.http.{HttpResponse, HttpRequest}
import java.util.concurrent.TimeUnit


class ProxyActor(
    val httpClient: ActorRef,
    val metricsRegistry: MetricsRegistry,
    val shadowConfig: ShadowConfig) extends Actor with ProxyService {

  def actorRefFactory = context

  def actorSystem = context.system

  def receive = runRoute(route)

}

case class ShadowEntry(request: HttpRequest, responses: ((HttpResponse, Long), (HttpResponse, Long)))

case class ShadowConfig(trueHost: String, truePort: Int, shadowHost: String, shadowPort: Int)

trait ProxyService extends HttpService {

  val httpClient: ActorRef
  val metricsRegistry: MetricsRegistry
  val shadowConfig: ShadowConfig
  def actorSystem: ActorSystem

  val trueTimer: Timer = metricsRegistry.newTimer(getClass, "true-server-resp")
  val shadowTimer: Timer = metricsRegistry.newTimer(getClass, "shadow-server-resp")

  def time[A](timer: Timer, future: Future[A]): Future[Long] = {
    val start = Clock.defaultClock().tick()
    val ctx = timer.time()
    val promise = Promise[Long]()
    future.onComplete { x =>
      ctx.stop()
      promise.success(TimeUnit.NANOSECONDS.toMillis(Clock.defaultClock().tick() - start))
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

package com.twilio.shadow

import org.scalatest.matchers.ShouldMatchers
import org.scalatest.{GivenWhenThen, BeforeAndAfter, FunSpec}
import akka.actor._
import spray.io.IOExtension
import com.xebialabs.restito.server.StubServer
import spray.can.client.{HttpDialog, HttpClient}
import com.xebialabs.restito.builder.stub.StubHttp.whenHttp
import com.xebialabs.restito.semantics.Action.{custom => customAction, _}
import com.xebialabs.restito.semantics.Condition._
import spray.can.server.HttpServer
import akka.testkit.TestProbe
import spray.io.IOServer.Bind
import spray.httpx.RequestBuilding.Get
import scala.concurrent.Future
import java.util.concurrent.TimeUnit
import org.scalatest.concurrent.Futures
import org.scalatest.time.SpanSugar
import spray.io.SingletonHandler
import scala.util.Failure
import scala.util.Success
import spray.io.IOServer.Bound
import org.glassfish.grizzly.http.util.HttpStatus
import com.xebialabs.restito.semantics.Action
import org.glassfish.grizzly.http.server.Response
import scala.concurrent.duration.Duration
import com.codahale.metrics.MetricRegistry

trait ScalaFutures extends Futures {
  implicit class ScalaFutureConcept[T](fut: Future[T]) extends FutureConcept[T] {
    def eitherValue: Option[Either[Throwable, T]] = {
      fut.value.map( x => {
        x match {
          case Success(y) => Right(y)
          case Failure(y) => Left(y)
        }
      })
    }

    def isExpired: Boolean = fut.isCompleted

    def isCanceled: Boolean = false

    override def futureValue(implicit config: PatienceConfig): T = {
      import scala.concurrent.Await
      import scala.concurrent.duration.Duration

      Await.result(fut, Duration(config.timeout.millisPart, TimeUnit.MILLISECONDS))
    }
  }
}


class ProxySuite extends FunSpec with SpanSugar with ShouldMatchers with BeforeAndAfter with ScalaFutures with GivenWhenThen {


  trait ActorSystemFixture {
    val system = ActorSystem("test-system")
  }

  trait IOBridgeFixture extends ActorSystemFixture {
    val ioBridge = IOExtension(system).ioBridge()
  }

  trait EverythingFixture extends IOBridgeFixture {
    val httpClient = system.actorOf(Props(new HttpClient(ioBridge)), "client")
    val metricsRegistry = new MetricRegistry()
  }

  def withStubServer(doTest: (StubServer, Int) => Any) {
    val server = new StubServer().run()
    val port = server.getPort

    try {
      doTest(server, port)
    } finally {
      server.stop()
    }
  }

  def withBothServers(doTest: (StubServer, Int, StubServer, Int) => Any) {
    withStubServer { (server1, port1) =>
      withStubServer { (server2, port2) =>
        doTest(server1, port1, server2, port2)
      }
    }
  }

  def timeoutAction(waitTime: Timeout): Action = {

    customAction(new com.google.common.base.Function[Response, Response] {
      def apply(input: Response): Response = {
        Thread.sleep(waitTime.value.millisPart)
        input
      }
    })

  }

  def withProxyAndStubs(doTest: (StubServer, StubServer, Int, ActorRef, ActorSystem) => Any) {
    withBothServers { (trueServer, truePort, shadowServer, shadowPort) =>
      new EverythingFixture {
        val shadowConfig = ShadowConfig("localhost", truePort, "localhost", shadowPort)

        val proxy = system.actorOf(Props(new ProxyActor(httpClient, metricsRegistry, shadowConfig)))

        val proxyServer = system.actorOf(Props(new HttpServer(ioBridge, SingletonHandler(proxy))), "proxy")

        val testProbe = new TestProbe(system)

        val proxyPort = 1024 + scala.util.Random.nextInt(10000)

        testProbe.send(proxyServer, Bind(interface = "localhost", port=proxyPort))

        val bound = testProbe.expectMsgType[Bound]

        bound.endpoint.getPort should be (proxyPort)

        try {
          doTest(trueServer, shadowServer, proxyPort, httpClient, system)
        } finally {
          proxyServer ! PoisonPill
          proxy ! PoisonPill
        }
      }
    }
  }

  describe ("ProxyService") {
    it("should initialize and bind to port properly") {
      withProxyAndStubs { (trueServer, shadowServer, proxyPort, httpClient, actorSystem) =>
        // this tests the fixture initialization, if we can get it everything is good
        assert(condition = true)
      }
    }

    it ("should direct the same incoming request to both downstream servers") {
      withProxyAndStubs { (trueServer, shadowServer, proxyPort, httpClient, actorSystem) =>

        Given("trueServer and shadowServer both returns 200 OK")
        whenHttp(trueServer).`match`(get("/test")).then(success(), stringContent("OK")).mustHappen(1)
        whenHttp(shadowServer).`match`(startsWithUri("/test")).then(success(), stringContent("OK")).mustHappen(1)

        implicit val actorRefFactory: ActorRefFactory = actorSystem

        val testProbe = new TestProbe(actorSystem)

        actorSystem.eventStream.subscribe(testProbe.ref, classOf[ShadowEntry])

        When("we make a request to the proxy server")
        val respF = HttpDialog(httpClient, "localhost", proxyPort).send(Get("/test")).end

        Then("we should get a 200 OK from the proxy")
        respF.futureValue(timeout(2.seconds)).entity.asString should be ("OK")

        And("we should also get a ShadowEntry pubbed into the eventStream of the actorSystem")
        val entry = testProbe.expectMsgType[ShadowEntry]

        entry.request.path should be ("/test")

        val trueServerResp = entry.responses._1._1
        val shadowServerResp = entry.responses._2._1

        trueServerResp.entity.asString should be ("OK")
        shadowServerResp.entity.asString should be ("OK")
      }
    }

    it ("should return the response from the true server") {
      withProxyAndStubs { (trueServer, shadowServer, proxyPort, httpClient, actorSystem) =>

        Given("trueServer returns OK but shadowServer returns a 500")
        whenHttp(trueServer).`match`(get("/test")).then(success(), stringContent("OK")).mustHappen(1)
        // simulate a internal server error
        whenHttp(shadowServer).`match`(startsWithUri("/test"))
          .then(status(HttpStatus.INTERNAL_SERVER_ERROR_500), stringContent("NOT-OK")).mustHappen(1)

        implicit val actorRefFactory: ActorRefFactory = actorSystem

        val testProbe = new TestProbe(actorSystem)

        actorSystem.eventStream.subscribe(testProbe.ref, classOf[ShadowEntry])

        When("we make a request to the proxy server")
        val respF = HttpDialog(httpClient, "localhost", proxyPort).send(Get("/test")).end

        Then("we should get the true server's 200 OK response")
        respF.futureValue(timeout(2.seconds)).entity.asString should be ("OK")

        And("we should get a pub into eventStream with both the results from true and shadow servers")
        val entry = testProbe.expectMsgType[ShadowEntry]

        entry.request.path should be ("/test")

        val trueServerResp = entry.responses._1._1
        val shadowServerResp = entry.responses._2._1

        trueServerResp.entity.asString should be ("OK")
        trueServerResp.status.value should be (200)

        shadowServerResp.entity.asString should be ("NOT-OK")
        shadowServerResp.status.value should be (500)
      }
    }

    it ("should still return the true server response when the shadow server times out") {
      withProxyAndStubs { (trueServer, shadowServer, proxyPort, httpClient, actorSystem) =>

        Given("true server returns 200 OK but shadow server returns a 500 after 2 seconds")
        whenHttp(trueServer).`match`(get("/test")).then(success(), stringContent("OK")).mustHappen(1)
        // simulate a internal server error after a timeout
        whenHttp(shadowServer).`match`(startsWithUri("/test"))
          .then(
            status(HttpStatus.INTERNAL_SERVER_ERROR_500),
            stringContent("NOT-OK"),
            timeoutAction(timeout(2.seconds)))
          .mustHappen(1)

        implicit val actorRefFactory: ActorRefFactory = actorSystem

        val testProbe = new TestProbe(actorSystem)

        actorSystem.eventStream.subscribe(testProbe.ref, classOf[ShadowEntry])

        When("we hit the proxy server")
        val respF = HttpDialog(httpClient, "localhost", proxyPort).send(Get("/test")).end

        // the timeout value here ensures that we are getting the result from the true server
        // as soon as it is available and not after 5 seconds
        Then("proxy server should return as soon as the true server returns")
        respF.futureValue(timeout(1.seconds)).entity.asString should be ("OK")

        // this should still be filed after 5 seconds
        And("we should still get a ShadowEntry after the shadow server returns")
        val entry = testProbe.expectMsgType[ShadowEntry](max = Duration(3, TimeUnit.SECONDS))

        entry.request.path should be ("/test")

        val trueServerResp = entry.responses._1._1
        val shadowServerResp = entry.responses._2._1

        trueServerResp.entity.asString should be ("OK")
        trueServerResp.status.value should be (200)

        shadowServerResp.entity.asString should be ("NOT-OK")
        shadowServerResp.status.value should be (500)
      }
    }
  }
}

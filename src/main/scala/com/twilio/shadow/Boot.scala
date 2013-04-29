package com.twilio.shadow

import akka.actor.{ActorSystem, Props}
import spray.io.{SingletonHandler, IOExtension}
import spray.can.server.{ServerSettings, HttpServer}
import spray.io.IOServer.Bind
import spray.can.client.{HttpClient, ClientSettings}
import com.typesafe.config.ConfigFactory
import com.codahale.metrics.MetricRegistry

object Boot extends App{

  val system = ActorSystem("shadow-system")
  val ioBridge = IOExtension(system).ioBridge()

  val config = ConfigFactory.load()
  val metricsRegistry = new MetricRegistry()

  val httpClient = system.actorOf(Props(new HttpClient(ioBridge, ClientSettings(config))), "client")

  val ui = system.actorOf(Props(new UIActor(metricsRegistry)))

  val shadowConfig = ShadowConfig(
      config.getString("shadow.trueServer.host"), config.getInt("shadow.trueServer.port"),
      config.getString("shadow.shadowServer.host"), config.getInt("shadow.shadowServer.port"))

  val proxy = system.actorOf(Props(new ProxyActor(httpClient, metricsRegistry, shadowConfig)))

  val proxyServer = system.actorOf(Props(new HttpServer(ioBridge, SingletonHandler(proxy), ServerSettings(config))), "proxy")
  val uiServer = system.actorOf(Props(new HttpServer(ioBridge, SingletonHandler(ui), ServerSettings(config))), "ui")

  val proxyHost = config.getString("shadow.proxy-host")
  val proxyPort = config.getInt("shadow.proxy-port")

  val uiHost = config.getString("shadow.ui-host")
  val uiPort = config.getInt("shadow.ui-port")


  proxyServer ! Bind(interface = proxyHost, port = proxyPort)
  uiServer ! Bind(interface = uiHost, port = uiPort)

}
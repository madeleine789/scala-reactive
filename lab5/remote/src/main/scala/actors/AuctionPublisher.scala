package actors.remote


import akka.actor.{ActorLogging, Actor}

import com.typesafe.config.ConfigFactory
import scala.concurrent.duration._
import akka.actor._

import scala.concurrent.Await

class AuctionPublisher extends Actor with ActorLogging{

  override def receive: Receive = {
    case "INIT"=> {
      log.info(s"Publisher starting...")
    }
    case "OK" =>
    case msg: String => {
      log.info(msg)
      sender ! "OK"
    }
    case _ => log.info("Received unknown msg")
  }
}

object AuctionPublisher {
  def main (args: Array[String]) {
    val config = ConfigFactory.load()
    val publisher = ActorSystem("AuctionPublisher", config.getConfig("publisher").withFallback(config))
    val remoteActor = publisher.actorOf(Props[AuctionPublisher], name = "publisher")

    remoteActor ! "PUBLISHER IS ALIVE"
    Await.result(publisher.whenTerminated, Duration.Inf)
  }
}
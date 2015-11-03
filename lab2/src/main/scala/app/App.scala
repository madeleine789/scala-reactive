package app

import akka.actor.{ActorSystem, Props}
import actors.{Buyer, AuctionManager}

import scala.concurrent.duration._

object App extends App {

  val system = ActorSystem("AuctionSystem")
  import system.dispatcher

  val manager = system.actorOf(Props[AuctionManager], "AuctionManager")

  val buyers = List[String]("CaptainAmerica", "BlackWidow", "Hulk", "Thor", "IronMan", "SpiderMan", "Daredevil")

  buyers.map {name => system.actorOf(Props(classOf[Buyer], manager), name)}

  system.scheduler.scheduleOnce(1 minutes)(system.shutdown())

}

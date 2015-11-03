package app

import actors.Watcher.WatchHim
import actors.{Watcher, Buyer, Seller}
import akka.actor.{ActorSystem, Props}
import search.AuctionSearch


import scala.concurrent.duration._

object App {
  class BSWatcher extends Watcher {
    override def shutdownHook(): Unit = {
      log.info("No actors left. Shutting down...")
      context.system.shutdown()
    }
  }


  def main(args: Array[String]){

    val system = ActorSystem("AuctionSystem")
    import system.dispatcher

    val watcher = system.actorOf(Props[BSWatcher], "BSWatcher")

    val sellers =Map("CaptainAmerica" -> List("Infinity Gauntlet", "Ultimate Nullifier", "Wand of Watoomb", "Orb of Agamotto"),
                      "IronMan" -> List("Wand of Ornate", "Infinity Gem", "Evil Eye of Avalon", "Heart of Universe", "Eye of Agamotto"))
    val buyers = List(("BlackWidow","Wand",19000),("Thor","Wand",8000),("Hulk", "Wand", 4500))

    sellers.foreach { key =>
      val seller = system.actorOf(Props(classOf[Seller], key._2), s"Seller_${key._1}")
      watcher ! WatchHim(seller)
    }

    system.actorOf(Props[AuctionSearch], "AuctionSearch")

    system.scheduler.scheduleOnce(1 seconds) {
      buyers.foreach { b =>
        val buyer = system.actorOf(Props(classOf[Buyer], b._2, b._3), b._1)
        watcher ! WatchHim(buyer)
      }
    }

    system.scheduler.scheduleOnce(1 minutes)(system.shutdown())
  }
}
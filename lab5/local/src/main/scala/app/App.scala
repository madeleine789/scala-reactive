package app

import actors.notifier.Notifier
import actors.{Buyer, Seller}
import akka.actor.{ActorSystem, Props}
import com.typesafe.config.ConfigFactory
import search.AuctionSearch

import scala.concurrent.duration._

object App {



  def main(args: Array[String]){

    val config = ConfigFactory.load()
    val system = ActorSystem("AuctionSystem", config.getConfig("auction").withFallback(config))
    import system.dispatcher



    val sellers =Map("CaptainAmerica" -> List("Infinity Gauntlet", "Ultimate Nullifier", "Wand of Watoomb", "Orb of Agamotto"),
                      "IronMan" -> List("Wand of Ornate", "Infinity Gem", "Evil Eye of Avalon", "Heart of Universe", "Eye of Agamotto"))
    val buyers = List(("BlackWidow","Wand",19000),("Thor","Wand",18000),("Hulk", "Wand", 14500))

    sellers.foreach { key =>
      val seller = system.actorOf(Props(classOf[Seller], key._2), s"Seller_${key._1}")
    }
    system.actorOf(Props[AuctionSearch], "AuctionSearch")
    val notifier = system.actorOf(Props[Notifier], "Notifier")
    notifier ! "INIT"


    system.scheduler.scheduleOnce(1 seconds) {
      buyers.foreach { b =>
        val buyer = system.actorOf(Props(classOf[Buyer], b._2, b._3), b._1)

      }
    }
    system.scheduler.scheduleOnce(1 minutes)(system.shutdown())
  }
}
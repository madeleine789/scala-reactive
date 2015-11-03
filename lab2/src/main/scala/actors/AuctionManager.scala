package actors

import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import actors.AuctionManager.Auctions
import actors.AuctionMessages.EndOfAuction

import scala.concurrent.duration._

object AuctionManager {
  case object Auctions
}


class AuctionManager extends Actor with ActorLogging {

  val items = List[String]("Infinity_Gauntlet", "Ultimate_Nullifier", "Wand_of_Watoomb", "Evil_Eye_of_Avalon", "Heart_of_Universe")

  var auctions: List[ActorRef] = items.map(item => {
    context.actorOf(Props(classOf[Auction], item, 20 seconds), s"${item}_Auction")
    //context.actorOf(Props(classOf[FSMAuction], item, 20 seconds), s"${item}_Auction")
  })

  override def receive: Receive = {
    case Auctions =>
      sender ! auctions

    case EndOfAuction(success) =>
      success match {
        case true =>
        case false => log.info(s"${sender.path.name} ended with failure. Item wasn't sold.")
      }
      auctions = auctions.diff(sender :: Nil)
      if (auctions.length == 0) {
        log.info("Auction ended. Shutting down.")
        context.system.shutdown()
      }
  }
}

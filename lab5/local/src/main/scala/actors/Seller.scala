package actors

import actors.auction.{Messages, Auction}
import Messages.EndOfAuction
import actors.auction.Auction
import akka.actor.{Props, ActorLogging, Actor}
import search.AuctionSearch.{Unregister, Register}


import scala.concurrent.duration._



class Seller(items : List[String]) extends Actor with ActorLogging {
  import context.dispatcher

  val auctionSearch = context.actorSelection("/user/AuctionSearch").resolveOne(10 seconds)
  var auctions = items.map(item => {
    val auction = context.actorOf(Props(classOf[Auction], item, 20 seconds, 10 seconds), s"${item.replaceAll(" ", "_")}")
    auctionSearch.map(_.tell(Register(item), auction)) // register every Auction with AuctionSearch
    auction -> item
  }).toMap


  override def receive: Receive = {
    case EndOfAuction(success) =>
      success match {
        case true =>
        case false => log.info(s"Auction ended. ${sender().path.name.replaceAll("_", " ")} wasn't sold.")
      }
      auctions -= sender()
      auctionSearch.map(_.tell(Unregister(auctions(sender())), sender()))
      if (auctions.isEmpty) {
        log.info("All auctions ended. Shutting down.")
        context.system.shutdown()
      }
  }
}

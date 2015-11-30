package actors

import actors.auction.{Messages, Auction}
import Messages.EndOfAuction
import actors.auction.Auction
import akka.actor.{Props, ActorLogging, Actor}
import actors.search.AuctionSearch.{Unregister, Register}
import actors.search.MasterSearch


import scala.concurrent.duration._

class Seller(items : List[String], persist : Boolean) extends Actor with ActorLogging {
  import context.dispatcher

  //val auctionSearch = context.actorSelection("akka.tcp://AuctionSystem@127.0.0.1:2551/user/AuctionSearch").resolveOne(10 seconds)
  val masterSearch = context.actorSelection("akka.tcp://AuctionSystem@127.0.0.1:2551/user/MasterSearch").resolveOne(10 seconds)
  var auctions = items.map(item => {
    val auction = context.actorOf(Props(classOf[Auction], item, 20 seconds, 10 seconds, persist), s"${item.replaceAll(" ", "_")}")
    masterSearch.map(_.tell(Register(item), auction)) // register every Auction with AuctionSearch
    auction -> item
  }).toMap


  override def receive: Receive = {
    case EndOfAuction(success) =>
      success match {
        case true =>
        case false => log.info(s"Auction ended. ${sender().path.name.replaceAll("_", " ")} wasn't sold.")
      }
      auctions -= sender()
      masterSearch.map(_.tell(Unregister(auctions(sender())), sender()))
      //auctionSearch.map(_.tell(Unregister(auctions(sender())), sender()))
      if (auctions.isEmpty) {
        log.info("All auctions ended. Shutting down.")
        context.system.shutdown()
      }
  }
}

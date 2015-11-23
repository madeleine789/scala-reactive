package actors

import actors.auction.Messages
import Messages._
import akka.actor.{ActorRef, ActorLogging, Actor}
import akka.event.LoggingReceive
import search.AuctionSearch.{NoResults, SearchResults, Search}

import scala.util.Random
import scala.concurrent.duration._
/**
 * Created by mms on 2015-10-27.
 */
class Buyer(target: String, maxPrice: Int) extends Actor with ActorLogging {
  import context._

  val auctionSearch = context actorSelection "akka.tcp://AuctionSystem@127.0.0.1:2551/user/AuctionSearch"

  auctionSearch ! Search(target)

  var alreadySold = 0
  var items: List[ActorRef] = _

  def stopIfAuctionEnded() = if (items.length == alreadySold) {
    log.info(s"No items left. ${self.path.name} stops.")
    context stop self
  }

  override def receive = init
  def init = LoggingReceive {
    case NoResults =>
      log.info("No results found. Stopping.")
      context stop self
    case SearchResults(auctions) =>
      items = auctions
      items.foreach(_ ! RegisterForNotifications)
      items.foreach(_ ! Offer(Random.nextInt(100)))
      stopIfAuctionEnded()
      context become bidding

    case _ =>
  }

  def bidding = LoggingReceive {

    case Outbid(price) =>
      makeOffer(price, sender())

    case InsufficientOffer(price) =>
      makeOffer(price, sender())

    case ItemIsNotAvailable(item) =>
      stopIfAuctionEnded()

    case YouWon(item, price) =>
      log.info(s"${self.path.name} bought $item for $price.")
      alreadySold += 1
      stopIfAuctionEnded()

    case _ =>
  }

  def makeOffer(currentPrice: Int, auction: ActorRef) {
    maxPrice - currentPrice match {
      case farFromMax if farFromMax > 50 =>
        sendOffer(auction, currentPrice + Random.nextInt(100))
      case notFarFromMax if notFarFromMax > 0 =>
        sendOffer(auction, maxPrice)
      case moreThanMax =>
        alreadySold += 1
        auction ! UnregisterForNotifications
        log.info(s"Price is too high - not buying ${auction.path.name}.")
        auction.path
        stopIfAuctionEnded()
    }
  }

  def sendOffer(auction: ActorRef, price: Int) {
    context.system.scheduler.scheduleOnce(Random.nextInt(200) millis ) {
      auction.!(Offer(price))(self)
    }
  }
}

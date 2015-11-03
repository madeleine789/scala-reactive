package actors

import actors.AuctionMessages.Offer
import akka.actor.{Actor, ActorLogging, ActorRef}
import akka.event.LoggingReceive
import actors.AuctionManager.Auctions
import actors.AuctionMessages._

import scala.concurrent.duration._
import scala.util.Random

class Buyer(AuctionManager: ActorRef) extends Actor with ActorLogging {

  import context._

  var alreadySold = 0
  var items: List[ActorRef] = _

  def stopIfAuctionEnded() = if (items.length == alreadySold) {
    log.info(s"No items left. ${self.path.name} stops.")
    context stop self
  }

  override def receive = init

  AuctionManager ! Auctions

  def init = LoggingReceive {
    case auctions: List[ActorRef] =>
      items = new Random(Random.nextLong()).shuffle(auctions).take(2).map {
        auction =>
          auction ! Offer(Random.nextInt(100))
          auction
      }
      stopIfAuctionEnded()
      context become bidding

    case _ =>
  }


  def bidding = LoggingReceive {
    case Outbid(price) =>
      val send = sender()
      context.system.scheduler.scheduleOnce(Random.nextInt(400) millis) {
        (send ! Offer(price + Random.nextInt(100)))(self)
      }

    case InsufficientOffer(price) =>
      val send = sender()
      context.system.scheduler.scheduleOnce(Random.nextInt(400) millis) {
        (send ! Offer(price + Random.nextInt(100)))(self)
      }

    case ItemIsNotAvailable(item) =>
      stopIfAuctionEnded()

    case YouWon(item, price) =>
      log.info(s"${self.path.name} bought $item for $price.")
      alreadySold += 1
      stopIfAuctionEnded()

    case _ =>
  }
}

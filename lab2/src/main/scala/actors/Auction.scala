package actors

import actors.AuctionMessages.Offer
import akka.actor.{Actor, ActorLogging, ActorRef, PoisonPill}
import akka.event.LoggingReceive
import actors.AuctionMessages._

import scala.concurrent.duration._


class Auction(itemName: String, bidTime: FiniteDuration) extends Actor with ActorLogging {

  import context._
  context.system.scheduler.scheduleOnce(bidTime, self, BidTimerExpired)

  override def receive = created
  var currentPrice: Int = 0
  var winner: Option[ActorRef] = None

  def active = LoggingReceive {
    case Offer(price) if price > currentPrice =>
      log.info(s"${sender().path.name} outbids with $price.")
      currentPrice = price
      winner.foreach(_ ! Outbid(currentPrice))
      winner = Some(sender())

    case Offer(_) =>
      sender ! InsufficientOffer(currentPrice)

    case BidTimerExpired =>
      winner.foreach(_ ! YouWon(itemName, currentPrice))
      parent ! EndOfAuction(success = true)
      context.system.scheduler.scheduleOnce(10 seconds, self, DeleteTimerExpired)
      context become sold
  }

  def created = LoggingReceive {
    case BidTimerExpired =>
      context.system.scheduler.scheduleOnce(10 seconds, self, DeleteTimerExpired)
      context become ignored

    case offer: Offer =>
      log.info(s"${sender().path.name} sets starting price at ${offer.price}.")
      context become active
      self forward offer
  }

  def ignored: Receive = LoggingReceive {
    case DeleteTimerExpired =>
      parent ! EndOfAuction(success = false)
      self ! PoisonPill
  }

  def sold = LoggingReceive {
    case DeleteTimerExpired =>
      self ! PoisonPill
  }
}

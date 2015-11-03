package actors

import actors.AuctionMessages._
import actors.FSMAuction._
import akka.actor._

import scala.concurrent.duration._

object FSMAuction {
  sealed trait State
  case object Created extends State
  case object Activated extends State
  case object Ignored extends State
  case object Sold extends State

  sealed trait Data
  case object FSMAuctionNotStarted extends Data
  case class FSMAuctionState(currentWinner: ActorRef, currentPrice: Int) extends Data
}


class FSMAuction(item: String, bidTime: FiniteDuration) extends Actor with ActorLogging with FSM[State, Data] {

  import context.dispatcher
  context.system.scheduler.scheduleOnce(bidTime, self, BidTimerExpired)

  when(Created){
    case Event(offer: Offer, FSMAuctionNotStarted) =>
      log.info(s"${sender().path.name} sets starting price at ${offer.price}.")
      goto(Activated) using FSMAuctionState(sender(), offer.price)
    
    case Event(BidTimerExpired, _) =>
      context.system.scheduler.scheduleOnce(10 seconds, self, DeleteTimerExpired)
      goto(Ignored) using FSMAuctionNotStarted

  }

  when(Activated) {
    case Event(Offer(price), FSMAuctionState(currentWinner, currentPrice)) if price > currentPrice =>
      log.info(s"${sender().path.name} outbids with $price.")
      currentWinner ! Outbid(currentPrice)
      stay using FSMAuctionState(sender(), price)
      
    case Event(Offer(_), state: FSMAuctionState) =>
      sender ! InsufficientOffer(state.currentPrice)
      stay()
      
    case Event(BidTimerExpired, state: FSMAuctionState) =>
      state.currentWinner ! YouWon(item, state.currentPrice)
      context.system.scheduler.scheduleOnce(10 seconds, self, DeleteTimerExpired)
      goto(Sold) using state
  }

  when(Sold) {
    case Event(Offer(_), _) =>
      sender ! ItemIsNotAvailable(item)
      stay()
      
    case Event(DeleteTimerExpired, state: FSMAuctionState) =>
      context.parent ! EndOfAuction(success = true)
      stop()
  }

  when(Ignored) {
    case Event(DeleteTimerExpired, _) =>
      context.parent ! EndOfAuction(success = false)
      stop()

    case Event(_, _) =>
      context.system.scheduler.scheduleOnce(20 seconds, self, BidTimerExpired)
      goto(Created) using FSMAuctionNotStarted

  }

  startWith(Created, FSMAuctionNotStarted)
  initialize()
}

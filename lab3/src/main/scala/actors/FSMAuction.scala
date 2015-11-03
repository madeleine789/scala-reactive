package actors


import akka.actor._
import actors.Messages._
import actors.FSMAuction._

import scala.collection.mutable
import scala.concurrent.duration._

object FSMAuction {
  sealed trait State
  case object Created extends State
  case object Activated extends State
  case object Ignored extends State
  case object Sold extends State
  case object Deleted extends State
  case object Waiting extends State

  sealed trait Data
  case object Uninitialized extends Data
  case class Registration(toNotify: mutable.Set[ActorRef]) extends Data
  case class AuctionState(currentWinner: ActorRef, currentPrice: Int, toNotify: mutable.Set[ActorRef]) extends Data
}


class FSMAuction(item: String, bidTime: FiniteDuration) extends Actor with ActorLogging with FSM[State, Data] {

  import context.dispatcher
  context.system.scheduler.scheduleOnce(bidTime, self, BidTimerExpired)

  when(Created){
    case Event(offer: Offer, Uninitialized) =>
      log.info(s"${sender().path.name} sets starting price at ${offer.price}.")
      goto(Activated) using AuctionState(sender(), offer.price, mutable.Set(sender()))

    case Event(RegisterForNotifications, Uninitialized) =>
      val toNotify = mutable.Set(sender())
      log.info(s"${sender().path.name} will be notified about $item auction.")
      goto(Waiting) using Registration(toNotify)

    case Event(BidTimerExpired, _) =>
      context.system.scheduler.scheduleOnce(10 seconds, self, DeleteTimerExpired)
      goto(Ignored) using Uninitialized

  }

  when (Waiting){
    case Event(RegisterForNotifications, state : Registration) =>
      state.toNotify.add(sender())
      log.info(s"${sender().path.name} will be notified about $item auction.")
      stay using Registration(state.toNotify)

    case Event(offer: Offer,state : Registration) =>
      log.info(s"${sender().path.name} sets starting price at ${offer.price}.")
      goto(Activated) using AuctionState(sender(), offer.price, state.toNotify)

    case Event(BidTimerExpired, state : Registration) =>
      context.system.scheduler.scheduleOnce(10 seconds, self, DeleteTimerExpired)
      goto(Ignored) using Registration(state.toNotify)
  }

  when(Activated) {
    case Event(RegisterForNotifications, AuctionState(currentWinner, currentPrice, toNotify)) =>
      toNotify.add(sender())
      log.info(s"${sender().path.name} will be notified about $item auction.")
      stay using AuctionState(currentWinner, currentPrice, toNotify)
    case Event(Offer(price), AuctionState(currentWinner, currentPrice, toNotify)) if price > currentPrice =>
      log.info(s"${sender().path.name} outbids with $price.")
      if(toNotify.contains(currentWinner)){currentWinner ! Outbid(currentPrice)}
      stay using AuctionState(sender(), price, toNotify)

    case Event(Offer(_), state: AuctionState) =>
      sender ! InsufficientOffer(state.currentPrice)
      stay()

    case Event(UnregisterForNotifications, state: AuctionState) =>
      state.toNotify.remove(sender())
      log.info(s"${sender().path.name} will not be notified about this auction anymore.")
      stay()

    case Event(BidTimerExpired, state: AuctionState) =>
      state.currentWinner ! YouWon(item, state.currentPrice)
      context.system.scheduler.scheduleOnce(10 seconds, self, DeleteTimerExpired)
      goto(Sold) using state
  }

  when(Sold) {
    case Event(Offer(_), _) =>
      sender ! ItemIsNotAvailable(item)
      stay()

    case Event(DeleteTimerExpired, state: AuctionState) =>
      context.parent ! EndOfAuction(success = true)       // inform seller auction ended
      stop()
  }

  when(Ignored) {
    case Event(DeleteTimerExpired, _) =>
      context.parent ! EndOfAuction(success = false)      // inform seller auction ended
      stop()

    case Event(_, state: Registration) =>
      context.system.scheduler.scheduleOnce(20 seconds, self, BidTimerExpired)
      goto(Created) using Uninitialized

  }

  startWith(Created, Uninitialized)
  initialize()
}

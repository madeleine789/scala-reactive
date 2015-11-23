package actors.auction

import actors.auction.Messages._
import actors.auction.FSMAuction._
import akka.actor._

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

  sealed trait FSMData
  case object FSMUninitialized extends FSMData
  case class Registration(toNotify: mutable.Set[ActorRef]) extends FSMData
  case class FSMAuctionState(currentWinner: ActorRef, currentPrice: Int, toNotify: mutable.Set[ActorRef]) extends FSMData
}


class FSMAuction(item: String, bidTime: FiniteDuration) extends Actor with ActorLogging with FSM[State, FSMData] {

  import context.dispatcher
  context.system.scheduler.scheduleOnce(bidTime, self, BidTimerExpired)

  when(FSMAuction.Created){
    case Event(offer: Offer, FSMUninitialized) =>
      log.info(s"${sender().path.name} sets starting price at ${offer.price}.")
      goto(Activated) using FSMAuctionState(sender(), offer.price, mutable.Set(sender()))

    case Event(RegisterForNotifications, FSMUninitialized) =>
      val toNotify = mutable.Set(sender())
      log.info(s"${sender().path.name} will be notified about $item auction.")
      goto(FSMAuction.Waiting) using Registration(toNotify)

    case Event(BidTimerExpired, _) =>
      context.system.scheduler.scheduleOnce(10 seconds, self, DeleteTimerExpired)
      goto(FSMAuction.Ignored) using FSMUninitialized

  }

  when (FSMAuction.Waiting){
    case Event(RegisterForNotifications, state : Registration) =>
      state.toNotify.add(sender())
      log.info(s"${sender().path.name} will be notified about $item auction.")
      stay using Registration(state.toNotify)

    case Event(offer: Offer,state : Registration) =>
      log.info(s"${sender().path.name} sets starting price at ${offer.price}.")
      goto(Activated) using FSMAuctionState(sender(), offer.price, state.toNotify)

    case Event(BidTimerExpired, state : Registration) =>
      context.system.scheduler.scheduleOnce(10 seconds, self, DeleteTimerExpired)
      goto(FSMAuction.Ignored) using Registration(state.toNotify)
  }

  when(Activated) {
    case Event(RegisterForNotifications, FSMAuctionState(currentWinner, currentPrice, toNotify)) =>
      toNotify.add(sender())
      log.info(s"${sender().path.name} will be notified about $item auction.")
      stay using FSMAuctionState(currentWinner, currentPrice, toNotify)
    case Event(Offer(price), FSMAuctionState(currentWinner, currentPrice, toNotify)) if price > currentPrice =>
      log.info(s"${sender().path.name} outbids with $price.")
      if(toNotify.contains(currentWinner)){currentWinner ! Outbid(currentPrice)}
      stay using FSMAuctionState(sender(), price, toNotify)

    case Event(Offer(_), state: FSMAuctionState) =>
      sender ! InsufficientOffer(state.currentPrice)
      stay()

    case Event(UnregisterForNotifications, state: FSMAuctionState) =>
      state.toNotify.remove(sender())
      log.info(s"${sender().path.name} will not be notified about this auction anymore.")
      stay()

    case Event(BidTimerExpired, state: FSMAuctionState) =>
      state.currentWinner ! YouWon(item, state.currentPrice)
      context.system.scheduler.scheduleOnce(10 seconds, self, DeleteTimerExpired)
      goto(FSMAuction.Sold) using state
  }

  when(FSMAuction.Sold) {
    case Event(Offer(_), _) =>
      sender ! ItemIsNotAvailable(item)
      stay()

    case Event(DeleteTimerExpired, state: FSMAuctionState) =>
      context.parent ! EndOfAuction(success = true)       // inform seller auction ended
      stop()
  }

  when(FSMAuction.Ignored) {
    case Event(DeleteTimerExpired, _) =>
      context.parent ! EndOfAuction(success = false)      // inform seller auction ended
      stop()

    case Event(_, state: Registration) =>
      context.system.scheduler.scheduleOnce(20 seconds, self, BidTimerExpired)
      goto(FSMAuction.Created) using FSMUninitialized

  }

  startWith(FSMAuction.Created, FSMUninitialized)
  initialize()
}


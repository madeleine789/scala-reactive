package actors.auction

import java.util.concurrent.TimeUnit

import Messages._
import actors.notifier.Notifications.{Closed, FinishedAuction, NotStarted, OngoingAuction}
import akka.actor._
import akka.event.LoggingReceive
import akka.persistence._

import scala.collection.mutable
import scala.concurrent.duration._

sealed trait AuctionState
case object Created extends AuctionState
case object Waiting extends AuctionState
case object Active extends AuctionState
case object Ignored extends AuctionState
case object Sold extends AuctionState


sealed trait Data
case class Uninitialized(bidTime:Int,deleteTime:Int) extends Data
case class AuctionData(winner: ActorRef, price: Int, toNotify: mutable.Set[ActorRef]) extends Data

case class AuctionStateChangeEvent(state: AuctionState, data: Data, duration: Long)


class Auction(item: String, bidTime: FiniteDuration, deleteTime: FiniteDuration) extends PersistentActor with ActorLogging {

  import context._

  context.system.scheduler.scheduleOnce(bidTime, self, BidTimerExpired)
  val notifier = context actorSelection "akka.tcp://AuctionSystem@192.168.0.100:2551/user/Notifier"
  override def persistenceId: String = "persistent-auction-" + item.toLowerCase.replaceAll(" ","-")

  override def receive = created
  var currentPrice: Int = 0
  var winner: ActorRef = null
  var toNotify: mutable.Set[ActorRef] = mutable.Set.empty

  var lastTick: Long = System.currentTimeMillis()
  var duration: Long = 0

  var data: Data = Uninitialized(60, 10)

  var bidTimeStarted: Long = 0
  var deleteTimeStarted: Long = 0

  def active = LoggingReceive {
    case Offer(price) if price > currentPrice =>
      log.info(s"${sender().path.name} outbids with $price.")
      currentPrice = price
      if(winner != null &&  toNotify.contains(winner)){
        winner ! Outbid(currentPrice)
        notifier ! OngoingAuction(item,winner,currentPrice)
      }
      winner = sender()
      persist(AuctionStateChangeEvent(Active, AuctionData(sender(),price,toNotify), duration)){
        event =>
          updateState(event)
          updateTimer()
      }


    case Offer(_) =>
      sender ! InsufficientOffer(currentPrice)

    case BidTimerExpired =>
      winner ! YouWon(item, currentPrice)
      parent ! EndOfAuction(success = true)
      notifier ! FinishedAuction(item,winner,currentPrice)
      persist(AuctionStateChangeEvent(Sold, data, duration)){
        event =>
          context.system.scheduler.scheduleOnce(10 seconds, self, DeleteTimerExpired)
          updateState(event)
          updateTimer()
      }

    case RegisterForNotifications =>
      toNotify.add(sender())
      persist(AuctionStateChangeEvent(Active, AuctionData(winner,currentPrice,toNotify), duration)){
        event =>
          log.info(s"${sender().path.name} will be notified about $item auction.")
          updateState(event)
          updateTimer()
      }
    case UnregisterForNotifications =>
      toNotify.remove(sender())
      persist(AuctionStateChangeEvent(Active, AuctionData(winner,currentPrice,toNotify), duration)){
        event =>
          log.info(s"${sender().path.name} will not be notified about this auction anymore.")
          updateState(event)
          updateTimer()
      }
  }

  def created = LoggingReceive {
    case BidTimerExpired =>
      notifier ! NotStarted(item)
      persist(AuctionStateChangeEvent(Ignored,data,duration)){
        event =>
          updateState(event)
          updateTimer()
      }

    case offer: Offer =>
      notifier ! OngoingAuction(item,sender(),offer.price)
      persist(AuctionStateChangeEvent(Active, AuctionData(sender(),offer.price,toNotify), duration)){
        event =>
          log.info(s"${sender().path.name} sets starting price at ${offer.price}.")
          updateState(event)
          updateTimer()
      }

    case RegisterForNotifications =>
      toNotify.add(sender())
      persist(AuctionStateChangeEvent(Waiting, AuctionData(null,currentPrice, toNotify),duration)){
        event =>
          log.info(s"${sender().path.name} will be notified about $item auction.")
          updateState(event)
          updateTimer()
      }
  }

  def ignored: Receive = LoggingReceive {
    case DeleteTimerExpired =>
        parent ! EndOfAuction(success = false)
        notifier ! Closed(item)
        context.stop(self)
  }

  def sold = LoggingReceive {
    case DeleteTimerExpired =>
      //self ! PoisonPill
      context.stop(self)
    case BidTimerExpired =>
      log.info(s"Auction ended. Item was bought by $winner for $currentPrice.")
      context.stop(self)
  }

  def waiting = LoggingReceive {
    case BidTimerExpired =>
      persist(AuctionStateChangeEvent(Ignored,data,duration)){
        event =>
          updateState(event)
          updateTimer()
          context.system.scheduler.scheduleOnce(10 seconds, self, DeleteTimerExpired)
      }

    case offer: Offer =>
      notifier ! OngoingAuction(item,sender(),offer.price)
      persist(AuctionStateChangeEvent(Active, AuctionData(sender(),offer.price,toNotify), duration)){
        event =>
          log.info(s"${sender().path.name} sets starting price at ${offer.price}.")
          updateState(event)
          updateTimer()
      }

    case RegisterForNotifications =>
      toNotify.add(sender())
      persist(AuctionStateChangeEvent(Waiting, AuctionData(null,currentPrice, toNotify),duration)){
        event =>
          log.info(s"${sender().path.name} will be notified about $item auction.")
          updateState(event)
          updateTimer()
      }
  }

  override def receiveRecover: Receive = {
    case event: AuctionStateChangeEvent => {
      updateState(event)
    }
    case RecoveryCompleted => {
      lastTick = System.currentTimeMillis()
      val newBidTimer = FiniteDuration(bidTime.toMillis - duration, TimeUnit.MILLISECONDS)
      context.system.scheduler.scheduleOnce(newBidTimer, context.self, BidTimerExpired)
      data match {
        case AuctionData(bidder,price,notify)=>
            winner = bidder
            currentPrice = price
            toNotify = notify
            log.info(s"Recovery completed with $bidder and $price")

        case _ =>
          log.info("Recovery completed" )
      }
    }
  }

  override def receiveCommand: Receive = created

  def updateState(event: AuctionStateChangeEvent): Unit = {
    //println(event)
    data = event.data
    duration = event.duration
    context.become(
      event.state match {
        case Created => created
        case Ignored =>
          context.system.scheduler.scheduleOnce(deleteTime,self, DeleteTimerExpired)
          ignored
        case Active => active
        case Sold =>
          context.system.scheduler.scheduleOnce(deleteTime,self, DeleteTimerExpired)
          sold
        case Waiting => waiting
      })
  }

  def updateTimer(): Unit = {
    duration += System.currentTimeMillis() - lastTick
    lastTick = System.currentTimeMillis()
  }


}

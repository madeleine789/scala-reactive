package actors.notifier

import java.net.{BindException, ConnectException}
import java.nio.channels.ClosedChannelException

import actors.notifier.Notifications.Notification
import actors.notifier.NotifierRequest.NotificationException

import akka.actor.SupervisorStrategy.{Resume, Stop, Restart}
import akka.actor._
import akka.pattern._
import akka.util.Timeout

import scala.concurrent.duration._
object Notifications {
  sealed trait Notification
  case class OngoingAuction(item: String, winner: ActorRef, price: Int) extends Notification
  case class FinishedAuction(item: String, winner: ActorRef, price: Int) extends Notification
  case class NotStarted(item: String) extends Notification
  case class Closed(item: String) extends Notification
  case object Init extends Notification
  case object OK extends Notification

}

class Notifier extends Actor with ActorLogging  {
  val publisher = context.actorSelection("akka.tcp://AuctionPublisher@127.0.0.1:2552/user/publisher")
  publisher ! "INIT"
  var pendingWorkers = Map[ActorRef, ActorRef]()

  override val supervisorStrategy = OneForOneStrategy(10, 2.minutes, loggingEnabled = false) {
    case _: NotificationException =>
      log.warning("Received unsupported type of notification")
      Resume
    case _: AskTimeoutException  =>
      log.warning("Sending notification failed, restarting.")
      Resume
    case e: ClosedChannelException =>
      log.error("Unexpected failure: {}", e.getMessage)
      notifyConsumerFailure(worker = sender(), failure = e)
      Restart
    case e: ConnectException =>
        log.error("Unexpected failure: {}", e.getMessage)
      notifyConsumerFailure(worker = sender(), failure = e)
      Restart
    case e: BindException =>
      log.error("Unexpected failure: {}", e.getMessage)
      notifyConsumerFailure(worker = sender(), failure = e)
      Stop
    case e =>
      log.error("Unexpected failure: {}", e.getMessage)
      notifyConsumerFailure(worker = sender(), failure = e)
      Stop
  }

  def notifyConsumerFailure(worker: ActorRef, failure: Throwable): Unit = {
    // Status.Failure is a message type provided by the Akka library. The
    // reason why it is used because it is recognized by the "ask" pattern
    // and the Future returned by ask will fail with the provided exception.
    pendingWorkers.get(worker) foreach { _ ! Status.Failure(failure) }
    pendingWorkers -= worker
  }

  def notifyConsumerSuccess(worker: ActorRef, msg: String): Unit = {
    pendingWorkers.get(worker) foreach { _ ! Status.Success }
    pendingWorkers -= worker
    publisher ! msg
  }

  override def receive: Receive = {
    case msg : Notification => {
      val worker = context.actorOf(Props(classOf[NotifierRequest], msg))
      pendingWorkers += worker -> sender()
      implicit val timeout = Timeout(4.seconds)
      worker ? msg
    }
    case "INIT" => {
      publisher ! "OK"
    }
    case msg : String => notifyConsumerSuccess(sender(), msg)
  }
}

package actors.notifier

import actors.notifier.Notifications._
import actors.notifier.NotifierRequest.NotificationException
import akka.actor.SupervisorStrategy.{Stop, Resume, Restart, Escalate}
import akka.actor._

import scala.concurrent.duration._

object NotifierRequest {
  class NotificationException extends Exception("Unsupported type of notification")
}

class NotifierRequest(n : Notification) extends Actor with ActorLogging{

  override val supervisorStrategy = OneForOneStrategy(10, 2.minutes,loggingEnabled = false) {
    case e: NotificationException =>
      Stop
    case _ =>
      Restart
  }

  override def preStart(): Unit = n match {
    case msg: Notification =>
      context.parent ! createMsg(msg)
      context.stop(self)
    case _ =>
      context.stop(self)
  }

  override def receive = {
    case msg: Notification => context.parent ! createMsg(msg)
  }

  def createMsg(n : Notification) : String ={
    n match {
      case msg: OngoingAuction => s"Auction ${msg.item}: current winner ${msg.winner.path.name} [${msg.price}]"

      case msg: FinishedAuction => s"Auction ${msg.item}: won by ${msg.winner.path.name} for ${msg.price}."

      case msg : Closed => s"Auction ${msg.item} closed with no sale."

      case msg : NotStarted => s"Auction ${msg.item} have not started yet."

      case _ => throw new NotificationException
    }

  }
}

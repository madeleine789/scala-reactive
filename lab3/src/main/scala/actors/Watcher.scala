package actors

import actors.Watcher.{WatchMe, WatchHim}
import akka.actor.{Terminated, ActorRef, ActorLogging, Actor}

import scala.collection.mutable.ArrayBuffer

/**
 * Created by mms on 2015-10-27.
 */
object Watcher{
  // Used by others to register an Actor for watching
  case class WatchMe(ref: ActorRef)
  case class WatchHim(ref: ActorRef)
}
abstract class Watcher extends Actor with ActorLogging {

  val watched = ArrayBuffer.empty[ActorRef]

  // Derivations need to implement this method.  It's the
  // hook that's called when everything's dead
  def shutdownHook(): Unit


  private def watchActor(ref: ActorRef) = {
    log.info(s"Registering $ref, currently watches ${watched.size} actors.")
    log.debug(s"Registering $ref, currently watched auctions: $watched.")
    context.watch(ref)
    watched += ref
  }

  // Watch and check for termination
  final def receive = {
    case WatchMe(ref) => this watchActor ref
    case WatchHim(ref) => this watchActor ref
    case Terminated(ref) =>
      watched -= ref
      log.info(s"Unregistered $ref, currently watches ${watched.size} actors.")
      log.debug(s"Unregistered $ref, currently have registered: $watched.")
      if (watched.isEmpty) shutdownHook()
  }
}
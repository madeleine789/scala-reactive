package actors.search

import akka.actor._
import actors.search.AuctionSearch.{Unregister, Register, Search}
import akka.routing.ActorRefRoutee
import akka.routing.Router
import akka.routing.RoundRobinRoutingLogic
import akka.routing.BroadcastRoutingLogic

class MasterSearch(numberOfNodes: Int) extends Actor with ActorLogging{

  val routees = Vector.fill(numberOfNodes) {
    val r = context.actorOf(Props[AuctionSearch])
    context watch r
    ActorRefRoutee(r)
  }

  var searchRouter = Router(RoundRobinRoutingLogic(), routees)
  var registerRouter = Router(BroadcastRoutingLogic(), routees)
  var unregisterRouter = Router(BroadcastRoutingLogic(), routees)

  def receive = {
    case msg @ Register(name) =>
      registerRouter.route(msg, sender())
    case msg @ Search(name) =>
      searchRouter.route(msg, sender())
    case msg @ Unregister(name) =>
      unregisterRouter.route(msg, sender())
    case Terminated(a) =>
      registerRouter = registerRouter.removeRoutee(a)
      unregisterRouter = registerRouter.removeRoutee(a)
      searchRouter = searchRouter.removeRoutee(a)
      val r = context.actorOf(Props[AuctionSearch])
      context watch r
      registerRouter = registerRouter.addRoutee(r)
      unregisterRouter = registerRouter.addRoutee(r)
      searchRouter = searchRouter.addRoutee(r)
  }
}

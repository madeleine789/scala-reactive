package search

import akka.actor._
import search.AuctionSearch._

import scala.collection.mutable

object AuctionSearch {
  case class Search(phase: String)
  case class SearchResults(auctions: List[ActorRef])
  case object NoResults
  case class Unregister(auctionName: String)
  case class Register(auctionName: String)
}

class AuctionSearch extends Actor with ActorLogging {

  val auctions = mutable.Map[String, ActorRef]()

  override def receive: Receive = {
    case Search(searchPhrase) =>
      log.info(s"Search query for $searchPhrase")
      val phrase = searchPhrase.toLowerCase
      val list = auctions.filterKeys(_.contains(phrase)).values.toList
      if (list.nonEmpty) sender ! SearchResults(list)
      else sender ! NoResults
    case Unregister(auctionName) =>
      log.info(s"Unregistering auction $auctionName by actor ${sender()}")
      auctions -= auctionName
    case Register(name) =>
      log.info(s"Registering auction $name by actor ${sender()}")
      auctions += name.toLowerCase -> sender
  }

}

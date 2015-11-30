package actors

import akka.actor._
import akka.event.LoggingReceive
import com.typesafe.config.ConfigFactory
import actors.search.AuctionSearch.{Register, Search}
import actors.search.MasterSearch

object Message {
  case object Init
  case object Search
  case object Stop
}

class StressTestActor extends Actor with ActorLogging {
  var AUCTION_SEARCH_SLAVES = 6
  var AUCTIONS_TO_REGISTER = 1000000
  var AUCTIONS_TO_SEARCH = 500000

  val masterSearch = context.actorOf(Props(new MasterSearch(AUCTION_SEARCH_SLAVES)), "MasterSearch")
  val auctions = List.tabulate(AUCTIONS_TO_REGISTER)(n =>"auction-%07d".format(n))
  var start = 0L

  def receive = LoggingReceive {
    case Message.Init =>
      auctions.foreach(a => masterSearch ! Register(a))
      self ! Message.Search
    case Message.Search =>
      start = System.nanoTime()
      val toSearch = List.tabulate(AUCTIONS_TO_SEARCH)(a => "auction-%07d".format(scala.util.Random.nextInt(AUCTIONS_TO_REGISTER) + 1))
      toSearch.foreach(a => masterSearch ! Search(a))
      self ! Message.Stop
    case Message.Stop =>
      val time = (System.nanoTime() - start)/1000000000.0
      print("\nRegistered: " + AUCTIONS_TO_REGISTER + "\nSearched: " + AUCTIONS_TO_SEARCH + "\nSlaves: " + AUCTION_SEARCH_SLAVES)
      print("\nTime elapsed: " + time)
      context.system.terminate()
  }
}

object Test {
  def main(args: Array[String]){
    val config = ConfigFactory.load()
    val system = ActorSystem("AuctionSystem", config.getConfig("auction").withFallback(config))
    val tester = system.actorOf(Props(new StressTestActor))
    tester ! Message.Init
  }
}
package actors

import akka.actor.ActorSystem
import akka.testkit.{TestProbe, TestActorRef, ImplicitSender, TestKit}
import org.scalatest.{OneInstancePerTest, Matchers, WordSpecLike}
import search.AuctionSearch
import search.AuctionSearch._

import scala.concurrent.duration._

/**
 * Created by mms on 2015-10-28.
 */
class AuctionSearchSpec extends TestKit(ActorSystem("auction_test")) with WordSpecLike with Matchers with ImplicitSender with OneInstancePerTest {
  val underTest = TestActorRef(new AuctionSearch())

  "AuctionSearch actor" should {
    "register auction" in {
      val auction = TestProbe()
      underTest.tell(Register("test_auction"), auction.ref)
      assert(underTest.underlyingActor.auctions.contains("test_auction"))
    }
    "unregister auction" in {
      val auction = TestProbe()
      underTest.tell(Register("test_auction"), auction.ref)
      assert(underTest.underlyingActor.auctions.contains("test_auction"))
      underTest.tell(Unregister("test_auction"), auction.ref)
      assert(!underTest.underlyingActor.auctions.contains("test_auction"))
    }
    "find existing auction" in {
      val auction = TestProbe()
      underTest.tell(Register("test_auction"), auction.ref)
      assert(underTest.underlyingActor.auctions.contains("test_auction"))
      underTest.tell(Search("test"), auction.ref)
      auction.expectMsg(SearchResults(List(auction.ref)))
    }

    "not find inexistent auction" in {
      val b = TestProbe()
      underTest.tell(Register("test_auction"), b.ref)
      underTest.tell(Search("tst"), b.ref)
      b.expectMsgType[NoResults.type]
    }
  }

}

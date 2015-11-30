package actors

import actors.auction.{Messages, FSMAuction}
import FSMAuction.Registration
import Messages.{RegisterForNotifications, YouWon, Outbid, Offer}

import scala.collection.mutable

/**
 * Created by mms on 2015-10-27.
 */


import akka.actor.ActorSystem
import akka.testkit.{TestProbe, ImplicitSender, TestFSMRef, TestKit}
import org.scalatest.{Matchers, OneInstancePerTest, WordSpecLike}

import scala.concurrent.duration._


class AuctionSpec extends TestKit(ActorSystem("auction_test")) with WordSpecLike with Matchers with ImplicitSender with OneInstancePerTest {
  val underTest = TestFSMRef(new FSMAuction("test_item", 20 seconds))

  "Auction actor" should {
    "change state to Waiting after registration for notifications" in {
      underTest ! RegisterForNotifications
      underTest.stateName should be theSameInstanceAs FSMAuction.Waiting
    }

    "change state to Activated after offer if in Waiting state" in {
      underTest ! RegisterForNotifications
      underTest.stateName should be theSameInstanceAs FSMAuction.Waiting
      underTest ! Offer(10)
      underTest.stateName should be theSameInstanceAs FSMAuction.Activated
    }

    "change state to Activated after first offer" in {
      underTest ! Offer(10)
      underTest.stateName should be theSameInstanceAs FSMAuction.Activated
    }

    "change state to Ignored after 10 seconds" in {
      awaitAssert(underTest.stateName should be theSameInstanceAs FSMAuction.Ignored, 30 seconds, 2 seconds)
    }

    "change state to Sold after bidTime expired and one offer were" in {
      underTest ! Offer(10)
      awaitAssert(underTest.stateName should be theSameInstanceAs FSMAuction.Sold, 30 seconds, 3 seconds)
    }

    "notify previous buyer when outbidded" in {
      val (b1, b2) = (TestProbe(), TestProbe())
      underTest.tell(Offer(10), b1.ref)
      underTest.tell(Offer(20), b2.ref)
      b1.expectMsgType[Outbid]
    }

    "notify only registered buyers about outbidding" in {
      val (b1, b2, b3) = (TestProbe(), TestProbe(), TestProbe())
      underTest.tell(RegisterForNotifications, b1.ref)
      underTest.tell(RegisterForNotifications, b2.ref)
      underTest.stateData should be(Registration(mutable.Set(b1.ref, b2.ref)))
      underTest.tell(Offer(10), b1.ref)
      b2.expectMsgType[Outbid]
      b3.expectNoMsg(5 seconds)
      underTest.tell(Offer(20), b3.ref)
      b1.expectMsgType[Outbid]
      underTest.tell(Offer(20), b2.ref)
      b3.expectNoMsg(5 seconds)
    }
    "notify buyer about auction end" in {
      val b1 = TestProbe()
      underTest.tell(Offer(10), b1.ref)
      awaitAssert(b1.expectMsg(YouWon("test_item", 10)), 40 seconds, 4 seconds)
    }
  }
}



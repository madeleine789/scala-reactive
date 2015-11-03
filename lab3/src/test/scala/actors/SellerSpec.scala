package actors

import actors.Messages.{Offer, EndOfAuction}
import akka.actor.{Props, ActorSystem}
import akka.testkit.{TestActorRef, TestProbe, ImplicitSender, TestKit}
import org.scalatest.{OneInstancePerTest, Matchers, WordSpecLike}

import scala.concurrent.duration._

/**
 * Created by mms on 2015-10-29.
 */
class SellerSpec  extends TestKit(ActorSystem("auction_test")) with WordSpecLike with Matchers with ImplicitSender with OneInstancePerTest {

  "Seller actor" should {
    "get message about unsold item" in {
      val parent = TestProbe()
      val child = TestActorRef(Props(new FSMAuction("item", 3 seconds)), parent.ref, "child")
      awaitAssert(parent.expectMsg(EndOfAuction(false)), 40 seconds, 4 seconds)
    }

    "get message about sold item" in {
      val parent = TestProbe()
      val buyer = TestProbe()
      val child = TestActorRef(Props(new FSMAuction("item", 10 seconds)), parent.ref, "child")
      child.tell(Offer(100), buyer.ref)
      awaitAssert(parent.expectMsg(EndOfAuction(true)), 40 seconds, 4 seconds)
    }
  }

}

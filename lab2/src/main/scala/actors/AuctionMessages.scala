package actors

object AuctionMessages {
  case class Offer(price: Int)
  case class InsufficientOffer(price: Int)
  case class Outbid(price: Int)
  case class YouWon(item: String, price: Int)
  case class ItemIsNotAvailable(item:String)
  case class EndOfAuction(success: Boolean)
  case object BidTimerExpired
  case object DeleteTimerExpired
}

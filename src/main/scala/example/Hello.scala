import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.{ContentTypes, HttpEntity, StatusCodes}
import akka.http.scaladsl.server.Directives._
import akka.stream.ActorMaterializer
import spray.json.DefaultJsonProtocol

import java.time.{DayOfWeek, ZonedDateTime}

case class DeliveryRequest(cart_value: Int, delivery_distance: Int, number_of_items: Int, time: ZonedDateTime)

case class DeliveryResponse(delivery_fee: Int)

class DeliveryCostCalculator(cartValue: Int, deliveryDistance: Int, numberOfItems: Int, deliveryTime: ZonedDateTime) {
  private val SmallOrderThreshold = 1000
  private val BaseDeliveryFee = 200
  private val AdditionalDistanceFee = 100
  private val BulkItemSurcharge = 50
  private val BulkFeeThreshold = 12
  private val MaxDeliveryFee = 1500
  private val FreeDeliveryThreshold = 1000
  private val RushHourMultiplier = 12 / 10 // 1.2
  private val Friday = DayOfWeek.FRIDAY
  private val RushHourStart = 15
  private val RushHourEnd = 19

  def calculateDeliveryFee(): Int = {
    val smallOrderSurcharge: Int = calculateSmallOrderSurcharge()
    val baseFee: Int = BaseDeliveryFee + smallOrderSurcharge
    val additionalDistanceFee: Int = calculateAdditionalDistanceFee()
    val bulkSurcharge: Int = calculateBulkSurcharge()
    val bulkFee: Int = if (numberOfItems > BulkFeeThreshold) 120 else 0
    val totalFee: Int = baseFee + additionalDistanceFee + bulkSurcharge + bulkFee

    val finalFee: Int = if (cartValue >= FreeDeliveryThreshold) 0 else Math.min(MaxDeliveryFee, totalFee)
    if (isFridayRushHour()) (finalFee * RushHourMultiplier).toInt else finalFee
  }

  private def calculateSmallOrderSurcharge(): Int = {
    Math.max(0, SmallOrderThreshold - cartValue)
  }

  private def calculateAdditionalDistanceFee(): Int = {
    val distanceAboveThreshold = Math.max(0, deliveryDistance - 1000)
    val additionalDistanceBlocks = Math.ceil(distanceAboveThreshold / 500).toInt
    BaseDeliveryFee + additionalDistanceBlocks * AdditionalDistanceFee
  }

  private def calculateBulkSurcharge(): Int = {
    Math.max(0, numberOfItems - 4) * BulkItemSurcharge
  }

  private def isFridayRushHour(): Boolean = {
    val isFriday = deliveryTime.getDayOfWeek == Friday
    val isRushHour = deliveryTime.getHour >= RushHourStart && deliveryTime.getHour < RushHourEnd
    isFriday && isRushHour
  }
}

object DeliveryJsonProtocol extends DefaultJsonProtocol {
  implicit val requestFormat = jsonFormat4(DeliveryRequest)
  implicit val responseFormat = jsonFormat1(DeliveryResponse)
}

object Main extends App {
  import DeliveryJsonProtocol._

  implicit val system = ActorSystem("delivery-system")
  implicit val materializer = ActorMaterializer()
  implicit val executionContext = system.dispatcher

  val route =
    path("deliveryFee") {
      post {
        entity(as[DeliveryRequest]) { request =>
          val calculator = new DeliveryCostCalculator(request.cart_value, request.delivery_distance, request.number_of_items, request.time)
          val deliveryFee = calculator.calculateDeliveryFee()
          complete(DeliveryResponse(deliveryFee))
        }
      }
    }

  val bindingFuture = Http().bindAndHandle(route, "localhost", 8080)

  println(s"Server online at http://localhost:8080/")

  sys.addShutdownHook {
    bindingFuture
      .flatMap(_.unbind())
      .onComplete(_ => system.terminate())
  }
}

// hove to fix Json handling
// add try and catch
// add testing
// search the syntaxes
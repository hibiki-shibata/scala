import akka.actor.typed.ActorSystem
import akka.actor.typed.scaladsl.Behaviors
import akka.http.scaladsl.Http
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route.seal
import scala.io.StdIn
import akka.http.scaladsl.server.Directives
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import spray.json._
import java.time.{ DayOfWeek, ZonedDateTime }
import java.time.format.DateTimeFormatter

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
  private val RushHourMultiplier = 1.2
  private val Friday = DayOfWeek.FRIDAY
  private val RushHourStart = 15
  private val RushHourEnd = 19

  def calculateDeliveryFee(): Int = {
    try {
      val smallOrderSurcharge = calculateSmallOrderSurcharge()
      val baseFee = BaseDeliveryFee + smallOrderSurcharge
      val additionalDistanceFee = calculateAdditionalDistanceFee()
      val bulkSurcharge = calculateBulkSurcharge()
      val bulkFee = if (numberOfItems > BulkFeeThreshold) 120 else 0
      val totalFee = baseFee + additionalDistanceFee + bulkSurcharge + bulkFee

      val finalFee = if (cartValue >= FreeDeliveryThreshold) 0 else Math.min(MaxDeliveryFee, totalFee)
      if (isFridayRushHour()) (finalFee * RushHourMultiplier).toInt else finalFee
    } catch {
      case e: Exception =>
        // Handle the exception as per your requirements
        println(s"Error calculating delivery fee: ${e.getMessage}")
        0 // Return a default value or handle the error in an appropriate way
    }
  }
private def calculateSmallOrderSurcharge(): Int = Math.max(0, SmallOrderThreshold - cartValue)


  private def calculateAdditionalDistanceFee(): Int = {
    val distanceAboveThreshold = Math.max(0, deliveryDistance - 1000)
    val additionalDistanceBlocks = Math.ceil(distanceAboveThreshold / 500).toInt
    BaseDeliveryFee + additionalDistanceBlocks * AdditionalDistanceFee
  }


  private def calculateBulkSurcharge(): Int = Math.max(0, numberOfItems - 4) * BulkItemSurcharge


  private def isFridayRushHour(): Boolean = {
    val isFriday = deliveryTime.getDayOfWeek == Friday
    val isRushHour = deliveryTime.getHour >= RushHourStart && deliveryTime.getHour < RushHourEnd
    isFriday && isRushHour
  }
}

trait JsonSupport extends SprayJsonSupport with DefaultJsonProtocol {
 implicit object ZonedDateTimeFormat extends JsonFormat[ZonedDateTime] {
    private val pattern = "yyyy-MM-dd'T'HH:mm:ssX"
    private val formatter = DateTimeFormatter.ofPattern(pattern)

    def write(obj: ZonedDateTime): JsValue = JsString(formatter.format(obj))
    def read(json: JsValue): ZonedDateTime = json match {
      case JsString(s) => ZonedDateTime.parse(s, formatter)
      case _ => deserializationError("Expected ZonedDateTime as JsString")
    }
  }


  implicit val DeliveryRequestFormat: RootJsonFormat[DeliveryRequest] = jsonFormat4(DeliveryRequest)
  implicit val DeliveryResponseFormat: RootJsonFormat[DeliveryResponse] = jsonFormat1(DeliveryResponse)
}

object HttpServerRoutingMinimal extends Directives with JsonSupport {

  def main(args: Array[String]): Unit = {
    implicit val system = ActorSystem(Behaviors.empty, "my-system")
    implicit val executionContext = system.executionContext

    val route =
      path("deliveryFee") {
        post {
          entity(as[DeliveryRequest]) { request =>
            try {
              val calculator = new DeliveryCostCalculator(request.cart_value, request.delivery_distance, request.number_of_items, request.time)
              val deliveryFee = calculator.calculateDeliveryFee()
              complete(DeliveryResponse(deliveryFee))
            } catch {
              case e: Exception =>
                // Handle the exception as per your requirements
                complete(s"Error processing the request: ${e.getMessage}")
            }
          }
        }
      }

    val bindingFuture = Http().newServerAt("localhost", 8080).bind(route)

    println(s"Server is running")
    StdIn.readLine()
    bindingFuture.flatMap(_.unbind()).onComplete(_ => system.terminate())
  }
}

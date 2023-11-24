import akka.actor.typed.ActorSystem
import akka.actor.typed.scaladsl.Behaviors
import akka.http.scaladsl.Http
import akka.http.scaladsl.model._
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route.seal
import scala.io.StdIn


import akka.http.scaladsl.server.Directives
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import spray.json._
import java.time.{DayOfWeek, ZonedDateTime}
import java.time.format.DateTimeFormatter


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

final case class DeliveryRequest(cart_value: Int, delivery_distance: Int, number_of_items: Int, time: ZonedDateTime)
final case class DeliveryResponse(delivery_fee: Int)


trait JsonSupport extends SprayJsonSupport with DefaultJsonProtocol {
  implicit object ZonedDateTimeFormat extends JsonFormat[ZonedDateTime] {
    private val pattern = "yyyy-MM-dd'T'HH:mm:ss.SSZ"
    private val formatter = DateTimeFormatter.ofPattern(pattern)

    def write(obj: ZonedDateTime): JsValue = JsString(formatter.format(obj))
    def read(json: JsValue): ZonedDateTime = json match {
      case JsString(s) => ZonedDateTime.parse(s, formatter)
      case _ => deserializationError("Expected ZonedDateTime as JsString")
    }
  }

  implicit val DeliveryRequestFormat: RootJsonFormat[DeliveryRequest] = jsonFormat4(DeliveryRequest.apply)
  implicit val DeliveryResponseFormat: RootJsonFormat[DeliveryResponse] = jsonFormat1(DeliveryResponse.apply)
}


object HttpServerRoutingMinimal extends Directives with JsonSupport{

  def main(args: Array[String]): Unit = {

    implicit val system = ActorSystem(Behaviors.empty, "my-system")
    // needed for the future flatMap/onComplete in the end
    implicit val executionContext = system.executionContext

  
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

    val bindingFuture = Http().newServerAt("localhost", 8080).bind(route)

    println(s"Server now online. Please navigate to http://localhost:8080/hello\nPress RETURN to stop...")
    StdIn.readLine() // let it run until user presses return
    bindingFuture
      .flatMap(_.unbind()) // trigger unbinding from the port
      .onComplete(_ => system.terminate()) // and shutdown when done
  }
}

// hove to fix Json handling | https://doc.akka.io/docs/akka-http/current/common/json-support.html
// add try and catch
// add testing
// search the syntaxes


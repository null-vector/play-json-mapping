package org.nullvector

import org.scalatest.Matchers._
import org.scalatest._
import play.api.libs.json.{Json, JsonConfiguration}
import play.api.libs.json.JsonNaming.SnakeCase

import scala.util.{Success, Try}

class JsonMapperSpec extends FlatSpec {

  it should "create a complex mapping" in {

    import JsonMapper._

    val place = Place(
      "Watership Down",
      Location(51.235685, -1.309197),
      Seq(
        Resident("Fiver", 4, None),
        Resident("Bigwig", 6, Some("Owsla"))
      )
    )

    implicit val config = JsonConfiguration(SnakeCase)

    implicit val a = writesOf[Place]

    val jsValue = place.asJson
    println(Json.prettyPrint(jsValue))
    (jsValue \ "center_location" \ "lat").as[Double] shouldBe 51.235685


  }

}


sealed trait Day

object Day {
  def apply(name: String): Day = name match {
    case "Monday" => Monday
    case "Sunday" => Sunday
  }
}

case object Monday extends Day

case object Sunday extends Day

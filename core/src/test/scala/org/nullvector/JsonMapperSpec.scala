package org.nullvector

import org.nullvector.domian._
import org.scalatest.Matchers._
import org.scalatest._
import play.api.libs.json.{Format, Json, JsonConfiguration, Reads, Writes}

class JsonMapperSpec extends FlatSpec {

  it should "create a writes for complex case classes graph" in {
    import JsonMapper._

    val place = Place(
      "Watership Down",
      Location(51.235685, -1.309197),
      Seq(
        Resident("Fiver", 4, None),
        Resident("Bigwig", 6, Some("Owsla"))
      )
    )

    implicit val w: Writes[Place] = writesOf[Place](snakeAndTypeNamingAndWriteNullsConfiguration)
    implicit val r: Reads[Place] = readsOf[Place](snakeAndTypeNamingConfiguration)

    val jsValue = place.asJson
    jsValue.toString() should include("\"role\":null")
    (jsValue \ "center_location" \ "lat").as[Double] shouldBe 51.235685
    jsValue.as[Place].name should be(place.name)
  }

  it should "create a writes with a seales trait family" in {
    import JsonMapper._

    val operationSchedule = OperationSchedule(Monday)

    implicit val conf = JsonConfiguration(typeNaming = typeNaming)

    implicit val x = mappingOf[OperationSchedule]

    val jsValue = operationSchedule.asJson

    (jsValue \ "availableDay" \ "_type").as[String] shouldBe "Monday"
    jsValue.as[OperationSchedule].availableDay shouldBe Monday
  }

}


package org.nullvector.api.json

import java.time.LocalDateTime
import java.util.Locale

import org.joda.time.DateTime
import org.nullvector.api.json.domian.{DaysOpen, Location, Monday, Money, OperationSchedule, Place, Product, ProductId, Resident, Sunday, SupportedTypes}
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers._
import play.api.libs.json.{Format, Json, JsonConfiguration, Reads, Writes}

class JsonMapperSpec extends AnyFlatSpec {

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

  it should "mapping of trait family inside type class" in {
    import JsonMapper._
    val example = DaysOpen(List(Monday, Sunday))
    implicit val conf = JsonConfiguration(typeNaming = typeNaming)
    implicit val x = mappingOf[DaysOpen]
    val jsValue = example.asJson

    jsValue.as[DaysOpen] shouldBe example
  }

  it should "create a format mapping with enum" in {
    import JsonMapper._
    implicit val m: Format[Money] = mappingOf[Money]

    val aMoney = Money.ars(2345.678)

    aMoney.asJson.as[Money] should be(aMoney)
  }

  it should "create a writes mapping with enum" in {
    import JsonMapper._
    implicit val w: Writes[Money] = writesOf[Money]

    val aMoney = Money(6783.3211, Money.MXN)

    aMoney.asJson.toString() should be("""{"amount":6783.3211,"currency":"MXN"}""")
  }

  it should "create a reads mapping with enum" in {
    import JsonMapper._
    implicit val r: Reads[Money] = readsOf[Money]

    Json
      .parse("""{"amount":6783.3211,"currency":"USD"}""")
      .as[Money] shouldBe Money(6783.3211, Money.USD)
  }

  it should "creat mapping with AnyVal" in {
    import JsonMapper._
    implicit val m = mappingOf[domian.Product]

    val json = Product(new ProductId(23), "Train").asJson
    println(json)
  }

  it should "creat a read mapping with AnyVal" in {
    import JsonMapper._
    implicit val m = readsOf[domian.Product]

    Json
      .parse("""{"productId":23,"name":"Train"}""")
      .as[domian.Product] shouldBe Product(new ProductId(23), "Train")
  }

  it should "creat a write mapping with AnyVal" in {
    import JsonMapper._
    implicit val m = writesOf[domian.Product]

    Product(new ProductId(23), "Train")
      .asJson.toString() shouldBe """{"productId":23,"name":"Train"}"""
  }

}


# Play json Mapping
Enhance the ability to create writes and reads from complex case classes graph. 

[ ![Download](https://api.bintray.com/packages/null-vector/releases/play-json-mapping/images/download.svg?version=1.1.1) ](https://bintray.com/null-vector/releases/play-json-mapping/1.1.1/link)

## Installation
Add into your `build.sbt` the following lines:
```scala
resolvers += Resolver.bintrayRepo("null-vector", "releases")
```
```scala
libraryDependencies += "null-vector" %% "play-json-mapping" % "1.0.x"
```
## Example
This example use the same model using in play-json examples:
```scala
import play.api.libs.json._
import org.nullvector.JsonMapper._

case class Location(lat: Double, long: Double)
case class Resident(name: String, age: Int, role: Option[String])
case class Place(name: String, centerLocation: Location, residents: Seq[Resident])

implicit val w: Writes[Place] = writesOf[Place]
implicit val r: Reads[Place] = readsOf[Place]
//or just:
implicit val m: Format[Place] = mappingOf[Place]
```
And use as follow:
```scala
val place = Place(
  "Watership Down",
  Location(51.235685, -1.309197),
  Seq(
    Resident("Fiver", 4, None),
    Resident("Bigwig", 6, Some("Owsla"))
  )
)

val json: JsValue = place.asJson
```
Scala Enumerations are also automatic mapping:
```scala
case class Money(amount: BigDecimal, currency: Money.Currency)

object Money extends Enumeration {
  type Currency = Value
  val ARS, BRL, USD, MXN = Value
}
...
implicit val moneyMapping: Format[Money] = JsonMapper.mappingOf[Money]
```

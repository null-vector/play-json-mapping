# Play json Mapping
Enhance the ability to create writes and reads from complex case classes graph.

Supports **Scala 2.13** and **Scala 3**.


## Installation
Add into your `build.sbt` the following lines:
```sbt
resolvers += "GitHub Package Registry" at "https://maven.pkg.github.com/null-vector/play-json-mapping"

credentials ++= {
      val token = sys.env.get("GITHUB_TOKEN")
      token.toSeq.map { t =>
        Credentials("GitHub Package Registry", "maven.pkg.github.com", "_", t)
      }
    }

libraryDependencies += "null-vector" %% "play-json-mapping" % "<version>"
```

GitHub Packages requires a token with `read:packages` (set `GITHUB_TOKEN`).

Published packages: https://github.com/null-vector/play-json-mapping/packages

## Example
This example use the same model using in play-json examples:
```scala
import play.api.libs.json._
import org.nullvector.api.json.JsonMapper._

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

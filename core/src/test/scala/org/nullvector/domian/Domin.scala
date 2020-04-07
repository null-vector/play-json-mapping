package org.nullvector.domian

case class Location(lat: Double, long: Double)
case class Resident(name: String, age: Int, role: Option[String])
case class Place(name: String, centerLocation: Location, residents: Seq[Resident])
case class OperationSchedule(availableDay: Day)

sealed trait Day

object Day {
  def apply(name: String): Day = name match {
    case "Monday" => Monday
    case "Sunday" => Sunday
  }
}

case object Monday extends Day

case object Sunday extends Day
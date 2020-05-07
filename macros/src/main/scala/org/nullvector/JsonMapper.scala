package org.nullvector

import play.api.libs.json.JsonConfiguration.Aux
import play.api.libs.json.JsonNaming.SnakeCase
import play.api.libs.json.OptionHandlers.WritesNull
import play.api.libs.json.{Format, JsValue, Json, JsonConfiguration, JsonNaming, Reads, Writes}

import scala.util.matching.Regex

object JsonMapper {

  private val typeNameRegex: Regex = "^(\\$)?([^$.]*)(.*)".r

  val typeNaming: JsonNaming = (property: String) => property.reverse match {
    case typeNameRegex(_, name, _) => name.reverse
    case _ => property
  }

  val snakeAndTypeNamingConfiguration: JsonConfiguration = JsonConfiguration(SnakeCase, typeNaming = typeNaming)

  val snakeAndTypeNamingAndWriteNullsConfiguration: JsonConfiguration = JsonConfiguration(SnakeCase, typeNaming = typeNaming, optionHandlers = WritesNull)

  def mappingOf[T]: Format[T] = macro JsonMapperMacroFactory.mappingOf[T]

  def mappingOf[T](jsonConfiguration: JsonConfiguration): Format[T] = macro JsonMapperMacroFactory.mappingWithConfigOf[T]

  def readsOf[T]: Reads[T] = macro JsonMapperMacroFactory.readsOf[T]

  def readsOf[T](jsonConfiguration: JsonConfiguration): Reads[T] = macro JsonMapperMacroFactory.readsWithConfigOf[T]

  def writesOf[T]: Writes[T] = macro JsonMapperMacroFactory.writesOf[T]

  def writesOf[T](jsonConfiguration: JsonConfiguration): Writes[T] = macro JsonMapperMacroFactory.writesWithConfigOf[T]

  implicit class WritesDsl[T](anInstance: T) {

    def asJson(implicit w: Writes[T]): JsValue = Json.toJson(anInstance)
  }

}

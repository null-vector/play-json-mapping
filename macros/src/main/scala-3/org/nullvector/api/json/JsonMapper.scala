package org.nullvector.api.json

import play.api.libs.json.JsonNaming.SnakeCase
import play.api.libs.json.OptionHandlers.WritesNull
import play.api.libs.json.{Format, JsValue, Json, JsonConfiguration, JsonNaming, Reads, Writes}

import scala.util.matching.Regex

object JsonMapper {

  private val typeNameRegex: Regex = """^(\$)?([^$.]*)(.*)""".r

  val typeNaming: JsonNaming = (property: String) => property.reverse match {
    case typeNameRegex(_, name, _) => name.reverse
    case _ => property
  }

  val snakeAndTypeNamingConfiguration: JsonConfiguration = JsonConfiguration(SnakeCase, typeNaming = typeNaming)

  val snakeAndTypeNamingAndWriteNullsConfiguration: JsonConfiguration =
    JsonConfiguration(SnakeCase, typeNaming = typeNaming, optionHandlers = WritesNull)

  inline def mappingOf[T]: Format[T] = ${ JsonMapperMacroFactory.mappingOf[T] }

  inline def mappingOf[T](inline jsonConfiguration: JsonConfiguration): Format[T] =
    ${ JsonMapperMacroFactory.mappingWithConfigOf[T]('jsonConfiguration) }

  inline def readsOf[T]: Reads[T] = ${ JsonMapperMacroFactory.readsOf[T] }

  inline def readsOf[T](inline jsonConfiguration: JsonConfiguration): Reads[T] =
    ${ JsonMapperMacroFactory.readsWithConfigOf[T]('jsonConfiguration) }

  inline def writesOf[T]: Writes[T] = ${ JsonMapperMacroFactory.writesOf[T] }

  inline def writesOf[T](inline jsonConfiguration: JsonConfiguration): Writes[T] =
    ${ JsonMapperMacroFactory.writesWithConfigOf[T]('jsonConfiguration) }

  extension [T](anInstance: T) {
    def asJson(using w: Writes[T]): JsValue = Json.toJson(anInstance)
  }

}

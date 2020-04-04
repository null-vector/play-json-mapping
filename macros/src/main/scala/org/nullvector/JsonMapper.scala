package org.nullvector

import play.api.libs.json.{JsValue, Json, Writes}

object JsonMapper {

  def writesOf[T]: Writes[T] = macro JsonMapperMacroFactory.writesOf[T]

  implicit class WritesDsl[T](anInstance: T) {

    def asJson(implicit w: Writes[T]): JsValue = Json.toJson(anInstance)
  }

}

package org.nullvector

import play.api.libs.json.{Format, JsValue, Reads, Writes}

import scala.reflect.macros.blackbox

private object JsonMapperMacroFactory {

  implicit class MapOnPair[+T1, +T2](pair: (T1, T2)) {
    def map[A1, A2](f: (T1, T2) => (A1, A2)): (A1, A2) = f(pair._1, pair._2)
  }

  private val supportedClassTypes = List(
    "scala.Option",
    "scala.collection.immutable.List",
    "scala.collection.immutable.Seq",
    "scala.collection.Seq",
    "scala.collection.immutable.Set",
    "scala.collection.immutable.Map",
  )


  def writesOf[E](context: blackbox.Context)
                 (implicit domainTypeTag: context.WeakTypeTag[E]): context.Expr[Writes[E]] = {

    import context.universe._
    val typeOfWrites = context.typeOf[Writes[_]]
    val typeOfReads = context.typeOf[Reads[_]]
    val typeOfFormat = context.typeOf[Format[_]]


    val (toBeImplicit, toBeMainWriter) = extractCaseTypes(context)(domainTypeTag.tpe).toList.reverse.distinct.partition(_ != domainTypeTag.tpe)

    val implicitWriters = toBeImplicit
      .filter { caseType =>
        context.inferImplicitValue(appliedType(typeOfWrites, caseType)).isEmpty ||
        context.inferImplicitValue(appliedType(typeOfFormat, caseType)).isEmpty
      }
      .map(caseType => q"""private implicit val ${TermName(context.freshName())} = play.api.libs.json.Json.writes[$caseType] """)

    val code =
      q"""
        ..$implicitWriters
        play.api.libs.json.Json.writes[${toBeMainWriter.head}]
       """
    println(code)
    context.Expr[Writes[E]](code)
  }

  private def extractCaseTypes(context: blackbox.Context)
                              (caseType: context.universe.Type): org.nullvector.Tree[context.universe.Type] = {
    import context.universe._

    def extaracCaseClassesFromSupportedTypeClasses(classType: Type): List[Type] = {
      if (supportedClassTypes.contains(classType.typeSymbol.fullName)) classType.typeArgs.collect {
        case argType if argType.typeSymbol.asClass.isCaseClass => List(classType, argType)
        case t => extaracCaseClassesFromSupportedTypeClasses(t)
      }.flatten else Nil
    }

    if (caseType.typeSymbol.asClass.isCaseClass) {
      Tree(caseType,
        caseType.decls.toList
          .collect { case method: MethodSymbol if method.isCaseAccessor => method.returnType }
          .collect {
            case aType if aType.typeSymbol.asClass.isCaseClass => List(extractCaseTypes(context)(aType))
            case aType => extaracCaseClassesFromSupportedTypeClasses(aType).map(arg => extractCaseTypes(context)(arg))
          }.flatten
      )
    }
    else Tree.empty
  }

}

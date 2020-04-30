package org.nullvector

import org.nullvector.tree.Tree
import play.api.libs.json.{Format, JsonConfiguration, Reads, Writes}

import scala.reflect.macros.blackbox

private object JsonMapperMacroFactory {

  def writesOf[E](context: blackbox.Context)
                 (implicit domainTypeTag: context.WeakTypeTag[E]): context.Expr[Writes[E]] = {
    buildExpression(context, WritesExpressionFactory)(None, domainTypeTag)
  }

  def writesWithConfigOf[E](context: blackbox.Context)(jsonConfiguration: context.Expr[JsonConfiguration])
                           (implicit domainTypeTag: context.WeakTypeTag[E]): context.Expr[Writes[E]] = {
    buildExpression(context, WritesExpressionFactory)(Some(jsonConfiguration), domainTypeTag)
  }

  def readsOf[E](context: blackbox.Context)
                (implicit domainTypeTag: context.WeakTypeTag[E]): context.Expr[Reads[E]] = {
    buildExpression(context, ReadsExpressionFactory)(None, domainTypeTag)
  }

  def readsWithConfigOf[E](context: blackbox.Context)(jsonConfiguration: context.Expr[JsonConfiguration])
                          (implicit domainTypeTag: context.WeakTypeTag[E]): context.Expr[Reads[E]] = {
    buildExpression(context, ReadsExpressionFactory)(Some(jsonConfiguration), domainTypeTag)
  }

  def mappingOf[E](context: blackbox.Context)
                  (implicit domainTypeTag: context.WeakTypeTag[E]): context.Expr[Format[E]] = {
    buildExpression(context, FormatExpressionFactory)(None, domainTypeTag)
  }

  def mappingWithConfigOf[E](context: blackbox.Context)(jsonConfiguration: context.Expr[JsonConfiguration])
                            (implicit domainTypeTag: context.WeakTypeTag[E]): context.Expr[Format[E]] = {
    buildExpression(context, FormatExpressionFactory)(Some(jsonConfiguration), domainTypeTag)
  }


  private def buildExpression[E](context: blackbox.Context, mapperFilter: ExpressionFactory)
                                (jsonConfiguration: Option[context.Expr[JsonConfiguration]], domainTypeTag: context.WeakTypeTag[E]): context.Expr[Format[E]] = {

    import context.universe._

    val tree = extractTypes(context)(domainTypeTag.tpe).filterTree(mapperFilter.typeNotImplicitDeclared(context)(_, domainTypeTag.tpe))
    val (toBeImplicit) = tree.toList.reverse.distinct.filterNot(_ =:= domainTypeTag.tpe)
    val implicitWriters = mapperFilter.implicitExpressionsFrom(context)(toBeImplicit)

    val config = jsonConfiguration
      .map(confExpr => q"private implicit val ${TermName(context.freshName())} = $confExpr")
      .getOrElse(EmptyTree)

    val code =
      q"""
        import play.api.libs.json._
        import play.api.libs.json.Json._
        $config
        ..$implicitWriters
        ${mapperFilter.mainExpressionFrom(context)(domainTypeTag.tpe)}
       """
    //println(code)
    context.Expr[Format[E]](code)
  }

  private def extractTypes(context: blackbox.Context)
                          (mainType: context.universe.Type): Tree[context.universe.Type] = {
    import context.universe._

    def isSupprtedTrait(aTypeClass: ClassSymbol) = aTypeClass.isTrait && aTypeClass.isSealed && !aTypeClass.fullName.startsWith("scala")

    def extaracCaseClassesFromSupportedTypeClasses(classType: Type): List[Type] = {
      classType.typeArgs.collect {
        case argType if argType.typeSymbol.asClass.isCaseClass => List(classType, argType)
        case t => extaracCaseClassesFromSupportedTypeClasses(t)
      }.flatten
    }

    val aTypeClass: context.universe.ClassSymbol = mainType.typeSymbol.asClass

    if (aTypeClass.isCaseClass) {
      val accesors = mainType.decls.toList.collect { case method: MethodSymbol if method.isCaseAccessor => method.returnType }
      (s"$accesors") //This is the most strange thing that I have never seen in my life...
      Tree(mainType,
        accesors
          .collect {
            case aType if aType.typeSymbol.asClass.isCaseClass || isSupprtedTrait(aType.typeSymbol.asClass) => List(extractTypes(context)(aType))
            case aType => extaracCaseClassesFromSupportedTypeClasses(aType).map(arg => extractTypes(context)(arg))
          }.flatten
      )
    }
    else if (isSupprtedTrait(aTypeClass)) {
      Tree(mainType, aTypeClass.knownDirectSubclasses.map(aType => extractTypes(context)(aType.asClass.toType)).toList)
    }
    else Tree.empty
  }

  sealed trait ExpressionFactory {

    def typeNotImplicitDeclared(context: blackbox.Context)(aType: context.Type, ignore: context.Type): Boolean

    def implicitExpressionsFrom(context: blackbox.Context)(types: List[context.Type]): List[context.Tree]

    def mainExpressionFrom(context: blackbox.Context)(tpe: context.Type): context.Tree
  }

  private object FormatExpressionFactory extends ExpressionFactory {

    override def implicitExpressionsFrom(context: blackbox.Context)(types: List[context.Type]): List[context.Tree] = {
      types.map(aType => context.parse(s"""private implicit val ${context.freshName()}: Format[$aType] = format[$aType] """))
    }

    override def mainExpressionFrom(context: blackbox.Context)(tpe: context.Type): context.Tree = {
      context.parse(s"format[$tpe]")
    }

    override def typeNotImplicitDeclared(context: blackbox.Context)(aType: context.Type, ignore: context.Type): Boolean = {
      if (aType =:= ignore) true
      else {
        import context.universe._
        val typeOfFormat = context.typeOf[Format[_]]
        context.inferImplicitValue(appliedType(typeOfFormat, aType)).isEmpty
      }
    }
  }

  private object WritesExpressionFactory extends ExpressionFactory {

    override def implicitExpressionsFrom(context: blackbox.Context)(types: List[context.Type]): List[context.Tree] = {
      types.map(aType => context.parse(s"""private implicit val ${context.freshName()}: Writes[$aType] = writes[$aType] """))
    }

    override def mainExpressionFrom(context: blackbox.Context)(tpe: context.Type): context.Tree = {
      context.parse(s"writes[$tpe]")
    }

    override def typeNotImplicitDeclared(context: blackbox.Context)(aType: context.Type, ignore: context.Type): Boolean = {
      if (aType =:= ignore) true
      else {
        import context.universe._
        val typeOfFormat = context.typeOf[Writes[_]]
        context.inferImplicitValue(appliedType(typeOfFormat, aType)).isEmpty
      }
    }
  }

  private object ReadsExpressionFactory extends ExpressionFactory {

    override def implicitExpressionsFrom(context: blackbox.Context)(types: List[context.Type]): List[context.Tree] = {
      types.map(aType => context.parse(s"""private implicit val ${context.freshName()}: Reads[$aType] = reads[$aType] """))
    }

    override def mainExpressionFrom(context: blackbox.Context)(tpe: context.Type): context.Tree = {
      context.parse(s"reads[$tpe]")
    }

    override def typeNotImplicitDeclared(context: blackbox.Context)(aType: context.Type, ignore: context.Type): Boolean = {
      if (aType =:= ignore) true
      else {
        import context.universe._
        val typeOfFormat = context.typeOf[Reads[_]]
        context.inferImplicitValue(appliedType(typeOfFormat, aType)).isEmpty
      }
    }
  }

}

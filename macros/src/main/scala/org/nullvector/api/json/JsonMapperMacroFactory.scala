package org.nullvector.api.json

import org.nullvector.api.json.tree.{Tree => NTree}
import play.api.libs.json.{Format, JsValue, JsonConfiguration, Reads, Writes}

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
    //context.warning(context.enclosingPosition, code.toString())
    context.Expr[Format[E]](code)
  }


  private def extractTypes(context: blackbox.Context)
                          (rootType: context.universe.Type): NTree[context.universe.Type] = {
    import context.universe._
    val enumType = context.typeOf[Enumeration]
    val anyValType = context.typeOf[AnyVal]
    val jsValueType = context.typeOf[JsValue]

    def extractAll(testType: context.universe.Type): NTree[context.universe.Type] = {
      def isSupportedTrait(aTypeClass: ClassSymbol) = aTypeClass.isTrait && aTypeClass.isSealed && !aTypeClass.fullName.startsWith("scala")
      def isCaseOrTrait(aType: context.universe.Type) =
        aType.typeSymbol.isClass && (aType.typeSymbol.asClass.isCaseClass || isSupportedTrait(aType.typeSymbol.asClass))

      def extractCaseClassesFromTypeArgs(classType: Type): List[Type] = {
        classType.typeArgs.collect {
          case aType if aType <:< jsValueType => Nil
          case argType if isCaseOrTrait(argType) => List(classType, argType)
          case t => extractCaseClassesFromTypeArgs(t)
        }.flatten
      }

      val caseTypeAsClass = testType.typeSymbol.asClass

      if (caseTypeAsClass.isCaseClass) {
        tree.Tree(testType,
          testType.decls
            .collect { case method: MethodSymbol if method.isCaseAccessor =>
              val returnType = method.returnType
              returnType.toString // This is needed to materialize the type (WTF!!)
              returnType
            }
            .collect {
              case aType if aType <:< jsValueType => Nil
              case aType if aType <:< anyValType => List(NTree(aType))
              case aType if aType.typeSymbol.owner.isType && aType.typeSymbol.owner.asType.toType =:= enumType => List(NTree(aType))
              case aType if isCaseOrTrait(aType) => List(extractAll(aType))
              case aType => extractCaseClassesFromTypeArgs(aType).map(arg => extractAll(arg))
            }
            .flatten.toList
        )
      }
      else if (isSupportedTrait(caseTypeAsClass)) {
        val subclasses = caseTypeAsClass.knownDirectSubclasses
        tree.Tree(testType, subclasses.map(aType => extractAll(aType.asClass.toType)).toList)
      }
      else NTree.empty
    }

    extractAll(rootType)
  }


  sealed trait ExpressionFactory {

    def enumTypeName(context: blackbox.Context)(aType: context.Type): Option[String] = {
      val enumType = context.typeOf[Enumeration]
      if (aType.typeSymbol.owner.asType.toType =:= enumType) Some(aType.toString.split("\\.").dropRight(1).mkString("."))
      else None
    }

    def typeNotImplicitDeclared(context: blackbox.Context)(aType: context.Type, ignore: context.Type): Boolean

    def implicitExpressionsFrom(context: blackbox.Context)(types: List[context.Type]): List[context.Tree]

    def mainExpressionFrom(context: blackbox.Context)(tpe: context.Type): context.Tree
  }

  private object FormatExpressionFactory extends ExpressionFactory {

    override def implicitExpressionsFrom(context: blackbox.Context)(types: List[context.Type]): List[context.Tree] = {
      types.map(aType => enumTypeName(context)(aType) match {
        case Some(enumName) =>
          context.parse(s"""private implicit val ${context.freshName()}: Format[$aType] = formatEnum($enumName) """)
        case None =>
          if (aType <:< context.typeOf[AnyVal])
            context.parse(s"""private implicit val ${context.freshName()}: Format[$aType] = valueFormat[$aType] """)
          else
            context.parse(s"""private implicit val ${context.freshName()}: Format[$aType] = format[$aType] """)
      })
    }

    override def mainExpressionFrom(context: blackbox.Context)(tpe: context.Type): context.Tree = {
      import context.universe._
      (q"format[$tpe]")
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
      types.map(aType => enumTypeName(context)(aType) match {
        case Some(enumName) =>
          context.parse(s"""private implicit val ${context.freshName()}: Writes[$aType] = Writes.enumNameWrites[$enumName] """)
        case None =>
          if (aType <:< context.typeOf[AnyVal])
            context.parse(s"""private implicit val ${context.freshName()}: Writes[$aType] = valueWrites[$aType] """)
          else
            context.parse(s"""private implicit val ${context.freshName()}: Writes[$aType] = writes[$aType] """)
      })
    }

    override def mainExpressionFrom(context: blackbox.Context)(tpe: context.Type): context.Tree = {
      import context.universe._
      (q"writes[$tpe]")
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
      types.map(aType => enumTypeName(context)(aType) match {
        case Some(enumName) =>
          context.parse(s"""private implicit val ${context.freshName()}: Reads[$aType] = Reads.enumNameReads($enumName) """)
        case None =>
          if (aType <:< context.typeOf[AnyVal])
            context.parse(s"""private implicit val ${context.freshName()}: Reads[$aType] = valueReads[$aType] """)
          else
          context.parse(s"""private implicit val ${context.freshName()}: Reads[$aType] = reads[$aType] """)
      })
    }

    override def mainExpressionFrom(context: blackbox.Context)(tpe: context.Type): context.Tree = {
      import context.universe._
      (q"reads[$tpe]")
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

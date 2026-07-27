package org.nullvector.api.json

import org.nullvector.api.json.tree.{Tree => NTree}
import play.api.libs.json.{Format, JsValue, Json, JsonConfiguration, Reads, Writes}

import scala.quoted.*

private object JsonMapperMacroFactory {

  def mappingOf[E: Type](using Quotes): Expr[Format[E]] =
    FormatExpressionFactory.build(None)

  def mappingWithConfigOf[E: Type](jsonConfiguration: Expr[JsonConfiguration])(using Quotes): Expr[Format[E]] =
    FormatExpressionFactory.build(Some(jsonConfiguration))

  def readsOf[E: Type](using Quotes): Expr[Reads[E]] =
    ReadsExpressionFactory.build(None)

  def readsWithConfigOf[E: Type](jsonConfiguration: Expr[JsonConfiguration])(using Quotes): Expr[Reads[E]] =
    ReadsExpressionFactory.build(Some(jsonConfiguration))

  def writesOf[E: Type](using Quotes): Expr[Writes[E]] =
    WritesExpressionFactory.build(None)

  def writesWithConfigOf[E: Type](jsonConfiguration: Expr[JsonConfiguration])(using Quotes): Expr[Writes[E]] =
    WritesExpressionFactory.build(Some(jsonConfiguration))

  private def extractTypes(using Quotes)(rootType: quotes.reflect.TypeRepr): NTree[quotes.reflect.TypeRepr] = {
    import quotes.reflect.*

    val enumType = TypeRepr.of[Enumeration]
    val anyValType = TypeRepr.of[AnyVal]
    val jsValueType = TypeRepr.of[JsValue]

    def isSupportedTrait(sym: Symbol): Boolean =
      sym.flags.is(Flags.Trait) && sym.flags.is(Flags.Sealed) && !sym.fullName.startsWith("scala")

    def isCaseClass(aType: TypeRepr): Boolean = {
      val sym = aType.typeSymbol
      sym.isClassDef && sym.flags.is(Flags.Case)
    }

    def isCaseOrTrait(aType: TypeRepr): Boolean = {
      val sym = aType.typeSymbol
      sym.isClassDef && (sym.flags.is(Flags.Case) || isSupportedTrait(sym))
    }

    def symbolToType(sym: Symbol): TypeRepr =
      if (sym.flags.is(Flags.Module)) sym.termRef.widen
      else sym.typeRef

    def extractCaseClassesFromTypeArgs(classType: TypeRepr): List[TypeRepr] = {
      classType.typeArgs.collect {
        case aType if aType <:< jsValueType => Nil
        case argType if isCaseOrTrait(argType) => List(classType, argType)
        case t => extractCaseClassesFromTypeArgs(t)
      }.flatten
    }

    def extractAll(testType: TypeRepr): NTree[TypeRepr] = {
      val typeSym = testType.typeSymbol

      if (isCaseClass(testType)) {
        val fieldTypes = typeSym.caseFields.map(testType.memberType)
        NTree(
          testType,
          fieldTypes.collect {
            case aType if aType <:< jsValueType => Nil
            case aType if isUserAnyVal(aType, anyValType) => List(NTree(aType))
            case aType if enumObjectOf(aType, enumType).isDefined => List(NTree(aType))
            case aType if isCaseOrTrait(aType) => List(extractAll(aType))
            case aType => extractCaseClassesFromTypeArgs(aType).map(extractAll)
          }.flatten
        )
      } else if (isSupportedTrait(typeSym)) {
        NTree(testType, typeSym.children.map(symbolToType).map(extractAll))
      } else NTree.empty
    }

    extractAll(rootType)
  }

  /** True for user-defined AnyVal wrappers, not primitives like Int/Double. */
  private def isUserAnyVal(using Quotes)(aType: quotes.reflect.TypeRepr, anyValType: quotes.reflect.TypeRepr): Boolean = {
    import quotes.reflect.*
    aType <:< anyValType &&
      !aType.typeSymbol.flags.is(Flags.Case) &&
      aType.typeSymbol.isClassDef &&
      !(aType <:< TypeRepr.of[AnyVal] && aType.typeSymbol.fullName.startsWith("scala."))
  }

  /**
   * Resolves the Enumeration module that owns a Value/alias type such as Money.Currency.
   * Prefers the non-dealiased prefix so type aliases keep their declaring object.
   */
  private def enumObjectOf(using Quotes)(
    aType: quotes.reflect.TypeRepr,
    enumType: quotes.reflect.TypeRepr
  ): Option[quotes.reflect.Symbol] = {
    import quotes.reflect.*

    def moduleFromPrefix(prefix: TypeRepr): Option[Symbol] = {
      val widened = prefix.widen
      if (widened <:< enumType && !(widened =:= enumType)) {
        val sym = prefix.termSymbol
        if (sym.exists && sym.flags.is(Flags.Module)) Some(sym)
        else if (widened.typeSymbol.flags.is(Flags.Module)) Some(widened.typeSymbol.companionModule)
        else None
      } else None
    }

    aType match {
      case TypeRef(prefix, _) =>
        moduleFromPrefix(prefix).orElse {
          val dealiased = aType.dealias
          if (dealiased != aType) enumObjectOf(dealiased, enumType) else None
        }
      case TermRef(prefix, _) => moduleFromPrefix(prefix)
      case _ =>
        val owner = aType.typeSymbol.owner
        if (owner.isType && owner.typeRef =:= enumType) None // bare Enumeration.Value — no owning module
        else None
    }
  }

  private def hasImplicit(using Quotes)(target: quotes.reflect.TypeRepr): Boolean = {
    import quotes.reflect.*
    Implicits.search(target) match {
      case _: ImplicitSearchSuccess => true
      case _ => false
    }
  }

  private def callJsonValueFormat[H: Type](using Quotes): Expr[Format[H]] = {
    import quotes.reflect.*
    TypeApply(
      Select.unique(Ref(Symbol.requiredModule("play.api.libs.json.Json")), "valueFormat"),
      List(TypeTree.of[H])
    ).asExprOf[Format[H]]
  }

  private def callJsonValueWrites[H: Type](using Quotes): Expr[Writes[H]] = {
    import quotes.reflect.*
    TypeApply(
      Select.unique(Ref(Symbol.requiredModule("play.api.libs.json.Json")), "valueWrites"),
      List(TypeTree.of[H])
    ).asExprOf[Writes[H]]
  }

  private def callJsonValueReads[H: Type](using Quotes): Expr[Reads[H]] = {
    import quotes.reflect.*
    TypeApply(
      Select.unique(Ref(Symbol.requiredModule("play.api.libs.json.Json")), "valueReads"),
      List(TypeTree.of[H])
    ).asExprOf[Reads[H]]
  }

  private object FormatExpressionFactory {

    def build[E: Type](jsonConfiguration: Option[Expr[JsonConfiguration]])(using Quotes): Expr[Format[E]] = {
      import quotes.reflect.*
      val rootType = TypeRepr.of[E]
      val enumType = TypeRepr.of[Enumeration]
      val toBeImplicit = extractTypes(rootType)
        .filterTree(aType => aType =:= rootType || !hasImplicit(TypeRepr.of[Format].appliedTo(aType)))
        .toList.reverse.distinct.filterNot(_ =:= rootType)

      def nest(remaining: List[TypeRepr]): Expr[Format[E]] = remaining match {
        case Nil => '{ Json.format[E] }
        case head :: tail =>
          head.asType match {
            case '[h] =>
              val givenExpr = implicitFor[h](head, enumType)
              '{
                given Format[h] = $givenExpr
                ${ nest(tail) }
              }
          }
      }

      val body = nest(toBeImplicit)
      jsonConfiguration match {
        case Some(config) => '{ given JsonConfiguration = $config; $body }
        case None => body
      }
    }

    private def implicitFor[H: Type](using Quotes)(
      tpe: quotes.reflect.TypeRepr,
      enumType: quotes.reflect.TypeRepr
    ): Expr[Format[H]] = {
      import quotes.reflect.*
      enumObjectOf(tpe, enumType) match {
        case Some(module) =>
          val ref = Ref(module).asExprOf[Enumeration]
          '{ Json.formatEnum($ref).asInstanceOf[Format[H]] }
        case None if isUserAnyVal(tpe, TypeRepr.of[AnyVal]) =>
          callJsonValueFormat[H]
        case None =>
          '{ Json.format[H] }
      }
    }
  }

  private object WritesExpressionFactory {

    def build[E: Type](jsonConfiguration: Option[Expr[JsonConfiguration]])(using Quotes): Expr[Writes[E]] = {
      import quotes.reflect.*
      val rootType = TypeRepr.of[E]
      val enumType = TypeRepr.of[Enumeration]
      val toBeImplicit = extractTypes(rootType)
        .filterTree(aType => aType =:= rootType || !hasImplicit(TypeRepr.of[Writes].appliedTo(aType)))
        .toList.reverse.distinct.filterNot(_ =:= rootType)

      def nest(remaining: List[TypeRepr]): Expr[Writes[E]] = remaining match {
        case Nil => '{ Json.writes[E] }
        case head :: tail =>
          head.asType match {
            case '[h] =>
              val givenExpr = implicitFor[h](head, enumType)
              '{
                given Writes[h] = $givenExpr
                ${ nest(tail) }
              }
          }
      }

      val body = nest(toBeImplicit)
      jsonConfiguration match {
        case Some(config) => '{ given JsonConfiguration = $config; $body }
        case None => body
      }
    }

    private def implicitFor[H: Type](using Quotes)(
      tpe: quotes.reflect.TypeRepr,
      enumType: quotes.reflect.TypeRepr
    ): Expr[Writes[H]] = {
      import quotes.reflect.*
      enumObjectOf(tpe, enumType) match {
        case Some(module) =>
          val ref = Ref(module).asExprOf[Enumeration]
          '{ Json.formatEnum($ref).asInstanceOf[Writes[H]] }
        case None if isUserAnyVal(tpe, TypeRepr.of[AnyVal]) =>
          callJsonValueWrites[H]
        case None =>
          '{ Json.writes[H] }
      }
    }
  }

  private object ReadsExpressionFactory {

    def build[E: Type](jsonConfiguration: Option[Expr[JsonConfiguration]])(using Quotes): Expr[Reads[E]] = {
      import quotes.reflect.*
      val rootType = TypeRepr.of[E]
      val enumType = TypeRepr.of[Enumeration]
      val toBeImplicit = extractTypes(rootType)
        .filterTree(aType => aType =:= rootType || !hasImplicit(TypeRepr.of[Reads].appliedTo(aType)))
        .toList.reverse.distinct.filterNot(_ =:= rootType)

      def nest(remaining: List[TypeRepr]): Expr[Reads[E]] = remaining match {
        case Nil => '{ Json.reads[E] }
        case head :: tail =>
          head.asType match {
            case '[h] =>
              val givenExpr = implicitFor[h](head, enumType)
              '{
                given Reads[h] = $givenExpr
                ${ nest(tail) }
              }
          }
      }

      val body = nest(toBeImplicit)
      jsonConfiguration match {
        case Some(config) => '{ given JsonConfiguration = $config; $body }
        case None => body
      }
    }

    private def implicitFor[H: Type](using Quotes)(
      tpe: quotes.reflect.TypeRepr,
      enumType: quotes.reflect.TypeRepr
    ): Expr[Reads[H]] = {
      import quotes.reflect.*
      enumObjectOf(tpe, enumType) match {
        case Some(module) =>
          val ref = Ref(module).asExprOf[Enumeration]
          '{ Reads.enumNameReads($ref).asInstanceOf[Reads[H]] }
        case None if isUserAnyVal(tpe, TypeRepr.of[AnyVal]) =>
          callJsonValueReads[H]
        case None =>
          '{ Json.reads[H] }
      }
    }
  }
}

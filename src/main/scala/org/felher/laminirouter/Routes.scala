package org.felher.laminouter

import org.scalajs.dom.URL
import org.scalajs.dom.URLSearchParams
import scala.quoted.*

/**
  * A combined codec for encoding and decoding the whole routes data structure.
  *
  * You should probably not use this type directly. It is summoned by the router though.
  */
trait Routes[A]:
  def decode(url: URL): Option[A]
  def encode(base: URL, value: A): URL

object Routes:

  inline given [A]: Routes[A] = ${ impl[A] }

  private def impl[A](using q: Quotes, t: Type[A]): Expr[Routes[A]] =
    new MacroImpl[A]().impl

  private class MacroImpl[A](using q: Quotes, t: Type[A]):
    import q.reflect.*
    private given Printer[Tree] = Printer.TreeStructure

    def impl: Expr[Routes[A]] =
      import q.reflect.*

      val children = TypeRepr.of[A].typeSymbol.children

      val childDecoders = children.map(makeChildDecoder)
      val decoder       = assembleDecoder(childDecoders)

      val childEncoders = children.map(buildEncoder)
      val encoder       = assembleEncoder(childEncoders)

      '{
        new Routes[A]:
          def decode(url: URL): Option[A]      = ${ decoder }.apply(url)
          def encode(base: URL, value: A): URL = ${ encoder }.apply(base, value)
      }

    private def makeChildDecoder(child: q.reflect.Symbol): Expr[URL => Option[A]] =
      import q.reflect.*

      child.tree match
        case ValDef(name, _, _) => buildDecoderForValDef(name, child)

        case ClassDef(_, DefDef(_, List(params: TermParamClause), _, _), _, _, _) =>
          buildDecoderForPathOnly(child, params)

        case ClassDef(_, DefDef(_, List(pathParams: TermParamClause, searchParams: TermParamClause), _, _), _, _, _) =>
          buildDecoderForPathAndSearch(child, pathParams, searchParams)

        case _ =>
          println(child.tree.show)
          sys.error("unsupported tree")

    private def buildDecoderForValDef(name: String, child: Symbol): Expr[URL => Option[A]] =
      val nameExpr  = Expr(name.decapitalize)
      val valueExpr = Ref(child).asExprOf[A]
      '{ (url: URL) =>
        if url.pathname == "/" + $nameExpr then Some($valueExpr)
        else None
      }

    private def buildDecoderForPathOnly(child: Symbol, params: TermParamClause): Expr[URL => Option[A]] =
      val routeName                        = Expr(child.name.decapitalize)
      val valDefs: List[ValDef]            = params.params
      val codecsExpr: Expr[List[Codec[?]]] = Expr.ofList(valDefs.map(summonCodec))

      def instantiate(values: Expr[List[?]]): Expr[A] =
        Apply(
          Select(
            New(TypeIdent(child)),
            child.primaryConstructor
          ),
          valDefs.zipWithIndex.map: (vd, i) =>
            vd.tpt.tpe.asType match
              case '[t] => '{ $values(${ Expr(i) }).asInstanceOf[t] }.asTerm
        ).asExprOf[A]

      '{ (url: URL) =>
        val codec: List[Codec[?]] = $codecsExpr
        Routes
          .getValueSegments(url, $routeName, ${ Expr(valDefs.length) })
          .flatMap: valueSegments =>
            val maybeValues: List[Option[?]] = codec.zip(valueSegments).map((codec, value) => codec.decode(value))

            if maybeValues.exists(_.isEmpty) then None
            else
              val values = maybeValues.map(_.get)
              Some(${ instantiate('values) })
      }

    private def buildDecoderForPathAndSearch(
        child: Symbol,
        pathParams: TermParamClause,
        searchParams: TermParamClause
    ): Expr[URL => Option[A]] =
      val routeName = Expr(child.name.decapitalize)

      val pathValDefs: List[ValDef]            = pathParams.params
      val pathCodecsExpr: Expr[List[Codec[?]]] = Expr.ofList(pathValDefs.map(summonCodec))

      val searchValDefs: List[ValDef]                = searchParams.params
      val searchCodecsExpr: Expr[List[Codec[?]]]     = Expr.ofList(searchValDefs.map(summonCodecUnwrapOption))
      val searchKeysExpr: Expr[List[String]]         = Expr(searchValDefs.map(_.name))
      val optionalSearchKeysExpr: Expr[List[String]] = Expr.ofList:
        searchValDefs.flatMap: vd =>
          vd.tpt.tpe.asType match
            case '[Option[t]] => List(Expr(vd.name))
            case _            => Nil

      def instantiate(pathValues: Expr[List[?]], searchValues: Expr[Map[String, ?]]): Expr[A] =
        Apply(
          Apply(
            Select(
              New(TypeIdent(child)),
              child.primaryConstructor
            ),
            pathValDefs.zipWithIndex.map: (vd, i) =>
              vd.tpt.tpe.asType match
                case '[t] => '{ $pathValues(${ Expr(i) }).asInstanceOf[t] }.asTerm
          ),
          searchValDefs.map: vd =>
            vd.tpt.tpe.asType match
              case '[Option[t]] => '{ $searchValues.get(${ Expr(vd.name) }).asInstanceOf[Option[t]] }.asTerm
              case '[t]         => '{ $searchValues(${ Expr(vd.name) }).asInstanceOf[t] }.asTerm
        ).asExprOf[A]

      '{ (url: URL) =>
        val pathCodecs: List[Codec[?]]   = $pathCodecsExpr
        val searchKeys: List[String]     = $searchKeysExpr
        val searchCodecs: List[Codec[?]] = $searchCodecsExpr

        Routes
          .getValueSegments(url, $routeName, ${ Expr(pathValDefs.length) })
          .flatMap: valueSegments =>
            val maybePathValues: List[Option[?]] =
              pathCodecs.zip(valueSegments).map((codec, value) => codec.decode(value))

            if maybePathValues.exists(_.isEmpty) then None
            else
              val pathValues                         = maybePathValues.map(_.get)
              val searchParams                       = url.searchParams
              val optionalSearchKeys: List[String]   = $optionalSearchKeysExpr
              val maybeSearchValues: List[Option[?]] = searchKeys
                .zip(searchCodecs)
                .map: (key, codec) =>
                  Option(searchParams.get(key)).flatMap(codec.decode)

              val searchValues = searchKeys
                .zip(maybeSearchValues)
                .collect:
                  case (key, Some(value)) => key -> value
                .toMap
              if (searchKeys.toSet -- optionalSearchKeys.toSet).exists(key => !searchValues.contains(key)) then None
              else Some(${ instantiate('pathValues, 'searchValues) })
      }

    private def summonCodec(vd: ValDef): Expr[Codec[?]] =
      val codecType = TypeRepr.of[Codec].appliedTo(vd.tpt.tpe)
      Implicits.search(codecType) match
        case _: ImplicitSearchFailure       =>
          sys.error(s"Cannot find implicit codec: ${codecType.show}")
        case success: ImplicitSearchSuccess =>
          success.tree.asExprOf[Codec[?]]

    private def summonCodecUnwrapOption(vd: ValDef): Expr[Codec[?]] =
      val codecType = TypeRepr
        .of[Codec]
        .appliedTo(vd.tpt.tpe.asType match
          case '[Option[t]] => TypeRepr.of[t]
          case _            => vd.tpt.tpe
        )
      Implicits.search(codecType) match
        case _: ImplicitSearchFailure       =>
          sys.error(s"Cannot find implicit codec: ${codecType.show}")
        case success: ImplicitSearchSuccess =>
          success.tree.asExprOf[Codec[?]]

    private def assembleDecoder(decoders: List[Expr[URL => Option[A]]]): Expr[URL => Option[A]] =
      locally(t)
      val decoderList = Expr.ofList[URL => Option[A]](decoders)
      '{
        val zero = (u: URL) => Option.empty[A]
        $decoderList.foldLeft(zero)((acc, decoder) => (u: URL) => acc(u).orElse(decoder(u)))
      }

    private def buildEncoder(child: Symbol): Expr[(URL, ?) => URL] =
      import q.reflect.*

      child.tree match
        case ValDef(name, _, _) => buildEncoderForValDef(name)

        case ClassDef(_, DefDef(_, List(params: TermParamClause), _, _), _, _, _) =>
          buildEncoderForPathOnly(child, params)

        case ClassDef(_, DefDef(_, List(pathParams: TermParamClause, searchParams: TermParamClause), _, _), _, _, _) =>
          buildEncoderForPathAndSearch(child, pathParams, searchParams)

        case _ =>
          println(child.tree.show)
          sys.error("unsupported tree")

    private def buildEncoderForValDef(name: String): Expr[(URL, ?) => URL] =
      '{ (base: URL, _: Any) =>
        base.pathname = "/" + ${ Expr(name.decapitalize) }
        base.search = ""
        base
      }

    private def buildEncoderForPathOnly(child: Symbol, params: TermParamClause): Expr[(URL, ?) => URL] =
      val routeName  = Expr(child.name.decapitalize)
      val valDefs    = params.params
      val codecsExpr = Expr.ofList(valDefs.map(summonCodec))

      child.typeRef.asType match
        case '[t] =>
          '{ (base: URL, value: t) =>
            base.pathname = "/" + ${ routeName } + ${ buildPath[t]('value, valDefs, codecsExpr) }
            base.search = ""
            base
          }

    private def buildEncoderForPathAndSearch(
        child: Symbol,
        pathParams: TermParamClause,
        searchParams: TermParamClause
    ): Expr[(URL, ?) => URL] =
      val routeName = Expr(child.name.decapitalize)

      val pathValDefs    = pathParams.params
      val pathCodecsExpr = Expr.ofList(pathValDefs.map(summonCodec))

      val searchValDefs    = searchParams.params
      val searchCodecsExpr = Expr.ofList(searchValDefs.map(summonCodecUnwrapOption))

      child.typeRef.asType match
        case '[t] =>
          '{ (base: URL, value: t) =>
            base.pathname = "/" + ${ routeName } + ${ buildPath[t]('value, pathValDefs, pathCodecsExpr) }
            base.search = ${ buildSearch('value, searchValDefs, searchCodecsExpr) }.toString
            base
          }

    private def buildPath[T](value: Expr[T], vals: List[ValDef], codecsExpr: Expr[List[Codec[?]]])(using
        Type[T]
    ): Expr[String] =
      val parts = vals.zipWithIndex.map: (vd, i) =>
        val select = Select.unique(value.asTerm, vd.name).asExpr
        vd.tpt.tpe.asType match
          case '[t] =>
            '{
              val value  = $select
              val codecs = $codecsExpr

              scalajs.js.URIUtils.encodeURIComponent(codecs(${
                Expr(i)
              }).asInstanceOf[Codec[t]].encode(value.asInstanceOf[t]))
            }

      val prefix = Expr(if parts.isEmpty then "" else "/")
      '{ $prefix + ${ Expr.ofList(parts) }.mkString("/") }

    private def buildSearch[T](value: Expr[T], vals: List[ValDef], codecsExpr: Expr[List[Codec[?]]])(using
        Type[T]
    ): Expr[URLSearchParams] =
      val parts: List[Expr[Option[(String, String)]]] =
        vals.zipWithIndex.map: (vd, i) =>
          val select = Select.unique(value.asTerm, vd.name).asExpr
          vd.tpt.tpe.asType match
            case '[Option[t]] =>
              '{
                val value  = $select.asInstanceOf[Option[t]]
                val codecs = $codecsExpr

                value.map: value =>
                  val encoded = scalajs.js.URIUtils.encodeURIComponent(codecs(${
                    Expr(i)
                  }).asInstanceOf[Codec[t]].encode(value.asInstanceOf[t]))

                  ${ Expr(vd.name) } -> encoded
              }
            case '[t]         =>
              '{
                val value  = $select
                val codecs = $codecsExpr

                val encoded = scalajs.js.URIUtils.encodeURIComponent(codecs(${
                  Expr(i)
                }).asInstanceOf[Codec[t]].encode(value.asInstanceOf[t]))

                Some(${ Expr(vd.name) } -> encoded)
              }

      '{
        val params = new URLSearchParams()
        ${ Expr.ofList(parts) }.flatten.foldLeft(params): (params, pair) =>
          val (key, value) = pair
          params.append(key, value)
          params
      }

    private def assembleEncoder(encoders: List[Expr[(URL, ?) => URL]]): Expr[(URL, A) => URL] =
      val encoderList = Expr.ofList[(URL, ?) => URL](encoders)
      '{ (base: URL, value: A) =>
        val encoderIndex = ${ Select.unique('value.asTerm, "ordinal").asExprOf[Int] }
        $encoderList(encoderIndex).asInstanceOf[(URL, Any) => URL](base, value)
      }

  private def getValueSegments(url: URL, routeName: String, numSegments: Int): Option[List[String]] =
    def checkAndStripPrefix(s: String): Option[String] =
      val prefix = "/" + routeName + (if numSegments > 0 then "/" else "")
      if s.startsWith(prefix) then Some(s.stripPrefix(prefix))
      else None

    def getSegments(s: String): Option[List[String]] =
      val segments = s.split("/").toList

      if numSegments == 0 && s.isEmpty then Some(Nil)
      else if segments.length == numSegments then Some(segments)
      else None

    def uriDecode(ss: List[String]): Option[List[String]] =
      try Some(ss.map(scalajs.js.URIUtils.decodeURIComponent))
      catch case _: Throwable => None

    checkAndStripPrefix(url.pathname)
      .flatMap(getSegments)
      .flatMap(uriDecode)
      .flatMap(uriDecode)

  extension (s: String)
    def decapitalize: String =
      if s.nonEmpty && s.head.isUpper then s.tail.prepended(s.head.toLower)
      else s

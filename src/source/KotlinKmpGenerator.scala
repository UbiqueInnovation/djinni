package djinni

import djinni.ast._
import djinni.generatorTools._
import djinni.meta._
import djinni.syntax.Loc
import djinni.writer.IndentWriter
import java.io.File

import scala.collection.mutable

class KotlinKmpGenerator(spec: Spec) extends Generator(spec) {

  private lazy val wasm = new KotlinKmpWasmGenerator(spec, this)
  private val kotlinMarshal = new KotlinMarshal(spec)
  private val objcMarshal = new ObjcMarshal(spec)
  private val iosModule: String = spec.kotlinKmpIosModule.getOrElse(spec.moduleName)
  private val kmpBridgePrefix: String = spec.kotlinKmpBridgePrefix.getOrElse("")
  private val kmpObjcNamePrefix: String =
    spec.kotlinKmpObjcNamePrefix.getOrElse(kmpBridgePrefix)
  private val resultExpr = "result"
  private val syntheticFile = new File("<generated>")

  private def syntheticIdent(name: String): Ident = Ident(name, syntheticFile, Loc(syntheticFile, 0, 0))

  override def generate(idl: Seq[TypeDecl]) {
    spec.kotlinKmpWasmOutFolder.foreach(wasm.generateRuntime)
    spec.kotlinKmpJsOutFolder.foreach(generateJsRuntime)
    for (td <- selectDecls(idl)) {
      td.body match {
        case r: Record => generateRecord(td, r)
        case e: Enum => generateEnum(td, e)
        case i: Interface => generateInterfaceDecl(td, i)
        case _ =>
      }
    }
  }

  override def generateEnum(origin: String, ident: Ident, doc: Doc, e: Enum) {}

  override def generateRecord(origin: String, ident: Ident, doc: Doc, params: Seq[TypeParam], r: Record) {}

  override def generateInterface(origin: String, ident: Ident, doc: Doc, typeParams: Seq[TypeParam], i: Interface) {}

  private def selectDecls(idl: Seq[TypeDecl]): Seq[TypeDecl] = {
    val byName = mutable.LinkedHashMap[String, TypeDecl]()
    for (td <- idl) {
      if (td.isInstanceOf[ExternTypeDecl]) {
        // Skip externs to avoid overwriting real definitions from their owning IDL file.
      } else {
        td.body match {
          case _: Record | _: Enum | _: Interface =>
            val name = canonicalName(td)
            byName.get(name) match {
              case Some(existing) =>
                val preferNew = td.isInstanceOf[InternTypeDecl] && !existing.isInstanceOf[InternTypeDecl]
                if (preferNew) {
                  byName(name) = td
                }
              case None =>
                byName(name) = td
            }
          case _ =>
        }
      }
    }
    byName.values.toSeq
  }

  private def canonicalName(td: TypeDecl): String = td match {
    case e: ExternTypeDecl => YamlGenerator.metaFromYaml(e).name
    case _ => td.ident.name
  }

  private[djinni] def kmpTypeName(td: TypeDecl): String = kmpBridgePrefix + idJava.ty(canonicalName(td))
  private def kmpObjcName(td: TypeDecl): String = kmpObjcNamePrefix + idJava.ty(canonicalName(td))
  private def externKmpPackage(e: MExtern): String = e.kmp.pkg
  private def externKmpBridgePrefix(e: MExtern): String =
    if (e.kmp.bridgePrefix.nonEmpty) e.kmp.bridgePrefix else kmpBridgePrefix
  private def externKmpTypeName(e: MExtern): String = externKmpBridgePrefix(e) + idJava.ty(e.name)
  private def externKmpFqTypeName(e: MExtern): String = {
    val typeName = externKmpTypeName(e)
    if (externKmpPackage(e).nonEmpty) s"${externKmpPackage(e)}.$typeName" else typeName
  }

  private def typeParamDecl(params: Seq[TypeParam]): String = {
    if (params.isEmpty) "" else params.map(p => idJava.typeParam(p.ident.name)).mkString("<", ", ", ">")
  }

  private def typeParamUse(params: Seq[TypeParam]): String = typeParamDecl(params)

  private def shortTypeName(typeName: String): String = {
    val idx = typeName.lastIndexOf('.')
    if (idx < 0) typeName else typeName.substring(idx + 1)
  }

  private def objcSystemModule(header: String): Option[String] = {
    if (header.startsWith("<")) {
      if (header.contains("Foundation/")) Some("platform.Foundation")
      else if (header.contains("CoreGraphics/")) Some("platform.CoreGraphics")
      else if (header.contains("CoreLocation/")) Some("platform.CoreLocation")
      else if (header.contains("UIKit/")) Some("platform.UIKit")
      else if (header.contains("Metal/")) Some("platform.Metal")
      else None
    } else None
  }

  private def iosActualTypeNameAndImport(td: TypeDecl): (String, Option[String]) = {
    val objcType = td match {
      case e: ExternTypeDecl => YamlGenerator.metaFromYaml(e).objc.typename
      case _ => objcMarshal.typename(td.ident, td.body)
    }
    val objcTypeWithProtocol = if (isObjcProtocol(td)) s"${objcType}Protocol" else objcType
    val importPath =
      if (iosModule.isEmpty) None
      else Some(s"$iosModule.$objcTypeWithProtocol")
    (objcTypeWithProtocol, importPath)
  }

  private def iosPlatformImportsForTypes(types: Seq[MExpr]): Seq[String] = {
    def imports(tm: MExpr): Set[String] = {
      val nested = tm.args.flatMap(imports).toSet
      tm.base match {
        case d: MDef =>
          val objcType = objcMarshal.typename(syntheticIdent(d.name), d.body)
          val objcTypeWithProtocol = d.body match {
            case i: Interface if useProtocol(i.ext, spec) => s"${objcType}Protocol"
            case _ => objcType
          }
          if (iosModule.isEmpty) nested else nested + s"$iosModule.$objcTypeWithProtocol"
        case e: MExtern =>
          objcSystemModule(e.objc.header) match {
            case Some(_) => nested
            case None =>
              val objcType = e.body match {
                case i: Interface if useProtocol(i.ext, spec) => s"${e.objc.typename}Protocol"
                case _ if e.objc.protocol => s"${e.objc.typename}Protocol"
                case _ => e.objc.typename
              }
              val resolvedModule = if (e.objc.module.nonEmpty) e.objc.module else iosModule
              if (resolvedModule.isEmpty) nested else nested + s"$resolvedModule.$objcType"
          }
        case _ => nested
      }
    }

    types.flatMap(imports).toSet.toSeq.sorted
  }

  private def iosPlatformImportsForRecord(td: TypeDecl, r: Record): Seq[String] =
    (iosActualTypeNameAndImport(td)._2.toSeq ++ iosPlatformImportsForTypes(r.fields.map(_.ty.resolved))).distinct.sorted

  private def iosPlatformImportsForInterface(td: TypeDecl, i: Interface): Seq[String] = {
    val methodTypes = i.methods.flatMap { m =>
      m.params.map(_.ty.resolved) ++ m.ret.map(_.resolved).toSeq
    }
    (iosActualTypeNameAndImport(td)._2.toSeq ++ iosPlatformImportsForTypes(methodTypes ++ i.consts.map(_.ty.resolved))).distinct.sorted
  }

  private def iosPlatformImportsForEnum(td: TypeDecl): Seq[String] =
    iosActualTypeNameAndImport(td)._2.toSeq

  private def isEnum(tm: MExpr): Boolean = tm.base match {
    case d: MDef => d.defType == DEnum
    case e: MExtern => e.defType == DEnum
    case _ => false
  }

  private def isRecord(tm: MExpr): Boolean = tm.base match {
    case d: MDef => d.defType == DRecord
    case e: MExtern => e.defType == DRecord
    case _ => false
  }

  private def objcBox(p: MPrimitive, expr: String): String = p.kName match {
    case "Byte" => s"platform.Foundation.NSNumber(char = $expr)"
    case "Short" => s"platform.Foundation.NSNumber(short = $expr)"
    case "Int" => s"platform.Foundation.NSNumber(int = $expr)"
    case "Long" => s"platform.Foundation.NSNumber(longLong = $expr)"
    case "Float" => s"platform.Foundation.NSNumber(float = $expr)"
    case "Double" => s"platform.Foundation.NSNumber(double = $expr)"
    case "Boolean" => s"platform.Foundation.NSNumber(bool = $expr)"
    case _ => expr
  }

  private def objcUnbox(p: MPrimitive, expr: String): String = p.kName match {
    case "Byte" => s"($expr as platform.Foundation.NSNumber).charValue"
    case "Short" => s"($expr as platform.Foundation.NSNumber).shortValue"
    case "Int" => s"($expr as platform.Foundation.NSNumber).intValue"
    case "Long" => s"($expr as platform.Foundation.NSNumber).longLongValue"
    case "Float" => s"($expr as platform.Foundation.NSNumber).floatValue"
    case "Double" => s"($expr as platform.Foundation.NSNumber).doubleValue"
    case "Boolean" => s"($expr as platform.Foundation.NSNumber).boolValue"
    case _ => expr
  }

  private def writeKotlinFile(folder: File, fileName: String, origin: String, f: IndentWriter => Unit, extraImports: Seq[String] = Seq.empty): Unit = {
    createFile(folder, fileName, (w: IndentWriter) => {
      w.wl("// AUTOGENERATED FILE - DO NOT MODIFY!")
      w.wl("// This file was generated by Djinni from " + origin)
      w.wl
      if (isInKmpIosOutFolder(folder)) {
        w.wl("@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)")
        w.wl("""@file:Suppress("RedundantCast", "RedundantCallOfConversionMethod", "UNCHECKED_CAST", "USELESS_CAST")""")
        w.wl
      }
      spec.kotlinKmpPackage.foreach(s => w.wl(s"package $s"))
      if (spec.kotlinKmpPackage.isDefined) {
        w.wl
      }
      extraImports.distinct.sorted.foreach(imp => w.wl(s"import $imp"))
      if (extraImports.nonEmpty) {
        w.wl
      }
      f(w)
    })
  }

  private def isInKmpIosOutFolder(folder: File): Boolean = {
    spec.kotlinKmpIosOutFolder.exists { root =>
      val folderPath = folder.getCanonicalFile.toPath
      val rootPath = root.getCanonicalFile.toPath
      folderPath.startsWith(rootPath)
    }
  }

  private def conversionImportsForTypes(types: Seq[MExpr]): Seq[String] = {
    val currentKmpPackage = spec.kotlinKmpPackage.getOrElse("")
    def externPackages(tm: MExpr): Set[String] = {
      val nested = tm.args.flatMap(externPackages).toSet
      tm.base match {
        case e: MExtern if externKmpPackage(e).nonEmpty && externKmpPackage(e) != currentKmpPackage =>
          nested + externKmpPackage(e)
        case _ => nested
      }
    }

    types
      .flatMap(externPackages)
      .toSet
      .toSeq
      .sorted
      .flatMap(pkg => Seq(s"$pkg.asKmp", s"$pkg.asPlatform"))
  }

  private def conversionImportsForRecord(r: Record): Seq[String] =
    conversionImportsForTypes(r.fields.map(_.ty.resolved))

  private def hasBinaryType(types: Seq[MExpr]): Boolean = {
    def check(tm: MExpr): Boolean = tm.base match {
      case MBinary => true
      case _ => tm.args.exists(check)
    }
    types.exists(check)
  }

  private val binaryCinteropImports: Seq[String] = Seq(
    "kotlinx.cinterop.addressOf",
    "kotlinx.cinterop.usePinned",
    "platform.Foundation.dataWithBytes",
    "platform.posix.memcpy"
  )

  private def conversionImportsForInterface(i: Interface): Seq[String] = {
    val methodTypes = i.methods.flatMap { m =>
      m.params.map(_.ty.resolved) ++ m.ret.map(_.resolved).toSeq
    }
    conversionImportsForTypes(methodTypes ++ i.consts.map(_.ty.resolved))
  }

  private def generateRecord(td: TypeDecl, r: Record): Unit = {
    val name = kmpTypeName(td)
    val typeParams = typeParamDecl(td.params)
    val origin = td.origin
    val conversionImports = conversionImportsForRecord(r)

    spec.kotlinKmpWasmOutFolder.foreach(folder => wasm.generateRecord(folder, td, r))
    spec.kotlinKmpJsOutFolder.foreach(folder => generateJsRecord(folder, td, r))
    spec.kotlinKmpCommonOutFolder.foreach(folder => {
      writeKotlinFile(folder, s"$name.kt", origin, w => {
        w.w(s"expect class $name$typeParams(")
        if (r.fields.nonEmpty) {
          w.wl
          w.increase()
          for (f <- r.fields) {
            w.wl(s"${idJava.field(f.ident)}: ${kmpConstructorFieldType(f.ty.resolved)},")
          }
          w.decrease()
          w.w(")")
        } else {
          w.w(")")
        }
        w.wl(" {")
        w.increase()
        for (f <- r.fields) {
          w.wl(s"val ${idJava.field(f.ident)}: ${kmpFieldType(f.ty.resolved)}")
        }
        w.decrease()
        w.wl("}")
      })
    })

    spec.kotlinKmpAndroidOutFolder.foreach(folder => {
      val actualType = androidActualTypename(td) + typeParamUse(td.params)
      val useRecordWrapper = needsAndroidRecordWrapper(td, r)
      writeKotlinFile(folder, s"$name.kt", origin, w => {
        if (useRecordWrapper) {
          w.w(s"actual class $name$typeParams actual public constructor(")
          if (r.fields.nonEmpty) {
            w.wl
            w.increase()
            for (f <- r.fields) {
              w.wl(s"${idJava.field(f.ident)}: ${kmpConstructorFieldType(f.ty.resolved)},")
            }
            w.decrease()
            w.w(")")
          } else {
            w.w(")")
          }
          w.wl(" {")
          w.increase()
          for (f <- r.fields) {
            val fieldName = idJava.field(f.ident)
            w.wl(s"actual val $fieldName: ${kmpFieldType(f.ty.resolved)} = ${kmpConstructorFieldInitializer(f.ty.resolved, fieldName)}")
          }
          w.decrease()
          w.wl("}")
          w.wl
          w.wl(s"public fun $name$typeParams.asPlatform(): $actualType = $actualType(")
          w.increase()
          for (f <- r.fields) {
            val fieldName = idJava.field(f.ident)
            w.wl(s"$fieldName = ${toPlatformExpr(f.ty.resolved, fieldName, isAndroid = true)},")
          }
          w.decrease()
          w.wl(")")
          w.wl(s"public fun $actualType.asKmp(): $name$typeParams = $name(")
          w.increase()
          for (f <- r.fields) {
            val fieldName = idJava.field(f.ident)
            val platformField = s"this.$fieldName"
            w.wl(s"$fieldName = ${fromPlatformExpr(f.ty.resolved, platformField, isAndroid = true)},")
          }
          w.decrease()
          w.wl(")")
        } else {
          w.wl(s"actual typealias $name$typeParams = $actualType")
          w.wl
          w.wl(s"public fun $name$typeParams.asPlatform(): $actualType = this")
          w.wl(s"public fun $actualType.asKmp(): $name$typeParams = this")
        }
      }, extraImports = conversionImports)
    })

    spec.kotlinKmpIosOutFolder.foreach(folder => {
      val actualType = iosActualTypename(td) + typeParamUse(td.params)
      val objcName = kmpObjcName(td)
      val iosImports = iosPlatformImportsForRecord(td, r)
      writeKotlinFile(folder, s"$name.kt", origin, w => {
        w.wl("import kotlin.experimental.ExperimentalObjCName")
        w.wl("import kotlin.native.ObjCName")
        w.wl
        w.wl("@OptIn(ExperimentalObjCName::class)")
        w.wl(s"""@ObjCName("$objcName", exact = true)""")
        w.w(s"actual class $name$typeParams actual public constructor(")
        if (r.fields.nonEmpty) {
          w.wl
          w.increase()
          for (f <- r.fields) {
            w.wl(s"${idJava.field(f.ident)}: ${kmpConstructorFieldType(f.ty.resolved)},")
          }
          w.decrease()
          w.w(")")
        } else {
          w.w(")")
        }
        w.wl(" {")
        w.increase()
        for (f <- r.fields) {
          val fieldName = idJava.field(f.ident)
          w.wl(s"actual val $fieldName: ${kmpFieldType(f.ty.resolved)} = ${kmpConstructorFieldInitializer(f.ty.resolved, fieldName)}")
        }
        w.decrease()
        w.wl("}")
        w.wl
        w.wl(s"public fun $name$typeParams.asPlatform(): $actualType = $actualType(")
        w.increase()
        for (f <- r.fields) {
          val fieldName = idJava.field(f.ident)
          w.wl(s"$fieldName = ${toPlatformExpr(f.ty.resolved, fieldName, isAndroid = false)},")
        }
        w.decrease()
        w.wl(")")
        w.wl(s"public fun $actualType.asKmp(): $name$typeParams = $name(")
        w.increase()
        for (f <- r.fields) {
          val fieldName = idJava.field(f.ident)
          val platformField = s"this.$fieldName"
          w.wl(s"$fieldName = ${fromPlatformExpr(f.ty.resolved, platformField, isAndroid = false)},")
        }
        w.decrease()
        w.wl(")")
      }, extraImports = conversionImports ++ iosImports ++ (if (hasBinaryType(r.fields.map(_.ty.resolved))) binaryCinteropImports else Seq.empty))
    })
  }

  private def generateEnum(td: TypeDecl, e: Enum): Unit = {
    val name = kmpTypeName(td)
    val origin = td.origin

    spec.kotlinKmpWasmOutFolder.foreach(folder => wasm.generateEnum(folder, td, e))
    spec.kotlinKmpJsOutFolder.foreach(folder => generateJsEnum(folder, td, e))
    spec.kotlinKmpCommonOutFolder.foreach(folder => {
      writeKotlinFile(folder, s"$name.kt", origin, w => {
        w.w(s"expect enum class $name")
        w.braced {
          for (o <- normalEnumOptions(e)) {
            w.wl(idJava.enum(o.ident) + ",")
          }
        }
      })
    })

    spec.kotlinKmpAndroidOutFolder.foreach(folder => {
      val actualType = androidActualTypename(td)
      writeKotlinFile(folder, s"$name.kt", origin, w => {
        w.wl(s"actual typealias $name = $actualType")
        w.wl
        w.wl(s"public fun $name.asPlatform(): $actualType = this")
        w.wl(s"public fun $actualType.asKmp(): $name = this")
      })
    })

    spec.kotlinKmpIosOutFolder.foreach(folder => {
      val actualType = iosActualTypename(td)
      val objcName = kmpObjcName(td)
      val iosImports = iosPlatformImportsForEnum(td)
      writeKotlinFile(folder, s"$name.kt", origin, w => {
        w.wl("import kotlin.experimental.ExperimentalObjCName")
        w.wl("import kotlin.native.ObjCName")
        w.wl
        w.wl("@OptIn(ExperimentalObjCName::class)")
        w.wl(s"""@ObjCName("$objcName", exact = true)""")
        w.wl(s"actual enum class $name(internal val platformValue: Long) {")
        w.increase()
        val normal = normalEnumOptions(e)
        for ((o, idx) <- normal.zipWithIndex) {
          val value =
            if (e.flags) s"(1L shl $idx)"
            else s"${idx}L"
          w.wl(s"${idJava.enum(o.ident)}($value),")
        }
        w.wl(";")
        w.wl
        w.wl("companion object {")
        w.increase()
        w.wl(s"public fun fromPlatform(value: $actualType): $name {")
        w.increase()
        w.wl("val raw: Long = value")
        w.wl("return when (raw) {")
        w.increase()
        for ((o, idx) <- normal.zipWithIndex) {
          val value =
            if (e.flags) s"(1L shl $idx)"
            else s"${idx}L"
          w.wl(s"$value -> $name.${idJava.enum(o.ident)}")
        }
        w.wl(s"""else -> throw IllegalArgumentException("Unknown $name value: " + raw)""")
        w.decrease()
        w.wl("}")
        w.decrease()
        w.wl("}")
        w.decrease()
        w.wl("}")
        w.decrease()
        w.wl("}")
        w.wl
        w.wl(s"public fun $name.asPlatform(): $actualType = platformValue")
      }, extraImports = iosImports)
    })
  }

  private def generateInterfaceDecl(td: TypeDecl, i: Interface): Unit = {
    val name = kmpTypeName(td)
    val origin = td.origin
    val typeParams = typeParamDecl(td.params)
    val typeParamsUse = typeParamUse(td.params)
    val objcIsProtocol = td match {
      case e: ExternTypeDecl => YamlGenerator.metaFromYaml(e).objc.protocol
      case _ => useProtocol(i.ext, spec)
    }
    val hasStatics = i.methods.exists(_.static) || i.consts.nonEmpty
    val kmpImplementable = objcIsProtocol && i.ext.java && i.ext.objc
    val classKind = !kmpImplementable || hasStatics
    val conversionImports = conversionImportsForInterface(i)

    spec.kotlinKmpWasmOutFolder.foreach(folder => wasm.generateInterface(folder, td, i, classKind))
    spec.kotlinKmpJsOutFolder.foreach(folder => generateJsInterface(folder, td, i, classKind))
    spec.kotlinKmpCommonOutFolder.foreach(folder => {
      writeKotlinFile(folder, s"$name.kt", origin, w => {
        if (classKind) {
          w.w(s"expect class $name$typeParams constructor(nativeHandle: Any)")
        } else {
          w.w(s"expect interface $name$typeParams")
        }
        w.braced {
          for (m <- i.methods if !m.static) {
            w.wl
            val params = methodParams(m)
            val retType = kmpReturnType(m.ret)
            val retSuffix = if (retType == "Unit") "" else s": $retType"
            w.wl(s"fun ${idJava.method(m.ident)}(${params.mkString(", ")})$retSuffix")
          }
          if (classKind && (i.consts.nonEmpty || i.methods.exists(_.static))) {
            w.wl
            w.wl("companion object").braced {
              for (c <- i.consts) {
                val constType = kmpFieldType(c.ty.resolved)
                w.wl(s"val ${idJava.const(c.ident)}: $constType")
              }
              for (m <- i.methods if m.static) {
                w.wl
                val params = methodParams(m)
                val retType = kmpReturnType(m.ret)
                val retSuffix = if (retType == "Unit") "" else s": $retType"
                w.wl(s"fun ${idJava.method(m.ident)}(${params.mkString(", ")})$retSuffix")
              }
            }
          }
        }
      })
    })

    spec.kotlinKmpAndroidOutFolder.foreach(folder => {
      writeKotlinFile(folder, s"$name.kt", origin, w => {
        val platformTypeBaseFq = androidActualTypename(td)
        val platformTypeBase = shortTypeName(platformTypeBaseFq)
        val platformType = platformTypeBase + typeParamsUse
        w.wl(s"import $platformTypeBaseFq")
        w.wl
        if (classKind) {
          w.wl(s"actual class $name$typeParams actual public constructor(")
          w.increase()
          w.wl("nativeHandle: Any,")
          w.decrease()
          w.wl(") {")
          w.increase()
          w.wl("internal val nativeHandle: Any = nativeHandle")
          w.wl(s"private val native = nativeHandle as $platformType")
          for (m <- i.methods if !m.static) {
            w.wl
            val params = methodParams(m)
            val retType = kmpReturnType(m.ret)
            val retSuffix = if (retType == "Unit") "" else s": $retType"
            w.wl(s"actual fun ${idJava.method(m.ident)}(${params.mkString(", ")})$retSuffix {")
            w.increase()
            val args = m.params.map(p => toPlatformExpr(p.ty.resolved, idJava.local(p.ident), isAndroid = true))
            val call = s"native.${idJava.method(m.ident)}(${args.mkString(", ")})"
            if (retType == "Unit") {
              w.wl(call)
            } else {
              w.wl(s"val result = $call")
              w.wl(s"return ${fromPlatformExpr(m.ret.get.resolved, resultExpr, isAndroid = true)}")
            }
            w.decrease()
            w.wl("}")
          }
          if (i.consts.nonEmpty || i.methods.exists(_.static)) {
            w.wl
            w.wl("actual companion object").braced {
              for (c <- i.consts) {
                val constType = kmpFieldType(c.ty.resolved)
                val platformConst = s"$platformTypeBase.${idJava.const(c.ident)}"
                w.wl(s"actual val ${idJava.const(c.ident)}: $constType")
                w.wl(s"    get() = ${fromPlatformExpr(c.ty.resolved, platformConst, isAndroid = true)}")
              }
              for (m <- i.methods if m.static) {
                w.wl
                val params = methodParams(m)
                val retType = kmpReturnType(m.ret)
                val retSuffix = if (retType == "Unit") "" else s": $retType"
                w.wl(s"actual fun ${idJava.method(m.ident)}(${params.mkString(", ")})$retSuffix {")
                w.increase()
                val args = m.params.map(p => toPlatformExpr(p.ty.resolved, idJava.local(p.ident), isAndroid = true))
                val call = s"$platformTypeBase.${idJava.method(m.ident)}(${args.mkString(", ")})"
                if (retType == "Unit") {
                  w.wl(call)
                } else {
                  w.wl(s"val result = $call")
                  w.wl(s"return ${fromPlatformExpr(m.ret.get.resolved, resultExpr, isAndroid = true)}")
                }
                w.decrease()
                w.wl("}")
              }
            }
          }
          w.decrease()
          w.wl("}")
          w.wl
          w.wl(s"public fun $name$typeParams.asPlatform(): $platformType = nativeHandle as $platformType")
          w.wl(s"public fun $platformType.asKmp(): $name$typeParams = $name(this)")
        } else {
          w.wl(s"actual interface $name$typeParams")
          w.braced {
            for (m <- i.methods if !m.static) {
              w.wl
              val params = methodParams(m)
              val retType = kmpReturnType(m.ret)
              val retSuffix = if (retType == "Unit") "" else s": $retType"
              w.wl(s"actual fun ${idJava.method(m.ident)}(${params.mkString(", ")})$retSuffix")
            }
          }
          w.wl
          val androidIsInterface = spec.javaGenInterface && !hasStatics && !i.ext.cpp
          val platformProxySuper = if (androidIsInterface) platformType else platformType + "()"
          w.wl(s"private class ${name}PlatformWrapper$typeParams(internal val nativeHandle: $platformType) : $name$typeParams")
          w.braced {
            for (m <- i.methods if !m.static) {
              w.wl
              val params = methodParams(m)
              val retType = kmpReturnType(m.ret)
              val retSuffix = if (retType == "Unit") "" else s": $retType"
              w.wl(s"override fun ${idJava.method(m.ident)}(${params.mkString(", ")})$retSuffix {")
              w.increase()
              val args = m.params.map(p => toPlatformExpr(p.ty.resolved, idJava.local(p.ident), isAndroid = true))
              val call = s"nativeHandle.${idJava.method(m.ident)}(${args.mkString(", ")})"
              if (retType == "Unit") {
                w.wl(call)
              } else {
                w.wl(s"val result = $call")
                w.wl(s"return ${fromPlatformExpr(m.ret.get.resolved, resultExpr, isAndroid = true)}")
              }
              w.decrease()
              w.wl("}")
            }
          }
          w.wl
          w.wl(s"private class ${name}PlatformProxy$typeParams(private val delegate: $name$typeParams) : $platformProxySuper")
          w.braced {
            for (m <- i.methods if !m.static) {
              w.wl
              val params = platformMethodParams(m, isAndroid = true)
              val retType = platformReturnType(m.ret, isAndroid = true)
              val retSuffix = if (retType == "Unit") "" else s": $retType"
              w.wl(s"override fun ${idJava.method(m.ident)}(${params.mkString(", ")})$retSuffix {")
              w.increase()
              val args = m.params.map(p => fromPlatformExpr(p.ty.resolved, idJava.local(p.ident), isAndroid = true))
              val call = s"delegate.${idJava.method(m.ident)}(${args.mkString(", ")})"
              if (retType == "Unit") {
                w.wl(call)
              } else {
                w.wl(s"val result = $call")
                w.wl(s"return ${toPlatformExpr(m.ret.get.resolved, resultExpr, isAndroid = true)}")
              }
              w.decrease()
              w.wl("}")
            }
          }
          w.wl
          w.wl(s"public fun $name$typeParams.asPlatform(): $platformType = when (this) {")
          w.wl(s"    is ${name}PlatformWrapper$typeParams -> this.nativeHandle")
          w.wl(s"    else -> ${name}PlatformProxy(this)")
          w.wl("}")
          w.wl(s"public fun $platformType.asKmp(): $name$typeParams = ${name}PlatformWrapper(this)")
        }
      }, extraImports = conversionImports)
    })

    spec.kotlinKmpIosOutFolder.foreach(folder => {
      val objcName = kmpObjcName(td)
      val iosImports = iosPlatformImportsForInterface(td, i)
      writeKotlinFile(folder, s"$name.kt", origin, w => {
        w.wl("import kotlin.experimental.ExperimentalObjCName")
        w.wl("import kotlin.native.ObjCName")
        if (!classKind) {
          w.wl("import platform.darwin.NSObject")
        }
        w.wl
        w.wl("@OptIn(ExperimentalObjCName::class)")
        w.wl(s"""@ObjCName("$objcName", exact = true)""")
        val platformType = iosActualTypename(td) + typeParamsUse
        if (classKind) {
          w.wl(s"actual class $name$typeParams actual public constructor(")
          w.increase()
          w.wl("nativeHandle: Any,")
          w.decrease()
          w.wl(") {")
          w.increase()
          w.wl("internal val nativeHandle: Any = nativeHandle")
          w.wl(s"private val native = nativeHandle as $platformType")
          for (m <- i.methods if !m.static) {
            w.wl
            val params = methodParams(m)
            val retType = kmpReturnType(m.ret)
            val retSuffix = if (retType == "Unit") "" else s": $retType"
            w.wl(s"actual fun ${idJava.method(m.ident)}(${params.mkString(", ")})$retSuffix {")
            w.increase()
            val args = m.params.map(p => toPlatformExpr(p.ty.resolved, idJava.local(p.ident), isAndroid = false))
            val call = s"native.${idJava.method(m.ident)}(${args.mkString(", ")})"
            if (retType == "Unit") {
              w.wl(call)
            } else {
              w.wl(s"val result = $call")
              w.wl(s"return ${fromPlatformExpr(m.ret.get.resolved, resultExpr, isAndroid = false)}")
            }
            w.decrease()
            w.wl("}")
          }
          if (i.consts.nonEmpty || i.methods.exists(_.static)) {
            w.wl
            w.wl("actual companion object").braced {
              for (c <- i.consts) {
                val constType = kmpFieldType(c.ty.resolved)
                val platformConst = s"${iosActualTypename(td)}.${idJava.const(c.ident)}"
                w.wl(s"actual val ${idJava.const(c.ident)}: $constType")
                w.wl(s"    get() = ${fromPlatformExpr(c.ty.resolved, platformConst, isAndroid = false)}")
              }
              for (m <- i.methods if m.static) {
                w.wl
                val params = methodParams(m)
                val retType = kmpReturnType(m.ret)
                val retSuffix = if (retType == "Unit") "" else s": $retType"
                w.wl(s"actual fun ${idJava.method(m.ident)}(${params.mkString(", ")})$retSuffix {")
                w.increase()
                val args = m.params.map(p => toPlatformExpr(p.ty.resolved, idJava.local(p.ident), isAndroid = false))
                val call = s"${iosActualTypename(td)}.${idJava.method(m.ident)}(${args.mkString(", ")})"
                if (retType == "Unit") {
                  w.wl(call)
                } else {
                  w.wl(s"val result = $call")
                  w.wl(s"return ${fromPlatformExpr(m.ret.get.resolved, resultExpr, isAndroid = false)}")
                }
                w.decrease()
                w.wl("}")
              }
            }
          }
          w.decrease()
          w.wl("}")
          w.wl
          w.wl(s"public fun $name$typeParams.asPlatform(): $platformType = nativeHandle as $platformType")
          w.wl(s"public fun $platformType.asKmp(): $name$typeParams = $name(this)")
        } else {
          w.wl(s"actual interface $name$typeParams")
          w.braced {
            for (m <- i.methods if !m.static) {
              w.wl
              val params = methodParams(m)
              val retType = kmpReturnType(m.ret)
              val retSuffix = if (retType == "Unit") "" else s": $retType"
              w.wl(s"actual fun ${idJava.method(m.ident)}(${params.mkString(", ")})$retSuffix")
            }
          }
          w.wl
          w.wl(s"private class ${name}PlatformWrapper$typeParams(internal val nativeHandle: $platformType) : $name$typeParams")
          w.braced {
            for (m <- i.methods if !m.static) {
              w.wl
              val params = methodParams(m)
              val retType = kmpReturnType(m.ret)
              val retSuffix = if (retType == "Unit") "" else s": $retType"
              w.wl(s"override fun ${idJava.method(m.ident)}(${params.mkString(", ")})$retSuffix {")
              w.increase()
              val args = m.params.map(p => toPlatformExpr(p.ty.resolved, idJava.local(p.ident), isAndroid = false))
              val call = s"nativeHandle.${idJava.method(m.ident)}(${args.mkString(", ")})"
              if (retType == "Unit") {
                w.wl(call)
              } else {
                w.wl(s"val result = $call")
                w.wl(s"return ${fromPlatformExpr(m.ret.get.resolved, resultExpr, isAndroid = false)}")
              }
              w.decrease()
              w.wl("}")
            }
          }
          w.wl
          w.wl(s"private class ${name}PlatformProxy$typeParams(private val delegate: $name$typeParams) : NSObject(), $platformType")
          w.braced {
            for (m <- i.methods if !m.static) {
              w.wl
              val params = platformMethodParams(m, isAndroid = false)
              val retType = platformReturnType(m.ret, isAndroid = false)
              val retSuffix = if (retType == "Unit") "" else s": $retType"
              w.wl(s"override fun ${idJava.method(m.ident)}(${params.mkString(", ")})$retSuffix {")
              w.increase()
              val args = m.params.map(p => fromPlatformExpr(p.ty.resolved, idJava.local(p.ident), isAndroid = false))
              val call = s"delegate.${idJava.method(m.ident)}(${args.mkString(", ")})"
              if (retType == "Unit") {
                w.wl(call)
              } else {
                w.wl(s"val result = $call")
                w.wl(s"return ${toPlatformExpr(m.ret.get.resolved, resultExpr, isAndroid = false)}")
              }
              w.decrease()
              w.wl("}")
            }
          }
          w.wl
          w.wl(s"public fun $name$typeParams.asPlatform(): $platformType = when (this) {")
          w.wl(s"    is ${name}PlatformWrapper$typeParams -> this.nativeHandle")
          w.wl(s"    else -> ${name}PlatformProxy(this)")
          w.wl("}")
          w.wl(s"public fun $platformType.asKmp(): $name$typeParams = ${name}PlatformWrapper(this)")
        }
      }, extraImports = conversionImports ++ iosImports)
    })
  }

  private def androidActualTypename(td: TypeDecl): String = td match {
    case e: ExternTypeDecl => YamlGenerator.metaFromYaml(e).java.typename
    case _ => kotlinMarshal.fqTypename(td.ident, td.body)
  }

  private def iosActualTypename(td: TypeDecl): String = iosActualTypeNameAndImport(td)._1

  private def isObjcProtocol(td: TypeDecl): Boolean = td match {
    case e: ExternTypeDecl =>
      e.body match {
        case i: Interface => useProtocol(i.ext, spec)
        case _ => YamlGenerator.metaFromYaml(e).objc.protocol
      }
    case _ =>
      td.body match {
        case i: Interface => useProtocol(i.ext, spec)
        case _ => false
      }
  }

  private[djinni] def kmpFieldType(tm: MExpr): String = {
    val name = kmpType(tm)
    if (kotlinMarshal.isEnumFlags(tm)) s"EnumSet<$name>" else name
  }

  private def kmpConstructorFieldType(tm: MExpr): String = {
    kmpFieldType(tm)
  }

  private def kmpConstructorFieldInitializer(tm: MExpr, fieldName: String): String = {
    fieldName
  }

  private def needsAndroidRecordWrapper(td: TypeDecl, r: Record): Boolean = {
    val rootName = canonicalName(td)
    r.fields.exists(f => needsAndroidRecordWrapperType(f.ty.resolved, Set(rootName)))
  }

  private def needsAndroidRecordWrapperType(tm: MExpr, seen: Set[String]): Boolean = {
    tm.base match {
      case MList =>
        true
      case MOptional | MSet | MArray =>
        tm.args.exists(arg => needsAndroidRecordWrapperType(arg, seen))
      case MMap =>
        tm.args.exists(arg => needsAndroidRecordWrapperType(arg, seen))
      case _ if meta.isInterface(tm) =>
        true
      case _ if isRecord(tm) =>
        true
      case _ =>
        false
    }
  }

  private[djinni] def kmpReturnType(ret: Option[TypeRef]): String = ret.fold("Unit")(t => kmpFieldType(t.resolved))

  private[djinni] def methodParams(m: Interface.Method): Seq[String] = {
    m.params.map(p => s"${idJava.local(p.ident)}: ${kmpFieldType(p.ty.resolved)}")
  }

  private def platformFieldType(tm: MExpr, isAndroid: Boolean): String = {
    val name = platformType(tm, isAndroid)
    val needsInterfaceNullable =
      !isAndroid && tm.base != MOptional && meta.isInterface(tm)
    val fieldType = if (needsInterfaceNullable) s"$name?" else name
    if (kotlinMarshal.isEnumFlags(tm)) s"EnumSet<$fieldType>" else fieldType
  }

  private def platformReturnType(ret: Option[TypeRef], isAndroid: Boolean): String =
    ret.fold("Unit")(t => platformFieldType(t.resolved, isAndroid))

  private def platformMethodParams(m: Interface.Method, isAndroid: Boolean): Seq[String] = {
    m.params.map(p => s"${idJava.local(p.ident)}: ${platformFieldType(p.ty.resolved, isAndroid)}")
  }

  private def toPlatformExpr(tm: MExpr, expr: String, isAndroid: Boolean, boxedPrimitive: Boolean = false): String = {
    val itExpr = "it"
    val itKeyExpr = "it.key"
    val itValueExpr = "it.value"
    tm.base match {
      case MOptional =>
        val arg = tm.args.head
        s"$expr?.let { ${toPlatformExpr(arg, itExpr, isAndroid, boxedPrimitive = true)} }"
      case MList =>
        val arg = tm.args.head
        s"ArrayList($expr.map { ${toPlatformExpr(arg, itExpr, isAndroid, boxedPrimitive = true)} })"
      case MSet =>
        val arg = tm.args.head
        s"HashSet($expr.map { ${toPlatformExpr(arg, itExpr, isAndroid, boxedPrimitive = true)} })"
      case MMap =>
        val k = tm.args.head
        val v = tm.args(1)
        s"HashMap($expr.map { ${toPlatformExpr(k, itKeyExpr, isAndroid, boxedPrimitive = true)} to ${toPlatformExpr(v, itValueExpr, isAndroid, boxedPrimitive = true)} }.toMap())"
      case MArray =>
        val arg = tm.args.head
        s"$expr.map { ${toPlatformExpr(arg, itExpr, isAndroid, boxedPrimitive = true)} }.toTypedArray()"
      case _ if meta.isInterface(tm) => s"$expr.asPlatform()"
      case _ if isRecord(tm) => s"$expr.asPlatform()"
      case _ if isEnum(tm) =>
        val base = s"$expr.asPlatform()"
        if (!isAndroid && boxedPrimitive) s"platform.Foundation.NSNumber(longLong = $base)" else base
      case p: MPrimitive =>
        if (!isAndroid && boxedPrimitive) objcBox(p, expr) else expr
      case MBinary if !isAndroid =>
        s"if ($expr.isEmpty()) platform.Foundation.NSData.dataWithBytes(bytes = null, length = 0u) else $expr.usePinned { pinned -> platform.Foundation.NSData.dataWithBytes(bytes = pinned.addressOf(0), length = $expr.size.toULong()) }"
      case _ => expr
    }
  }

  private def fromPlatformExpr(tm: MExpr, expr: String, isAndroid: Boolean, boxedPrimitive: Boolean = false): String = {
    val itExpr = "it"
    val itKeyExpr = "it.key"
    val itValueExpr = "it.value"
    tm.base match {
      case MOptional =>
        val arg = tm.args.head
        s"$expr?.let { ${fromPlatformExpr(arg, itExpr, isAndroid, boxedPrimitive = true)} }"
      case MList =>
        val arg = tm.args.head
        if (isAndroid) {
          s"ArrayList($expr.map { ${fromPlatformExpr(arg, itExpr, isAndroid, boxedPrimitive = true)} })"
        } else {
          val listExpr = s"($expr as? List<*>)"
          val listMap = s"$listExpr?.map { ${fromPlatformExpr(arg, itExpr, isAndroid, boxedPrimitive = true)} }"
          val arrayExpr = s"($expr as platform.Foundation.NSArray)"
          val itemExpr = s"$arrayExpr.objectAtIndex(idx.toULong())"
          val arrayMap = s"(0 until $arrayExpr.count.toInt()).map { idx -> ${fromPlatformExpr(arg, itemExpr, isAndroid, boxedPrimitive = true)} }"
          s"ArrayList(($listMap ?: $arrayMap))"
        }
      case MSet =>
        val arg = tm.args.head
        if (isAndroid) {
          s"HashSet($expr.map { ${fromPlatformExpr(arg, itExpr, isAndroid, boxedPrimitive = true)} })"
        } else {
          val setExpr = s"($expr as? Set<*>)"
          val setMap = s"$setExpr?.map { ${fromPlatformExpr(arg, itExpr, isAndroid, boxedPrimitive = true)} }"
          val nsSetExpr = s"($expr as platform.Foundation.NSSet)"
          val seqMap =
            s"run { val e = $nsSetExpr.objectEnumerator(); generateSequence { e.nextObject() }.map { ${fromPlatformExpr(arg, itExpr, isAndroid, boxedPrimitive = true)} }.toList() }"
          s"HashSet(($setMap ?: $seqMap))"
        }
      case MMap =>
        val k = tm.args.head
        val v = tm.args(1)
        if (isAndroid) {
          s"HashMap($expr.map { ${fromPlatformExpr(k, itKeyExpr, isAndroid, boxedPrimitive = true)} to ${fromPlatformExpr(v, itValueExpr, isAndroid, boxedPrimitive = true)} }.toMap())"
        } else {
          val mapExpr = s"($expr as? Map<*, *>)"
          val mapConvert =
            s"$mapExpr?.map { ${fromPlatformExpr(k, itKeyExpr, isAndroid, boxedPrimitive = true)} to ${fromPlatformExpr(v, itValueExpr, isAndroid, boxedPrimitive = true)} }?.toMap()"
          val dictExpr = s"($expr as platform.Foundation.NSDictionary)"
          val dictKey = "key"
          val dictValue = s"$dictExpr.objectForKey(key)"
          val dictConvert =
            s"run { val e = $dictExpr.keyEnumerator(); generateSequence { e.nextObject() }.associate { key -> ${fromPlatformExpr(k, dictKey, isAndroid, boxedPrimitive = true)} to ${fromPlatformExpr(v, dictValue, isAndroid, boxedPrimitive = true)} } }"
          s"HashMap(($mapConvert ?: $dictConvert))"
        }
      case MArray =>
        val arg = tm.args.head
        s"$expr.map { ${fromPlatformExpr(arg, itExpr, isAndroid, boxedPrimitive = true)} }.toTypedArray()"
      case _ if meta.isInterface(tm) =>
        val cast = s"(${expr} as ${platformType(tm, isAndroid)})"
        s"$cast.asKmp()"
      case _ if isRecord(tm) =>
        val cast = s"(${expr} as ${platformType(tm, isAndroid)})"
        s"$cast.asKmp()"
      case _ if isEnum(tm) =>
        if (isAndroid) {
          val cast = s"(${expr} as ${platformType(tm, isAndroid)})"
          s"$cast.asKmp()"
        } else {
          val base =
            if (boxedPrimitive) s"(${expr} as platform.Foundation.NSNumber).longLongValue"
            else s"(${expr} as ${platformType(tm, isAndroid)})"
          s"${kmpType(tm)}.fromPlatform($base)"
        }
      case p: MPrimitive =>
        if (!isAndroid && boxedPrimitive) objcUnbox(p, expr) else expr
      case MString =>
        if (!isAndroid && boxedPrimitive) s"($expr as String)" else expr
      case MBinary if !isAndroid =>
        s"run { val nsData = ($expr as platform.Foundation.NSData); ByteArray(nsData.length.toInt()).also { result -> result.usePinned { pinned -> platform.posix.memcpy(pinned.addressOf(0), nsData.bytes, nsData.length) } } }"
      case _ => expr
    }
  }

  private def platformType(tm: MExpr, isAndroid: Boolean): String = {
    def args(tm: MExpr) = if (tm.args.isEmpty) "" else tm.args.map(f).mkString("<", ", ", ">")
    def isObjcErasedGeneric(e: MExtern): Boolean = {
      e.objc.typename == "DJFuture"
    }
    def externBase(e: MExtern): String = {
      if (isAndroid) {
        e.java.typename
      } else {
        val objcType =
          e.body match {
            case i: Interface if useProtocol(i.ext, spec) => s"${e.objc.typename}Protocol"
            case _ if e.objc.protocol => s"${e.objc.typename}Protocol"
            case _ => e.objc.typename
          }
        objcSystemModule(e.objc.header) match {
          case Some(module) => s"$module.$objcType"
          case None => objcType
        }
      }
    }
    def defBase(d: MDef): String = {
      if (isAndroid) {
        kotlinMarshal.fqTypename(syntheticIdent(d.name), d.body)
      } else {
        val objcType = objcMarshal.typename(syntheticIdent(d.name), d.body)
        val objcTypeWithProtocol = d.body match {
          case i: Interface if useProtocol(i.ext, spec) => s"${objcType}Protocol"
          case _ => objcType
        }
        objcTypeWithProtocol
      }
    }
    def f(tm: MExpr): String = {
      tm.base match {
        case MOptional =>
          assert(tm.args.size == 1)
          val arg = tm.args.head
          arg.base match {
            case p: MPrimitive if !isAndroid => "platform.Foundation.NSNumber?"
            case p: MPrimitive => p.kName + "?"
            case MOptional => throw new AssertionError("nested optional?")
            case _ => f(arg) + "?"
          }
        case MArray => "Array<" + platformType(tm.args.head, isAndroid) + ">"
        case MList if !isAndroid => "List<*>"
        case MSet if !isAndroid => "Set<*>"
        case MMap if !isAndroid => "Map<Any?, *>"
        case e: MExtern =>
          val base = externBase(e)
          val generic = if (isAndroid) e.java.generic else (e.objc.generic && !isObjcErasedGeneric(e))
          base + (if (generic) args(tm) else "")
        case p: MProtobuf => p.name
        case o =>
          val base = o match {
            case p: MPrimitive => p.kName
            case MString => "String"
            case MDate => "Date"
            case MBinary => if (isAndroid) "ByteArray" else "platform.Foundation.NSData"
            case MOptional => throw new AssertionError("optional should have been special cased")
            case MList => "ArrayList"
            case MSet => "HashSet"
            case MMap => "HashMap"
            case MArray => throw new AssertionError("array should have been special cased")
            case d: MDef => defBase(d)
            case e: MExtern => externBase(e)
            case p: MProtobuf => p.name
            case p: MParam => idJava.typeParam(p.name)
            case MVoid => "Void"
          }
          base + args(tm)
      }
    }
    f(tm)
  }

  // JavaScript values use the existing Djinni WASM ABI, not Kotlin's mangled JS ABI.
  private def jsRuntime = kmpBridgePrefix + "DjinniJs"

  private def generateJsRuntime(folder: File): Unit = {
    writeKotlinFile(folder, s"$jsRuntime.kt", "KotlinKmpGenerator", w => {
      w.wl(s"object $jsRuntime {")
      w.wl("    private var module: dynamic = null")
      w.wl("    /** Register the initialized Emscripten module before calling static factories. */")
      w.wl("    fun initialize(module: dynamic) { require(module != null); this.module = module }")
      w.wl("    internal fun requireModule(): dynamic {")
      w.wl("        check(module != null) { \"Initialize the Djinni JS module before using static factories\" }")
      w.wl("        return module")
      w.wl("    }")
      w.wl("    private val proxies: dynamic = js(\"new WeakMap()\")")
      w.wl("    internal fun proxy(owner: Any, type: String, create: () -> dynamic): dynamic {")
      w.wl("        var entries = proxies.get(owner)")
      w.wl("        if (entries == null) { entries = js(\"Object.create(null)\"); proxies.set(owner, entries) }")
      w.wl("        if (entries[type] == null) entries[type] = create()")
      w.wl("        return entries[type]")
      w.wl("    }")
      w.wl("    internal fun <T> optional(value: dynamic, convert: (dynamic) -> T): T? = if (value == null) null else convert(value)")
      w.wl("    internal fun typedArray(kind: String, values: Array<dynamic>): dynamic = js(\"new globalThis[kind](values)\")")
      w.wl("    internal fun set(values: Array<dynamic>): dynamic = js(\"new Set(values)\")")
      w.wl("    internal fun map(values: Array<dynamic>): dynamic = js(\"new Map(values)\")")
      w.wl("    internal fun array(value: dynamic): Array<dynamic> = js(\"Array.from(value)\")")
      w.wl("    internal fun bigInt(value: Long): dynamic { val text = value.toString(); return js(\"BigInt(text)\") }")
      w.wl("    internal fun bytes(value: ByteArray): dynamic { val result = js(\"new Uint8Array(value.length)\"); for (i in value.indices) result[i] = value[i].toInt() and 255; return result }")
      w.wl("}")
    })
  }

  private[djinni] def jsConversionName(tm: MExpr): String = tm.base match {
    case e: MExtern => externKmpFqTypeName(e)
    case d: MDef => kmpBridgePrefix + idJava.ty(d.name)
    case _ => throw new AssertionError("Expected named JS type")
  }

  private def toJs(tm: MExpr, expr: String): String = tm.base match {
    case MOptional => s"""($expr)?.let { value -> ${toJs(tm.args.head, "value")} } ?: js("undefined")"""
    case p: MPrimitive if p.kName == "Long" => s"$jsRuntime.bigInt($expr)"
    case _: MPrimitive | MString => expr
    case MBinary => s"$jsRuntime.bytes($expr)"
    case MArray =>
      val values = s"($expr).map { element -> ${toJs(tm.args.head, "element")} }.toTypedArray()"
      val arrays = Map("Byte" -> "Int8Array", "Short" -> "Int16Array", "Int" -> "Int32Array", "Long" -> "BigInt64Array", "Float" -> "Float32Array", "Double" -> "Float64Array")
      tm.args.head.base match {
        case p: MPrimitive if arrays.contains(p.kName) => s"""$jsRuntime.typedArray("${arrays(p.kName)}", $values)"""
        case _ => values
      }
    case MList => s"($expr).map { element -> ${toJs(tm.args.head, "element")} }.toTypedArray()"
    case MSet => s"$jsRuntime.set(($expr).map { element -> ${toJs(tm.args.head, "element")} }.toTypedArray())"
    case MMap => s"$jsRuntime.map(($expr).map { (key, value) -> arrayOf<dynamic>(${toJs(tm.args.head, "key")}, ${toJs(tm.args(1), "value")}) }.toTypedArray())"
    case e: MExtern if e.name == "future" => s"${jsConversionName(tm)}ToJs($expr) { item -> ${toJs(tm.args.head, "item")} }"
    case _: MDef | _: MExtern => s"${jsConversionName(tm)}ToJs($expr)"
    case _: MParam => expr
    case _ => throw new IllegalArgumentException("Unsupported Kotlin/JS type: " + tm)
  }

  private def fromJs(tm: MExpr, expr: String): String = tm.base match {
    case MOptional => s"$jsRuntime.optional($expr) { value -> ${fromJs(tm.args.head, "value")} }"
    case p: MPrimitive if p.kName == "Long" => s"(($expr).toString() as String).toLong()"
    case p: MPrimitive if Set("Byte", "Short", "Int", "Float", "Double").contains(p.kName) => s"($expr as Number).to${p.kName}()"
    case p: MPrimitive => s"($expr as ${p.kName})"
    case MString => s"($expr as String)"
    case MBinary => s"$jsRuntime.array($expr).map { (it as Number).toByte() }.toByteArray()"
    case MList => s"$jsRuntime.array($expr).map { element -> ${fromJs(tm.args.head, "element")} }"
    case MArray => s"$jsRuntime.array($expr).map { element -> ${fromJs(tm.args.head, "element")} }.toTypedArray()"
    case MSet => s"$jsRuntime.array($expr).map { element -> ${fromJs(tm.args.head, "element")} }.toHashSet()"
    case MMap => s"$jsRuntime.array($expr).associate { entry -> ${fromJs(tm.args.head, "entry[0]")} to ${fromJs(tm.args(1), "entry[1]")} }.let { HashMap(it) }"
    case e: MExtern if e.name == "future" => s"${jsConversionName(tm)}FromJs($expr) { item: dynamic -> ${fromJs(tm.args.head, "item")} }"
    case _: MDef | _: MExtern => s"${jsConversionName(tm)}FromJs($expr)"
    case _: MParam => s"($expr as ${kmpType(tm)})"
    case _ => throw new IllegalArgumentException("Unsupported Kotlin/JS type: " + tm)
  }

  private def generateJsRecord(folder: File, td: TypeDecl, r: Record): Unit = {
    val name = kmpTypeName(td)
    val tp = typeParamDecl(td.params)
    val funTp = if (tp.isEmpty) "" else tp + " "
    writeKotlinFile(folder, s"$name.kt", td.origin, w => {
      w.wl(s"actual class $name$tp actual constructor(${r.fields.map(f => s"actual val ${idJava.field(f.ident)}: ${kmpFieldType(f.ty.resolved)}").mkString(", ")})")
      w.wl(s"fun $funTp${name}ToJs(value: $name$tp): dynamic {")
      w.wl("    val result = js(\"({})\")")
      for (f <- r.fields) w.wl(s"""    result["${idJs.field(f.ident)}"] = ${toJs(f.ty.resolved, "value." + idJava.field(f.ident))}""")
      w.wl("    return result")
      w.wl("}")
      w.wl(s"fun $funTp${name}FromJs(value: dynamic): $name$tp = $name(")
      for (f <- r.fields) w.wl(s"""    ${fromJs(f.ty.resolved, "value[\"" + idJs.field(f.ident) + "\"]")},""")
      w.wl(")")
    })
  }

  private def generateJsEnum(folder: File, td: TypeDecl, e: Enum): Unit = {
    val name = kmpTypeName(td)
    writeKotlinFile(folder, s"$name.kt", td.origin, w => {
      w.wl(s"actual enum class $name(val jsValue: Int) {")
      for ((o, idx) <- normalEnumOptions(e).zipWithIndex) {
        val value = if (e.flags) s"(1 shl $idx)" else idx.toString
        w.wl(s"    ${idJava.enum(o.ident)}($value),")
      }
      w.wl("}")
      w.wl(s"fun ${name}ToJs(value: $name): dynamic = value.jsValue")
      w.wl(s"fun ${name}FromJs(value: dynamic): $name = $name.entries.firstOrNull { it.jsValue == (value as Number).toInt() }")
      w.wl(s"""    ?: throw IllegalArgumentException("Unknown $name value: " + value)""")
    })
  }

  private def generateJsInterface(folder: File, td: TypeDecl, i: Interface, classKind: Boolean): Unit = {
    val name = kmpTypeName(td)
    val tp = typeParamDecl(td.params)
    val funTp = if (tp.isEmpty) "" else tp + " "
    val staticType = spec.wasmNamespace.map(_ + ".").getOrElse("") + idJs.ty(td.ident)
    def writeMethod(w: IndentWriter, m: Interface.Method, modifier: String, receiver: String): Unit = {
      w.wl(s"$modifier fun ${idJava.method(m.ident)}(${methodParams(m).mkString(", ")}): ${kmpReturnType(m.ret)} {")
      w.increase()
      val args = m.params.map(p => toJs(p.ty.resolved, idJava.local(p.ident)))
      val call = s"""$receiver["${idJs.method(m.ident)}"](${args.mkString(", ")})"""
      m.ret match {
        case Some(ret) => w.wl(s"val result = $call"); w.wl(s"return ${fromJs(ret.resolved, "result")}")
        case None => w.wl(call)
      }
      w.decrease(); w.wl("}")
    }
    writeKotlinFile(folder, s"$name.kt", td.origin, w => {
      if (classKind) {
        w.wl(s"actual class $name$tp actual constructor(nativeHandle: Any) {")
        w.increase()
        w.wl("internal val native: dynamic = nativeHandle")
        for (m <- i.methods if !m.static) writeMethod(w, m, "actual", "native")
        if (i.consts.nonEmpty || i.methods.exists(_.static)) {
          w.wl("actual companion object {"); w.increase()
          val receiver = staticType.split('.').foldLeft(s"$jsRuntime.requireModule()")((base, part) => base + "[\"" + part + "\"]")
          for (c <- i.consts) w.wl(s"""actual val ${idJava.const(c.ident)}: ${kmpFieldType(c.ty.resolved)} get() = ${fromJs(c.ty.resolved, receiver + "[\"" + idJs.const(c.ident) + "\"]")}""")
          for (m <- i.methods if m.static) writeMethod(w, m, "actual", receiver)
          w.decrease(); w.wl("}")
        }
        w.decrease(); w.wl("}")
        w.wl(s"fun $funTp${name}ToJs(value: $name$tp): dynamic = value.native")
        w.wl(s"fun $funTp${name}FromJs(value: dynamic): $name$tp = $name(value as Any)")
      } else {
        w.wl(s"actual interface $name$tp {")
        for (m <- i.methods) w.wl(s"    actual fun ${idJava.method(m.ident)}(${methodParams(m).mkString(", ")}): ${kmpReturnType(m.ret)}")
        w.wl("}")
        w.wl(s"private class ${name}JsWrapper$tp(val native: dynamic) : $name$tp {")
        w.increase()
        for (m <- i.methods) writeMethod(w, m, "override", "native")
        w.decrease(); w.wl("}")
        w.wl(s"fun $funTp${name}FromJs(value: dynamic): $name$tp = ${name}JsWrapper(value)")
        w.wl(s"fun $funTp${name}ToJs(value: $name$tp): dynamic {")
        w.increase()
        w.wl(s"if (value is ${name}JsWrapper$tp) return value.native")
        w.wl(s"""return $jsRuntime.proxy(value, "$name") {"""); w.increase()
        w.wl("val result = js(\"({})\")")
        for (m <- i.methods) {
          val params = m.params.indices.map(n => s"p$n: dynamic").mkString(", ")
          val args = m.params.zipWithIndex.map { case (p, n) => fromJs(p.ty.resolved, s"p$n") }
          w.wl(s"""result["${idJs.method(m.ident)}"] = {${if (params.nonEmpty) " " + params + " ->" else ""}""")
          w.increase()
          val call = s"value.${idJava.method(m.ident)}(${args.mkString(", ")})"
          m.ret match {
            case Some(ret) => w.wl(s"val returned = $call"); w.wl(toJs(ret.resolved, "returned"))
            case None => w.wl(call)
          }
          w.decrease(); w.wl("}")
        }
        w.wl("result"); w.decrease(); w.wl("}")
        w.decrease(); w.wl("}")
      }
    })
  }

  private def kmpType(tm: MExpr): String = {
    def args(tm: MExpr) = if (tm.args.isEmpty) "" else tm.args.map(f).mkString("<", ", ", ">")
    def f(tm: MExpr): String = {
      tm.base match {
        case MOptional =>
          assert(tm.args.size == 1)
          val arg = tm.args.head
          arg.base match {
            case p: MPrimitive => p.kName + "?"
            case MOptional => throw new AssertionError("nested optional?")
            case _ => f(arg) + "?"
          }
        case MArray => "Array<" + kmpType(tm.args.head) + ">"
        case e: MExtern => externKmpFqTypeName(e) + (if (e.java.generic) args(tm) else "")
        case p: MProtobuf => p.name
        case o =>
          val base = o match {
            case p: MPrimitive => p.kName
            case MString => "String"
            case MDate => "Date"
            case MBinary => "ByteArray"
            case MOptional => throw new AssertionError("optional should have been special cased")
            case MList => "List"
            case MSet => "HashSet"
            case MMap => "HashMap"
            case MArray => throw new AssertionError("array should have been special cased")
            case d: MDef => kmpBridgePrefix + idJava.ty(d.name)
            case e: MExtern => externKmpFqTypeName(e)
            case p: MProtobuf => p.name
            case p: MParam => idJava.typeParam(p.name)
            case MVoid => "Void"
          }
          base + args(tm)
      }
    }
    f(tm)
  }
}

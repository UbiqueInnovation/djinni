/**
  * Copyright 2024 Snap, Inc.
  *
  * Licensed under the Apache License, Version 2.0 (the "License");
  * you may not use this file except in compliance with the License.
  * You may obtain a copy of the License at
  *
  *    http://www.apache.org/licenses/LICENSE-2.0
  *
  * Unless required by applicable law or agreed to in writing, software
  * distributed under the License is distributed on an "AS IS" BASIS,
  * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
  * See the License for the specific language governing permissions and
  * limitations under the License.
  */

package djinni

import java.io._
import djinni.ast.Record.DerivingType
import djinni.ast._
import djinni.generatorTools._
import djinni.meta._
import djinni.writer.IndentWriter
import scala.collection.mutable.ListBuffer
import scala.collection.mutable.TreeSet
import java.util.regex.Pattern
import java.util.regex.Matcher
import scala.collection.mutable

class SwiftGenerator(spec: Spec) extends Generator(spec) {

  val marshal = new SwiftMarshal(spec)
  private val nativeMarshal = new SwiftxxMarshal(spec)
  private val cppMarshal = new CppMarshal(spec)
  private def escapeSwiftIdent(value: String): String = marshal.escapeSwiftIdent(value)
  private val swiftEnumIdent: IdentConverter = name => escapeSwiftIdent(idSwift.enum(name.toLowerCase(java.util.Locale.ROOT)))

  private def futureValue(m: Interface.Method): Option[MExpr] = m.ret.flatMap { t =>
    t.resolved.base match {
      case e: MExtern if e.swift.translator == "FutureMarshaller" && t.resolved.args.size == 1 => Some(t.resolved.args.head)
      case _ => None
    }
  }
  private def methodEffects(m: Interface.Method): String = if (futureValue(m).nonEmpty) " async throws" else throwsClause
  private def methodReturn(m: Interface.Method): String = futureValue(m).map(marshal.fqTypename).getOrElse(marshal.fqReturnType(m.ret))

  private def toNative(tm: MExpr, expr: String): String = if (!nativeMarshal.isNative(tm)) marshal.toCpp(tm, expr) else tm.base match {
    case _: MPrimitive => expr
    case MString => s"std.string($expr)"
    case _ => s"${marshal.helperName(tm)}.toNative($expr)"
  }
  private def fromNative(tm: MExpr, expr: String): String = if (!nativeMarshal.isNative(tm)) marshal.fromCpp(tm, expr) else tm.base match {
    case _: MPrimitive => expr
    case MString => s"String($expr)"
    case _ => s"${marshal.helperName(tm)}.fromNative($expr)"
  }
  private def containerToNative(tm: MExpr, expr: String, name: String, owner: String = ""): String =
    if (nativeMarshal.isNativeContainer(tm)) s"${owner}to$name($expr)" else toNative(tm, expr)
  private def containerFromNative(tm: MExpr, expr: String, name: String, owner: String = ""): String =
    if (nativeMarshal.isNativeContainer(tm)) s"${owner}from$name($expr)" else fromNative(tm, expr)

  private def writeNativeContainers(w: IndentWriter, containers: Seq[(String, MExpr)], helper: String, access: String): Unit = {
    for ((name, tm) <- containers) {
      val swiftType = marshal.fqTypename(tm)
      val child = tm.args.head
      w.w(s"${access}static func to$name(_ s: $swiftType) -> $helper.$name").braced {
        if (tm.base == MOptional) {
          w.wl(s"guard let value = s else { return $helper.$name() }")
          val value = containerToNative(child, "value", name + "Element")
          w.wl(if (nativeMarshal.isInterface(child)) s"return $value" else s"return $helper.$name($value)")
        } else {
          w.wl(s"var result = $helper.$name()")
          w.wl("result.reserve(s.count)")
          if (tm.base == MMap) {
            w.w("for (key, value) in s").braced {
              w.wl(s"$helper.insert$name(&result, ${containerToNative(child, "key", name + "Key")}, ${containerToNative(tm.args(1), "value", name + "Value")})")
            }
          } else {
            w.w("for value in s").braced {
              val insert = if (tm.base == MSet) "insert" else "push_back"
              w.wl(s"result.$insert(${containerToNative(child, "value", name + "Element")})")
            }
          }
          w.wl("return result")
        }
      }
      w.w(s"${access}static func from$name(_ c: $helper.$name) -> $swiftType").braced {
        if (tm.base == MOptional) {
          if (nativeMarshal.isInterface(child)) {
            w.wl("guard Bool(fromCxx: c) else { return nil }")
            w.wl(s"return ${containerFromNative(child, "c", name + "Element")}")
          } else {
            w.wl("guard let value = c.value else { return nil }")
            w.wl(s"return ${containerFromNative(child, "value", name + "Element")}")
          }
        } else {
          w.wl(s"var result: $swiftType = ${if (tm.base == MMap) "[:]" else "[]"}")
          w.wl("result.reserveCapacity(Int(c.size()))")
          if (tm.base == MMap || tm.base == MSet) {
            w.wl("var iterator = c.__beginUnsafe()")
            w.wl("let end = c.__endUnsafe()")
            w.w("while iterator != end").braced {
              if (tm.base == MMap) {
                w.wl("let entry = iterator.pointee")
                w.wl(s"result.updateValue(${containerFromNative(tm.args(1), "entry.second", name + "Value")}, forKey: ${containerFromNative(child, "entry.first", name + "Key")})")
              } else {
                w.wl(s"result.insert(${containerFromNative(child, "iterator.pointee", name + "Element")})")
              }
              w.wl("iterator = iterator.successor()")
            }
            w.wl("withExtendedLifetime(c) {}")
          } else {
            w.w("for index in 0..<c.size()").braced {
              val item = if (nativeMarshal.isBoolean(child)) s"$helper.read$name(c, index)" else "c[Int(index)]"
              w.wl(s"result.append(${containerFromNative(child, item, name + "Element")})")
            }
          }
          w.wl("return result")
        }
      }
    }
  }

  private def writeNativeCall(w: IndentWriter, ident: Ident, m: Interface.Method, receiver: String = "inst"): Unit = {
    if (futureValue(m).nonEmpty) w.wl("try Task.checkCancellation()")
    val args = (if (m.static) Seq.empty else Seq(receiver)) ++ m.params.zipWithIndex.map { case (p, index) => containerToNative(p.ty.resolved, idSwift.local(p.ident), nativeMarshal.methodContainerName(ident, m, s"Arg$index"), marshal.helperClass(ident) + ".") }
    val binding = if (m.ret.nonEmpty) "var" else "let"
    w.wl(s"$binding result = ${spec.swiftxxNamespace}.${idSwift.ty(ident)}_${idSwift.method(m.ident)}Native(${args.mkString(", ")})")
    w.w("if result.hasError()").braced {
      w.wl("var ret = result.getError()")
      w.wl(if (futureValue(m).nonEmpty) "try handleCppErrors(&ret)" else checkErrors)
    }
    m.ret.foreach { t =>
      val name = nativeMarshal.methodContainerName(ident, m, "Return")
      if (nativeMarshal.nativeFutureValue(t.resolved).nonEmpty) {
        w.wl(s"return try await ${marshal.helperClass(ident)}.from${name}Future(result.takeValue())")
      } else {
        val result = containerFromNative(t.resolved, "result.takeValue()", name, marshal.helperClass(ident) + ".")
        w.wl(if (futureValue(m).nonEmpty) s"return try await $result.value" else s"return $result")
      }
    }
  }

  private val throwsClause = if (spec.swiftNonThrowing) "" else " throws"
  private val checkErrors = if (spec.swiftNonThrowing) "try! handleCppErrors(&ret)" else "try handleCppErrors(&ret)"

  def writeSwiftFile(ident: String, origin: String, refs: Iterable[String], f: IndentWriter => Unit) {
    createFile(spec.swiftOutFolder.get, idSwift.ty(ident) + ".swift", (w: IndentWriter) => {
      w.wl("// AUTOGENERATED FILE - DO NOT MODIFY!")
      w.wl("// This file was generated by Djinni from " + origin)
      w.wl
      if (refs.nonEmpty) {
        refs.foreach(s => w.wl(s"import $s"))
        w.wl
      }
      f(w)
    })
  }
  def writeSwiftPrivateFile(ident: String, origin: String, refs: Iterable[String], f: IndentWriter => Unit) {
    createFile(spec.swiftOutFolder.get, idSwift.ty(ident) + "+Private.swift", (w: IndentWriter) => {
      w.wl("// AUTOGENERATED FILE - DO NOT MODIFY!")
      w.wl("// This file was generated by Djinni from " + origin)
      w.wl
      if (refs.nonEmpty) {
        refs.foreach(s => w.wl(s"import $s"))
        w.wl
      }
      f(w)
    })
  }

  def writeFlagNone(w: IndentWriter, e: Enum, ident: IdentConverter, t: String) {
    for (o <- e.options.find(_.specialFlag == Some(Enum.SpecialFlag.NoFlags))) {
      writeDoc(w, o.doc)
      w.wl(s"public static let ${ident(o.ident.name)}: $t = []")
    }
  }
  def writeFlags(w: IndentWriter, e: Enum, ident: IdentConverter, t: String) {
    var shift = 0
    for (o <- normalEnumOptions(e)) {
      writeDoc(w, o.doc)
      w.wl(s"public static let ${ident(o.ident.name)} = $t(rawValue: 1 << $shift)")
      shift += 1
    }
  }
  def writeFlagAll(w: IndentWriter, e: Enum, ident: IdentConverter, t: String) {
    for (
      o <- e.options.find(_.specialFlag.contains(Enum.SpecialFlag.AllFlags))
    ) {
      writeDoc(w, o.doc)
      val all = normalEnumOptions(e)
          .map{case o => "."+ident(o.ident.name)}
          .mkString(", ")
      w.w(s"public static let ${ident(o.ident.name)}: $t = [${all}]")
    }
  }

  override def generateEnum(origin: String, ident: Ident, doc: Doc, e: Enum) {
    writeSwiftFile(ident, origin, List[String](), w => {
      val t = marshal.typename(ident, e)
      if (e.flags) {
        w.w(s"public struct $t: OptionSet, Sendable").braced {
          w.wl("public let rawValue: Int32")
          w.wl("public init(rawValue: Int32) { self.rawValue = rawValue }")
          writeFlagNone(w, e, swiftEnumIdent, t)
          writeFlags(w, e, swiftEnumIdent, t)
          writeFlagAll(w, e, swiftEnumIdent, t)
        }
      } else {
        w.w(s"public enum ${marshal.typename(ident, e)}: Int32, Sendable").braced {
          writeEnumOptions(w, e, swiftEnumIdent, "=", "case ", "")
        }
      }
    })
    writeSwiftPrivateFile(ident, origin, List[String]("DjinniSupport", spec.swiftxxBaseLibModule, spec.swiftModule + "Cxx"), w => {
      val native = spec.swiftModule + "Cxx." + cppMarshal.fqTypename(ident, e).stripPrefix("::").replace("::", ".")
      val helper = spec.swiftxxNamespace + "." + spec.swiftxxClassIdentStyle(ident)
      w.w(s"public enum ${marshal.typename(ident, e)}Marshaller: DjinniSupport.Marshaller").braced {
        w.wl(s"public typealias SwiftType = ${marshal.fqTypename(ident, e)}")
        w.wl(s"public static func toNative(_ s: SwiftType) -> $native { $helper.toNative(s.rawValue) }")
        val unwrap = if (e.flags) "" else "!"
        w.wl(s"public static func fromNative(_ c: $native) -> SwiftType { SwiftType(rawValue: $helper.fromNative(c))$unwrap }")
        w.wl("public static func toCpp(_ s: SwiftType) -> djinni.swift.AnyValue { EnumMarshaller<SwiftType>.toCpp(s) }")
        w.wl("public static func fromCpp(_ c: djinni.swift.AnyValue) -> SwiftType { EnumMarshaller<SwiftType>.fromCpp(c) }")
      }
    })
  }

  // return the base type if tm is optional otherwise None
  private def optionalBase(tm: MExpr) : Option[MExpr] = {
    tm.base match {
      case MOptional => Some(tm.args.head)
      case _ => None
    }
  }

  private def swiftMethodName(ident: String): String = marshal.swiftMethodName(ident)

  class SwiftRefs(name: String) {
    var swiftImports = mutable.TreeSet[String]()
    var privateImports = mutable.TreeSet[String]("CxxStdlib", "DjinniSupport", "Foundation", spec.swiftxxBaseLibModule, spec.swiftModule + "Cxx")
    swiftImports.add("Foundation")
    def find(ty: TypeRef) { find(ty.resolved) }
    def find(tm: MExpr) {
      tm.args.foreach(find)
      find(tm.base)
    }
    def find(m: Meta) = for(r <- marshal.references(m, name)) r match {
      // don't import empty module name (e.g. types defined in stdlib) or same module as the current one
      case ImportRef(arg) => if (arg.nonEmpty && arg != spec.swiftModule) { swiftImports.add(arg) }
      case PrivateImportRef(arg) => if (arg.nonEmpty && arg != spec.swiftModule) { privateImports.add(arg) }
      case _ =>
    }
  }

  def generateSwiftConstants(w: IndentWriter, consts: Seq[Const]) = {

    def writeSwiftConst(w: IndentWriter, ty: TypeRef, v: Any): Unit = v match {
      case l: Long => w.w(l.toString)
      case d: Double => w.w(d.toString)
      case b: Boolean => w.w(if (b) "true" else "false")
      case s: String => w.w(s)
      case e: EnumValue =>  w.w(s"${marshal.typename(ty)}.${swiftEnumIdent(e)}")
      case v: ConstRef => w.w(idSwift.const(v))
      case z: Map[_, _] => { // Value is record
        val recordMdef = ty.resolved.base.asInstanceOf[MDef]
        val record = recordMdef.body.asInstanceOf[Record]
        val vMap = z.asInstanceOf[Map[String, Any]]
        w.wl(s"${marshal.typename(ty)}(")
        w.increase()
        // Use exact sequence
        val skipFirst = SkipFirst()
        for (f <- record.fields) {
          skipFirst {w.wl(",")}
          w.w(idSwift.field(f.ident) + ":")
          writeSwiftConst(w, f.ty, vMap.apply(f.ident.name))
        }
        w.w(")")
        w.decrease()
      }
    }

    for (c <- consts) {
      writeDoc(w, c.doc)
      w.w(s"public static let ${idSwift.const(c.ident)}: ${marshal.fieldType(c.ty)} = ")
      writeSwiftConst(w, c.ty, c.value)
      w.wl
    }
  }

  def generateConformance(deriving: Set[Record.DerivingType.Value], prefix: String) = {
    val eq = if (deriving.contains(DerivingType.Eq)) "Equatable" else ""
    val hashable = if (deriving.contains(DerivingType.Hashable)) "Hashable" else ""
    val sendable = if (deriving.contains(DerivingType.Sendable)) "Sendable" else ""
    val codable = if (deriving.contains(DerivingType.Codable) || deriving.contains(DerivingType.AndroidParcelable)) "Codable" else ""
    val error = if (deriving.contains(DerivingType.Error)) "Error" else ""
    val conformance = Array(eq, hashable, sendable, codable).filter(_ != "")
    if (conformance.nonEmpty) prefix + conformance.mkString(", ") else ""
  }

  override def generateRecord(origin: String, ident: Ident, doc: Doc, params: Seq[TypeParam], r: Record) {
    val refs = new SwiftRefs(ident.name)
    r.fields.foreach(f => refs.find(f.ty))
    writeSwiftFile(ident, origin, refs.swiftImports, w => {
      writeDoc(w, doc)
      w.w(s"public struct ${marshal.typename(ident, r)}${generateConformance(r.derivingTypes, ": ")}").braced {
        generateSwiftConstants(w, r.consts)
        for (f <- r.fields) {
          writeDoc(w, f.doc)
          w.wl(s"public var ${idSwift.field(f.ident)}: ${marshal.fqFieldType(f.ty)}")
        }
        val initParams = r.fields.map(f => s"${idSwift.field(f.ident)}: ${marshal.fqFieldType(f.ty)}").mkString(", ")
        w.wl
        w.wl(s"public init(${initParams})").braced {
          for (f <- r.fields) {
            w.wl(s"self.${idSwift.field(f.ident)} = ${idSwift.field(f.ident)}")
          }
        }
      }
    })
    writeSwiftPrivateFile(ident, origin, refs.privateImports, w => {
      w.w(s"public enum ${marshal.typename(ident, r)}Marshaller: DjinniSupport.Marshaller").braced {
        w.wl(s"public typealias SwiftType = ${marshal.fqTypename(ident, r)}")
        if (params.isEmpty) {
          val native = spec.swiftModule + "Cxx." + cppMarshal.fqTypename(ident, r).stripPrefix("::").replace("::", ".")
          val helper = spec.swiftxxNamespace + "." + spec.swiftxxClassIdentStyle(ident)
          val constructor = if (r.fields.forall(f => nativeMarshal.isNative(f.ty.resolved))) native else helper + ".makeNative"
          val containers = r.fields.zipWithIndex.flatMap { case (field, index) => nativeMarshal.nativeContainers(field.ty.resolved, s"NativeField$index") }
          writeNativeContainers(w, containers, helper, "private ")
          w.w(s"public static func toNative(_ s: SwiftType) -> $native").braced {
            val values = r.fields.zipWithIndex.map { case (f, index) => containerToNative(f.ty.resolved, "s." + idSwift.field(f.ident), s"NativeField$index") }.mkString(", ")
            w.wl(s"return $constructor($values)")
          }
          w.w(s"public static func fromNative(_ c: $native) -> SwiftType").braced {
            val fields = r.fields.zipWithIndex.map { case (f, index) =>
              val value = if (nativeMarshal.isNative(f.ty.resolved)) "c." + idCpp.field(f.ident) else s"$helper.getField$index(c)"
              idSwift.field(f.ident) + ": " + containerFromNative(f.ty.resolved, value, s"NativeField$index")
            }.mkString(", ")
            w.wl(s"return SwiftType($fields)")
          }
        }
        w.w("public static func fromCpp(_ c: djinni.swift.AnyValue) -> SwiftType").braced {
          w.wl("return withUnsafePointer(to: c) { p in").nested {
            for ((f, i) <- r.fields.view.zipWithIndex) {
              val swiftExp = s"djinni.swift.getMember(p, $i)"
              w.wl(s"let ${idSwift.field(f.ident)} = ${marshal.fromCpp(f.ty, swiftExp)}")
            }
            val members = r.fields.map(f => s"${idSwift.field(f.ident)}: ${idSwift.field(f.ident)}").mkString(", ")
            w.wl(s"return SwiftType(${members})")
          }
          w.wl("}")
        }
        w.w("public static func toCpp(_ s: SwiftType) -> djinni.swift.AnyValue").braced {
          if (r.fields.nonEmpty) {
            w.wl("var ret = djinni.swift.makeCompositeValue()")
            for (f <- r.fields) {
              val swiftExpr = s"s.${idSwift.field(f.ident)}"
              w.wl(s"djinni.swift.addMember(&ret, ${marshal.toCpp(f.ty,swiftExpr )})")
            }
            w.wl("return ret")
          } else {
            w.wl("return djinni.swift.makeCompositeValue()")
          }
        }
      }
    })
  }
  override def generateInterface(origin: String, ident: Ident, doc: Doc, typeParams: Seq[TypeParam], i: Interface) {
    val refs = new SwiftRefs(ident.name)
    i.methods.foreach(m => {
      m.params.foreach(p => refs.find(p.ty))
      m.ret.foreach(refs.find)
    })
    i.consts.foreach(c => {
      refs.find(c.ty)
    })
    // Preserve operations and overloaded getters as methods. Only unambiguous,
    // zero-argument get_* accessors become Swift properties.
    val properties = marshal.properties(i)
    def propertyName(m: Interface.Method) = marshal.propertyName(m)
    def setterFor(m: Interface.Method): Option[Interface.Method] = marshal.setterFor(i, m)
    val setters = properties.flatMap(setterFor)
    def writeProperty(w: IndentWriter, getter: Interface.Method, implementation: Boolean, receiver: String, access: String): Unit = {
      writeMethodDoc(w, getter, idSwift.local)
      w.w(s"${access}var ${propertyName(getter)}: ${methodReturn(getter)}").braced {
        if (implementation) {
          w.w(s"get${methodEffects(getter)}").braced { writeNativeCall(w, ident, getter, receiver) }
          setterFor(getter).foreach { setter =>
            w.w(s"set(${idSwift.local(setter.params.head.ident)})").braced { writeNativeCall(w, ident, setter, receiver) }
          }
        } else {
          w.wl(s"get${methodEffects(getter)}" + (if (setterFor(getter).nonEmpty) " set" else ""))
        }
      }
    }
    val nativeClass = i.ext.cpp && !i.ext.swift
    val publicImports = if (nativeClass) refs.privateImports.filter(_ != spec.swiftModule) else refs.swiftImports
    writeSwiftFile(ident, origin, publicImports, w => {
      writeDoc(w, doc)
      val declaration = if (nativeClass) s"public final class ${marshal.typename(ident, i)}" else s"public protocol ${marshal.typename(ident, i)}: AnyObject"
      w.w(declaration).braced {
        if (nativeClass) {
          w.wl("let _djinniHandle: djinni.swift.AnyValue")
          w.wl("init(_ handle: djinni.swift.AnyValue) { _djinniHandle = handle }")
          w.wl("deinit { forgetCppProxy(_djinniHandle) }")
        }
        properties.foreach(m => writeProperty(w, m, nativeClass, "_djinniHandle", if (nativeClass) "public " else ""))
        for (m <- i.methods.filter(m => !m.static && !properties.contains(m) && !setters.contains(m))) {
          writeMethodDoc(w, m, idSwift.local)
          w.w((if (nativeClass) "public " else "") + s"func ${swiftMethodName(m.ident)}(")
          if (m.params.nonEmpty) { w.w("_ ") }
          w.w(m.params.map(p => s"${idSwift.local(p.ident)}: ${marshal.fqParamType(p.ty)}").mkString(", "))
          w.w(s")${methodEffects(m)} -> ${methodReturn(m)}")
          if (nativeClass) w.braced { writeNativeCall(w, ident, m, "_djinniHandle") }
          else w.wl
        }
      }
    })
    writeSwiftPrivateFile(ident, origin, refs.privateImports, w => {
      writeDoc(w, doc)
      // Define CppProxy class if interface is implemented in C++
      if (i.ext.cpp && !nativeClass) {
        w.w(s"final class ${marshal.typename(ident, i)}CppProxy: DjinniSupport.CppProxy, ${marshal.fqTypename(ident, i)}").braced {
          w.wl("init(_ inst: djinni.swift.AnyValue) { super.init(inst:inst) }")
          properties.foreach(m => writeProperty(w, m, true, "inst", ""))
          for (m <- i.methods.filter(m => !m.static && !properties.contains(m) && !setters.contains(m))) {
            w.w(s"func ${swiftMethodName(m.ident)}(")
            if (m.params.nonEmpty) { w.w("_ ") }
            w.w(m.params.map(p => s"${idSwift.local(p.ident)}: ${marshal.fqParamType(p.ty)}").mkString(", "))
            w.w(s")${methodEffects(m)} -> ${methodReturn(m)}").braced {
              writeNativeCall(w, ident, m)
            }
          }
        }
      }
      // Define the vtbl for protocol wrapper if interface is implemented in Swift
      val swiftProxyClassName = s"${marshal.typename(ident, i)}SwiftProxy"
      val swiftProxyVtbl = s"${idSwift.local(ident)}Methods"
      if (i.ext.swift) {
        w.wl(s"let $swiftProxyVtbl: Vtbl<${marshal.typename(ident, i)}> = [").nested {
          for (m <- i.methods.filter(!_.static)) {
            w.wl("{ inst, rawCall in").nested {
              if (m.params.nonEmpty || m.ret.nonEmpty) {
                val payload = spec.swiftModule + "Cxx." + spec.swiftxxNamespace + "." + swiftProxyClassName + "_" + idSwift.method(m.ident) + "Call"
                w.wl(s"let call = rawCall!.assumingMemoryBound(to: $payload.self)")
              }
              for ((p, i) <- m.params.view.zipWithIndex) {
                val pi = s"call.pointee.arg$i"
                w.wl(s"let _${idSwift.local(p.ident)} = ${containerFromNative(p.ty.resolved, pi, nativeMarshal.methodContainerName(ident, m, s"Arg$i"), marshal.helperClass(ident) + ".")}")
              }
              val args = m.params.view.zipWithIndex.map{case (p, i) =>
                val label = if (i==0) "" else s"${idSwift.local(p.ident)}: "
                label + s"_${idSwift.local(p.ident)}"
              }.mkString(", ")
              val call = if (properties.contains(m)) s"inst.${propertyName(m)}"
                else properties.find(g => setterFor(g).contains(m)) match {
                  case Some(getter) => s"inst.${propertyName(getter)} = _${idSwift.local(m.params.head.ident)}"
                  case None => s"inst.${swiftMethodName(m.ident)}(${args})"
                }
              if (m.ret.exists(t => nativeMarshal.nativeFutureValue(t.resolved).nonEmpty)) {
                val name = nativeMarshal.methodContainerName(ident, m, "Return")
                w.wl(s"call.pointee.setResult(${marshal.helperClass(ident)}.to${name}Future { try await $call })")
              } else if (futureValue(m).nonEmpty) {
                val bridge = marshal.toCpp(m.ret.get.resolved, "").stripSuffix(".toCpp()")
                w.wl(s"call.pointee.setResult($bridge.toCppAsync { try await $call })")
              } else if (m.ret.isEmpty) {
                w.wl((if (spec.swiftNonThrowing) "" else "try ") + call)
              } else {
                val attempt = if (spec.swiftNonThrowing) "" else "try "
                w.wl(s"call.pointee.setResult($attempt${containerToNative(m.ret.get.resolved, call, nativeMarshal.methodContainerName(ident, m, "Return"), marshal.helperClass(ident) + ".")})")
              }
            }
            w.wl("},")
          }
        }
        w.wl("]")
        w.wl
      }
      // Define the marshaller
      w.w(s"public enum ${marshal.helperClass(ident)}: DjinniSupport.Marshaller").braced {
        w.wl(s"public typealias SwiftType = ${marshal.fqTypename(ident, i)}")
        if (typeParams.isEmpty && spec.cppNnType.isEmpty) {
          val helper = spec.swiftxxNamespace + "." + spec.swiftxxClassIdentStyle(ident)
          w.wl(s"public typealias NativeType = $helper.CppType")
          w.wl(s"public static func toNative(_ s: SwiftType) -> NativeType { $helper.toCpp(toCpp(s)) }")
          w.wl(s"public static func fromNative(_ c: NativeType) -> SwiftType { fromCpp($helper.fromCpp(c)) }")
        }
        writeNativeContainers(w, nativeMarshal.interfaceContainers(ident, i), spec.swiftxxNamespace, "")
        for (m <- i.methods; t <- m.ret; value <- nativeMarshal.nativeFutureValue(t.resolved)) {
          val name = nativeMarshal.methodContainerName(ident, m, "Return")
          val native = spec.swiftxxNamespace + "." + name
          val swift = marshal.fqTypename(value)
          w.w(s"static func from${name}Future(_ future: ${native}Future) async throws -> $swift").braced {
            w.wl("return try await awaitNativeFuture({ context, callback, cleanup in")
            w.nested { w.wl("future.subscribe(context, callback, cleanup)") }
            w.wl("}, decode: { raw in")
            w.nested {
              w.wl(s"let result = raw.assumingMemoryBound(to: ${native}Result.self)")
              w.w("if result.pointee.hasError()").braced {
                w.wl("var error = result.pointee.getError()")
                w.wl("try handleCppErrors(&error)")
              }
              w.wl(s"return ${containerFromNative(value, "result.pointee.takeValue()", name)}")
            }
            w.wl("})")
          }
          w.w(s"static func to${name}Future(_ operation: @escaping () async throws -> $swift) -> ${native}Future").braced {
            w.wl(s"let promise = ${native}Promise()")
            w.wl("let future = promise.getFuture()")
            w.w("Task").braced {
              w.w("do").braced {
                w.wl("let value = try await operation()")
                w.wl(s"promise.setValue(${containerToNative(value, "value", name)})")
              }
              w.w("catch").braced { w.wl("promise.setError(nativeFutureError(error))") }
            }
            w.wl("return future")
          }
        }
        w.w("public static func fromCpp(_ c: djinni.swift.AnyValue) -> SwiftType").braced {
          val newProxyBlock = if (nativeClass) {s"{ ${marshal.typename(ident, i)}(c) }"} else if (i.ext.cpp) {s"{ ${marshal.typename(ident, i)}CppProxy(c) as SwiftType }"} else {"{ fatalError(\"n/a\") }"}
          w.wl(s"return cppInterfaceToSwift(c, ${newProxyBlock})")
        }
        w.w("public static func toCpp(_ s: SwiftType) -> djinni.swift.AnyValue").braced {
          val newProxyBlock = if (i.ext.swift) {s"{ ${spec.swiftxxNamespace}.$swiftProxyClassName.make(ctxPtr(s, ${swiftProxyVtbl}), dispatcherProtocalCall)}"} else {"{ fatalError(\"n/a\") }"}
          if (nativeClass) w.wl("return s._djinniHandle")
          else w.wl(s"return swiftInterfaceToCpp(s, ${newProxyBlock})")
        }
      }
      // Define static method stubs
      val staticMethods = i.methods.filter(m => m.static && m.lang.swift)
      if (!staticMethods.isEmpty) {
        val declaration = if (nativeClass) s"extension ${marshal.typename(ident, i)}" else s"public enum ${marshal.typename(ident, i)}Factory"
        w.w(declaration).braced {
          for (m <- staticMethods) {
            w.w(s"public static func ${swiftMethodName(m.ident)}(")
            val factory = marshal.isFactory(ident.name, m)
            if (m.params.nonEmpty && !factory) { w.w("_ ") }
            w.w(m.params.map(p => s"${idSwift.local(p.ident)}: ${marshal.fqParamType(p.ty)}").mkString(", "))
            w.w(s")${methodEffects(m)} -> ${methodReturn(m)}").braced {
              writeNativeCall(w, ident, m)
            }
          }
        }
      }
    })
  }
}

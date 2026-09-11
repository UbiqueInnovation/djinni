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

class SwiftxxGenerator(spec: Spec) extends Generator(spec) {
  val cppMarshal = new CppMarshal(spec)
  val marshal = new SwiftxxMarshal(spec)

  private def nativeParam(t: TypeRef): String =
    if (marshal.isNative(t.resolved)) cppMarshal.fqParamType(t) else "const djinni::swift::AnyValue&"
  private def nativeReturn(t: Option[TypeRef]): String = t match {
    case Some(v) if marshal.nativeFutureValue(v.resolved).nonEmpty => s"djinni::swift::NativeFuture<${cppMarshal.fqTypename(marshal.nativeFutureValue(v.resolved).get)}>"
    case Some(v) if !marshal.isNative(v.resolved) => "djinni::swift::AnyValue"
    case _ => cppMarshal.fqReturnType(t)
  }

  class SwiftRefs(name: String) {
    var swiftHpp = mutable.TreeSet[String]()
    var swiftCpp = mutable.TreeSet[String]()
    swiftHpp.add("#include " + q(spec.swiftxxIncludeCppPrefix + spec.cppFileIdentStyle(name) + "." + spec.cppHeaderExt))
    swiftHpp.add("#include " + q(spec.swiftxxBaseLibIncludePrefix + "djinni_support.hpp"))
    spec.cppNnHeader match {
      case Some(nnHdr) => swiftHpp.add("#include " + nnHdr)
      case _ =>
    }

    def find(ty: TypeRef) { find(ty.resolved) }
    def find(tm: MExpr) {
      tm.args.foreach(find)
      find(tm.base)
    }
    def find(m: Meta) = for(r <- marshal.references(m, name)) r match {
      case ImportRef(arg) => swiftCpp.add("#include " + arg)
      case _ =>
    }
  }

  val writeSwiftCppFile = writeCppFileGeneric(spec.swiftxxOutFolder.get, spec.swiftxxNamespace, spec.swiftxxFileIdentStyle, "") _
  def writeSwiftHppFile(name: String, origin: String, includes: Iterable[String], fwds: Iterable[String], f: IndentWriter => Unit, f2: IndentWriter => Unit = (w => {})) =
    writeHppFileGeneric(spec.swiftxxOutFolder.get, spec.swiftxxNamespace, spec.swiftxxFileIdentStyle)(name, origin, includes, fwds, f, f2)

  override def generateEnum(origin: String, ident: Ident, doc: Doc, e: Enum) {
    val refs = new SwiftRefs(ident.name)
    writeSwiftHppFile(ident, origin, refs.swiftHpp, Nil, w => {
      w.wl(s"using ${spec.swiftxxClassIdentStyle(ident)} = djinni::swift::Enum<${cppMarshal.fqTypename(ident, e)}>;")
    })
    writeSwiftCppFile(ident, origin, List[String](), w => {})
  }

  override def generateRecord(origin: String, ident: Ident, doc: Doc, params: Seq[TypeParam], r: Record) {
    val refs = new SwiftRefs(ident.name)
    r.fields.foreach(f => refs.find(f.ty))
    val helper = marshal.helperClass(ident)
    writeSwiftHppFile(ident, origin, refs.swiftHpp, Nil, w => {
      w.w(s"struct $helper").bracedEnd(";") {
        w.wl(s"using CppType = ${cppMarshal.fqTypename(ident, r)};")
        if (params.isEmpty) {
          for ((field, index) <- r.fields.zipWithIndex;
               (name, tm) <- marshal.nativeContainers(field.ty.resolved, s"NativeField$index")) {
            w.wl(s"using $name = ${cppMarshal.fqTypename(tm)};")
            if (tm.base == MMap) {
              w.wl(s"static void insert$name($name& target, const ${cppMarshal.fqTypename(tm.args.head)}& key, const ${cppMarshal.fqTypename(tm.args(1))}& value) { target.insert_or_assign(key, value); }")
            }
            if ((tm.base == MList || tm.base == MArray) && marshal.isBoolean(tm.args.head)) {
              // vector<bool> exposes a bit proxy rather than a Bool to Swift.
              w.wl(s"static bool read$name(const $name& value, size_t index) { return value[index]; }")
            }
          }
        }
        if (params.isEmpty && !r.fields.forall(f => marshal.isNative(f.ty.resolved))) {
          val args = r.fields.zipWithIndex.map { case (f, index) => (if (marshal.isNative(f.ty.resolved)) cppMarshal.fqParamType(f.ty) else "const djinni::swift::AnyValue&") + s" arg$index" }.mkString(", ")
          w.wl(s"static CppType makeNative($args);")
          for ((f, index) <- r.fields.zipWithIndex if !marshal.isNative(f.ty.resolved)) {
            w.wl(s"static djinni::swift::AnyValue getField$index(const CppType& value);")
          }
        }
        w.wl(s"static djinni::swift::AnyValue fromCpp(const CppType& c);")
        w.wl(s"static CppType toCpp(const djinni::swift::AnyValue& s);")
      }
    })
    writeSwiftCppFile(ident, origin, refs.swiftCpp, w => {
      if (params.isEmpty && !r.fields.forall(f => marshal.isNative(f.ty.resolved))) {
        val args = r.fields.zipWithIndex.map { case (f, index) => (if (marshal.isNative(f.ty.resolved)) cppMarshal.fqParamType(f.ty) else "const djinni::swift::AnyValue&") + s" arg$index" }.mkString(", ")
        val values = r.fields.zipWithIndex.map { case (f, index) => if (marshal.isNative(f.ty.resolved)) s"arg$index" else marshal.toCpp(f.ty, s"arg$index") }.mkString(", ")
        w.w(s"$helper::CppType $helper::makeNative($args)").braced {
          w.wl(s"return CppType($values);")
        }
        for ((f, index) <- r.fields.zipWithIndex if !marshal.isNative(f.ty.resolved)) {
          w.w(s"djinni::swift::AnyValue $helper::getField$index(const CppType& value)").braced {
            w.wl(s"return ${marshal.fromCpp(f.ty, "value." + idCpp.field(f.ident))};")
          }
        }
      }
      w.w(s"djinni::swift::AnyValue ${helper}::fromCpp(const ${cppMarshal.fqTypename(ident, r)}& c)").braced {
        w.wl("auto ret = std::make_shared<djinni::swift::CompositeValue>();")
        for (f <- r.fields) {
          val member = s"c.${idCpp.field(f.ident)}"
          w.wl(s"ret->addValue(${marshal.fromCpp(f.ty, cppMarshal.maybeMove(member, f.ty))});")
        }
        w.wl("return {ret};")
      }
      w.w(s"${cppMarshal.fqTypename(ident, r)} ${helper}::toCpp(const djinni::swift::AnyValue& s)").braced {
        w.wl("auto p = std::get<djinni::swift::CompositeValuePtr>(s);")
        val members = r.fields.view.zipWithIndex.map{case (f, i) => {
          val expr = s"p->getValue($i)"
          s"${marshal.toCpp(f.ty, expr)}"
        }}.mkString(", ")
        w.wl(s"return ${cppMarshal.fqTypename(ident, r)}($members);")
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
    def includeNativeType(tm: MExpr): Unit = {
      if (marshal.isNative(tm) || marshal.nativeFutureValue(tm).nonEmpty) {
        tm.base match {
          case d: MDef => refs.swiftHpp.add("#include " + q(spec.swiftxxIncludeCppPrefix + spec.cppFileIdentStyle(d.name) + "." + spec.cppHeaderExt))
          case e: MExtern =>
            refs.swiftHpp.add("#include " + cppMarshal.resolveExtCppHdr(e.cpp.header))
            if (marshal.nativeFutureValue(tm).nonEmpty) refs.swiftHpp.add("#include " + marshal.resolveExtSwiftxxHdr(e.swiftxx.header))
          case _ =>
        }
        tm.args.foreach(includeNativeType)
      }
    }
    i.methods.filter(m => !m.static || m.lang.swift).foreach { m =>
      (m.params.map(_.ty) ++ m.ret.toSeq).foreach(t => includeNativeType(t.resolved))
    }
    val helper = marshal.helperClass(ident)
    val proxy = idSwift.ty(ident) + "SwiftProxy"
    writeSwiftHppFile(ident, origin, refs.swiftHpp, Nil, w => {
      w.wl(s"using ${spec.swiftxxClassIdentStyle(ident)} = djinni::swift::Interface<${cppMarshal.fqTypename(ident, i)}>;")
      for ((name, tm) <- marshal.interfaceContainers(ident, i)) {
        w.wl(s"using $name = ${cppMarshal.fqTypename(tm)};")
        if (tm.base == MMap) {
          w.wl(s"inline void insert$name($name& target, const ${cppMarshal.fqTypename(tm.args.head)}& key, const ${cppMarshal.fqTypename(tm.args(1))}& value) { target.insert_or_assign(key, value); }")
        }
        if ((tm.base == MList || tm.base == MArray) && marshal.isBoolean(tm.args.head)) {
          w.wl(s"inline bool read$name(const $name& value, size_t index) { return value[index]; }")
        }
      }
      for (m <- i.methods; t <- m.ret; value <- marshal.nativeFutureValue(t.resolved)) {
        val name = marshal.methodContainerName(ident, m, "Return")
        val cpp = cppMarshal.fqTypename(value)
        w.wl(s"using ${name}Future = djinni::swift::NativeFuture<$cpp>;")
        w.wl(s"using ${name}Promise = djinni::swift::NativePromise<$cpp>;")
        w.wl(s"using ${name}Result = djinni::swift::NativeResult<$cpp>;")
      }
      if (i.ext.cpp) {
        w.wl
        i.methods.filter(m => !m.static || (m.static && m.lang.swift)).foreach(m => {
          val args = (if (m.static) Seq.empty else Seq("const djinni::swift::AnyValue& instance")) ++ m.params.map(p => nativeParam(p.ty) + " " + idCpp.local(p.ident))
          w.wl(s"djinni::swift::NativeResult<${nativeReturn(m.ret)}> ${idSwift.ty(ident)}_${idSwift.method(m.ident)}Native(${args.mkString(", ")}) noexcept;")
        })
      }
      if (i.ext.swift) {
        for (m <- i.methods.filter(!_.static)) {
          w.w(s"struct ${proxy}_${idSwift.method(m.ident)}Call").bracedEnd(";") {
            for ((p, index) <- m.params.zipWithIndex) {
              val argType = if (marshal.isNative(p.ty.resolved)) cppMarshal.fqReturnType(Some(p.ty)) else "djinni::swift::AnyValue"
              w.wl(s"$argType arg$index;")
            }
            if (m.ret.nonEmpty) {
              val result = nativeReturn(m.ret)
              w.wl(s"std::optional<$result> result;")
              w.wl(s"void setResult($result value) { result = std::move(value); }")
            }
          }
        }
        w.wl
        w.w(s"class $proxy: public ${cppMarshal.fqTypename(ident, i)}, public djinni::swift::ProtocolWrapper").bracedEnd(";") {
          w.wlOutdent("public:")
          w.wl(s"$proxy(void* instance, djinni::swift::DispatchFunc dispatcher): ProtocolWrapper(instance, dispatcher) {}")
          w.wl("static djinni::swift::AnyValue make(void* instance, djinni::swift::DispatchFunc dispatcher);")
          i.methods.foreach(m => {
            val ret = cppMarshal.fqReturnType(m.ret)
            val params = m.params.map(p => cppMarshal.fqParamType(p.ty) + " " + idCpp.local(p.ident))
            if (!m.static) {
              val constFlag = if (m.const) " const" else ""
              w.wl(s"$ret ${idCpp.method(m.ident)}${params.mkString("(", ", ", ")")}$constFlag override;")
            }
          })
        }
      }
    })
    writeSwiftCppFile(ident, origin, refs.swiftCpp, w => {
      if (i.ext.cpp) {
        i.methods.filter(m => !m.static || (m.static && m.lang.swift)).foreach(m => {
          val args = (if (m.static) Seq.empty else Seq("const djinni::swift::AnyValue& instance")) ++ m.params.map(p => nativeParam(p.ty) + " " + idCpp.local(p.ident))
          w.w(s"djinni::swift::NativeResult<${nativeReturn(m.ret)}> ${idSwift.ty(ident)}_${idSwift.method(m.ident)}Native(${args.mkString(", ")}) noexcept try").braced {
            val receiver = if (m.static) s"${cppMarshal.fqTypename(ident, i)}::" else s"static_cast<${cppMarshal.fqTypename(ident, i)}*>(std::get<djinni::swift::InterfaceValue>(instance).ptr.get())->"
            val callArgs = m.params.map(p => if (marshal.isNative(p.ty.resolved)) idCpp.local(p.ident) else marshal.toCpp(p.ty, idCpp.local(p.ident)))
            val call = receiver + idCpp.method(m.ident) + callArgs.mkString("(", ", ", ")")
            m.ret match {
              case Some(t) if marshal.nativeFutureValue(t.resolved).nonEmpty => w.wl(s"return ${nativeReturn(m.ret)}($call);")
              case Some(t) if marshal.isNative(t.resolved) => w.wl(s"return $call;")
              case Some(t) => w.wl(s"return ${marshal.fromCpp(t, call)};")
              case None => w.wl(s"$call;"); w.wl("return {};")
            }
          }
          w.w("catch (const std::exception& e)").braced {
            w.wl("return djinni::swift::ErrorValue{e.what(), std::current_exception()};")
          }
          w.w("catch (...)").braced {
            w.wl("return djinni::swift::ErrorValue{\"Unknown C++ exception\", std::current_exception()};")
          }
        })
      }
      if (i.ext.swift) {
        w.w(s"djinni::swift::AnyValue ${proxy}::make(void* instance, djinni::swift::DispatchFunc dispatcher)").braced {
          w.wl(s"return {std::make_shared<$proxy>(instance, dispatcher)};")
        }
        for ((m, idx) <- i.methods.filter(!_.static).view.zipWithIndex) {
          val ret = cppMarshal.fqReturnType(m.ret)
          val params = m.params.map(p => cppMarshal.fqParamType(p.ty) + " " + idCpp.local(p.ident))
          val constFlag = if (m.const) " const" else ""
          w.w(s"$ret $proxy::${idCpp.method(m.ident)}${params.mkString("(", ", ", ")")}$constFlag").braced {
            val values = m.params.map(p => if (marshal.isNative(p.ty.resolved)) idCpp.local(p.ident) else marshal.fromCpp(p.ty, idCpp.local(p.ident)))
            w.wl(s"${proxy}_${idSwift.method(m.ident)}Call call{${values.mkString(", ")}};")
            w.wl(s"callProtocol($idx, &call);")
            m.ret.foreach(t => {
              val result = "std::move(call.result.value())"
              w.wl("return " + (if (marshal.nativeFutureValue(t.resolved).nonEmpty) result + ".take()" else if (marshal.isNative(t.resolved)) result else marshal.toCpp(t, result)) + ";")
            })
          }
        }
      }
    })
  }
}

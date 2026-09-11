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

import djinni.ast._
import djinni.generatorTools._
import djinni.meta._

class SwiftxxMarshal(spec: Spec) extends Marshal(spec) {

  // Types with a direct Swift/C++ conversion; external generators advertise support in YAML.
  def isNative(tm: MExpr): Boolean = tm.base match {
    case _: MPrimitive => true
    case MBinary => true
    case MString => !spec.cppUseWideStrings
    case MList | MArray | MOptional | MMap | MSet => isNativeContainer(tm)
    case e: MExtern => e.swift.native && tm.args.isEmpty
    case d: MDef => d.body match {
      case _: Enum => true
      case _: Record => tm.args.isEmpty
      case _: Interface => tm.args.isEmpty && spec.cppNnType.isEmpty
      case _ => false
    }
    case _ => false
  }

  def nativeFutureValue(tm: MExpr): Option[MExpr] = tm.base match {
    case e: MExtern if e.swift.translator == "FutureMarshaller" && tm.args.size == 1 && isNative(tm.args.head) => Some(tm.args.head)
    case _ => None
  }

  def isInterface(tm: MExpr): Boolean = tm.base match {
    case d: MDef => d.defType == DInterface
    case e: MExtern => e.defType == DInterface
    case _ => false
  }

  def isBoolean(tm: MExpr): Boolean = tm.base match {
    case p: MPrimitive => p.idlName == "bool"
    case _ => false
  }

  // Concrete aliases expose STL specializations to Swift's importer.
  def isNativeContainer(tm: MExpr): Boolean = tm.base match {
    case MList | MArray | MSet => isNative(tm.args.head)
    case MMap => tm.args.forall(isNative)
    case MOptional if spec.cppOptionalTemplate == "std::optional" => isNative(tm.args.head)
    case _ => false
  }
  def nativeContainers(tm: MExpr, name: String): Seq[(String, MExpr)] = {
    if (!isNativeContainer(tm)) Seq.empty
    else if (tm.base == MMap) Seq(name -> tm) ++ nativeContainers(tm.args.head, name + "Key") ++ nativeContainers(tm.args(1), name + "Value")
    else Seq(name -> tm) ++ nativeContainers(tm.args.head, name + "Element")
  }

  def methodContainerName(owner: String, method: Interface.Method, position: String): String =
    idSwift.ty(owner) + "_" + idSwift.method(method.ident) + position

  def interfaceContainers(owner: String, i: Interface): Seq[(String, MExpr)] =
    i.methods.filter(m => !m.static || m.lang.swift).flatMap { m =>
      val args = m.params.zipWithIndex.flatMap { case (p, index) => nativeContainers(p.ty.resolved, methodContainerName(owner, m, s"Arg$index")) }
      args ++ m.ret.toSeq.flatMap(t => nativeContainers(nativeFutureValue(t.resolved).getOrElse(t.resolved), methodContainerName(owner, m, "Return")))
    }

  override def typename(tm: MExpr): String = throw new AssertionError("not applicable")
  def typename(name: String, ty: TypeDef): String = throw new AssertionError("not applicable")

  override def fqTypename(tm: MExpr): String = throw new AssertionError("not applicable")
  def fqTypename(name: String, ty: TypeDef): String = throw new AssertionError("not applicable")

  override def paramType(tm: MExpr): String = throw new AssertionError("not applicable")
  override def fqParamType(tm: MExpr): String = throw new AssertionError("not applicable")

  override def returnType(ret: Option[TypeRef]): String = throw new AssertionError("not applicable")
  override def fqReturnType(ret: Option[TypeRef]): String = throw new AssertionError("not applicable")

  override def fieldType(tm: MExpr): String = throw new AssertionError("not applicable")
  override def fqFieldType(tm: MExpr): String = throw new AssertionError("not applicable")

  override def toCpp(tm: MExpr, expr: String): String = s"${helperClass(tm)}::toCpp(${expr})"
  override def fromCpp(tm: MExpr, expr: String): String = s"${helperClass(tm)}::fromCpp(${expr})"

  def references(m: Meta, exclude: String = ""): Seq[SymbolReference] = m match {
    case p: MProtobuf => List()
    case d: MDef => List(ImportRef(include(d.name)))
    case e: MExtern => List(ImportRef(resolveExtSwiftxxHdr(e.swiftxx.header)))
    case _ => List()
  }

  def include(ident: String) = q(spec.swiftxxIncludePrefix + spec.swiftxxFileIdentStyle(ident) + "." + spec.cppHeaderExt)

  def resolveExtSwiftxxHdr(path: String) = {
    path.replaceAll("\\$", spec.swiftxxBaseLibIncludePrefix);
  }

  def helperClass(name: String) = spec.swiftxxClassIdentStyle(name)
  private def helperClass(tm: MExpr): String = helperName(tm) + helperTemplates(tm)
  def helperName(tm: MExpr): String = tm.base match {
    case d: MDef => withNs(Some(spec.swiftxxNamespace), helperClass(d.name))
    case e: MExtern => e.swiftxx.translator
    case o => withNs(Some("djinni::swift"), o match {
      case p: MPrimitive => p.idlName match {
        case "i8" => "I8"
        case "i16" => "I16"
        case "i32" => "I32"
        case "i64" => "I64"
        case "f32" => "F32"
        case "f64" => "F64"
        case "bool" => "Bool"
      }
      case MOptional => "Optional"
      case MBinary => "Binary"
      case MString => if (spec.cppUseWideStrings) "WString" else "String"
      case MDate => "Date"
      case MList => "List"
      case MSet => "Set"
      case MMap => "Map"
      case p: MProtobuf => p.body.swift match {
        case Some(o) => "Protobuf" + "<" + withNs(Some(p.body.cpp.ns), p.name) + ">"
        case None => s"[invalid protobuf: ${p.name}]"
      }
      case MArray => "Array"
      case d: MDef => throw new AssertionError("unreachable")
      case e: MExtern => throw new AssertionError("unreachable")
      case p: MParam => throw new AssertionError("not applicable")
      case MVoid => "Void"
    })
  }
  private def helperTemplates(tm: MExpr): String = {
    def f() = if(tm.args.isEmpty) "" else tm.args.map(helperClass).mkString("<", ", ", ">")
    tm.base match {
      case MOptional =>
        assert(tm.args.size == 1)
        val argHelperClass = helperClass(tm.args.head)
        s"<${spec.cppOptionalTemplate}, $argHelperClass>"
      case MList | MSet | MArray =>
        assert(tm.args.size == 1)
        f
      case _ => f
    }
  }
}

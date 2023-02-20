package djinni

import djinni.ast.Record.DerivingType
import djinni.ast._
import djinni.generatorTools._
import djinni.meta._
import djinni.writer.IndentWriter

import scala.collection.mutable

class KotlinGenerator(spec: Spec) extends Generator(spec) {

  val javaAnnotationHeader = spec.javaAnnotation.map(pkg => '@' + pkg.split("\\.").last)
  val marshal = new KotlinMarshal(spec)

  class JavaRefs() {
    var java = mutable.TreeSet[String]()

    spec.javaAnnotation.foreach(pkg => java.add(pkg))
    spec.javaNullableAnnotation.foreach(pkg => java.add(pkg))
    spec.javaNonnullAnnotation.foreach(pkg => java.add(pkg))

    def find(ty: TypeRef) { find(ty.resolved) }
    def find(tm: MExpr) {
      tm.args.foreach(find)
      find(tm.base)
    }
    def find(m: Meta) = for(r <- marshal.references(m)) r match {
      case ImportRef(arg) => java.add(arg)
      case _ =>
    }
  }

  def writeKotlinFile(ident: String, origin: String, refs: Iterable[String], f: IndentWriter => Unit) {
    createFile(spec.kotlinOutFolder.get, idJava.ty(ident) + ".kt", (w: IndentWriter) => {
      w.wl("// AUTOGENERATED FILE - DO NOT MODIFY!")
      w.wl("// This file was generated by Djinni from " + origin)
      w.wl
      spec.javaPackage.foreach(s => w.wl(s"package $s").wl)
      if (refs.nonEmpty) {
        refs.foreach(s => w.wl(s"import $s"))
        w.wl
      }
      f(w)
    })
  }

  def generateJavaConstants(w: IndentWriter, consts: Seq[Const]) = {

    def writeJavaConst(w: IndentWriter, ty: TypeRef, v: Any): Unit = v match {
      case l: Long if marshal.fieldType(ty).equalsIgnoreCase("long") => w.w(l.toString + "L")
      case l: Long => w.w(l.toString)
      case d: Double if marshal.fieldType(ty).equalsIgnoreCase("float") => w.w(d.toString + "f")
      case d: Double => w.w(d.toString)
      case b: Boolean => w.w(if (b) "true" else "false")
      case s: String => w.w(s)
      case e: EnumValue =>  w.w(s"${marshal.typename(ty)}.${idJava.enum(e)}")
      case v: ConstRef => w.w(idJava.const(v))
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
          writeJavaConst(w, f.ty, vMap.apply(f.ident.name))
          w.w(" /* " + idJava.field(f.ident) + " */ ")
        }
        w.w(")")
        w.decrease()
      }
    }
    for (c <- consts) {
      writeDoc(w, c.doc)
      javaAnnotationHeader.foreach(w.wl)

      w.w(s"val ${idJava.const(c.ident)}: ${marshal.fieldType(c.ty)} = ")
      writeJavaConst(w, c.ty, c.value)
      w.wl
    }
  }

  private val moduleClass: String = spec.javaIdentStyle.ty(spec.moduleName) + "Module"

  override def generateModule(decls: Seq[InternTypeDecl]) {
    if (spec.jniUseOnLoad) {
      writeKotlinFile(moduleClass, s"${spec.moduleName} Module", List.empty, w => {
        w.w(s"class $moduleClass").braced {
          w.w("companion object").braced {
            w.w("init").braced {
              w.w("if (System.getProperty(\"java.vm.vendor\") == \"The Android Project\")").braced {
                w.wl("Guard.initialize();")
              }
            }
          }
          w.w("private class Guard").braced {
            w.w("companion object").braced {
              w.wl("@JvmStatic")
              w.wl("external fun initialize()")
            }
          }
        }
      })
    }
  }

  override def generateEnum(origin: String, ident: Ident, doc: Doc, e: Enum) {
    val refs = new JavaRefs()

    writeKotlinFile(ident, origin, refs.java, w => {
      writeDoc(w, doc)
      javaAnnotationHeader.foreach(w.wl)
      w.w(s"enum class ${marshal.typename(ident, e)}").braced {
        for (o <- normalEnumOptions(e)) {
          writeDoc(w, o.doc)
          w.wl(idJava.enum(o.ident) + ",")
        }
      }
    })
  }

  override def generateInterface(origin: String, ident: Ident, doc: Doc, typeParams: Seq[TypeParam], i: Interface) {
    val refs = new JavaRefs()

    i.methods.map(m => {
      m.params.map(p => refs.find(p.ty))
      m.ret.foreach(refs.find)
    })
    i.consts.map(c => {
      refs.find(c.ty)
    })
    if (i.ext.cpp) {
      refs.java.add("java.util.concurrent.atomic.AtomicBoolean")
      refs.java.add("com.snapchat.djinni.NativeObjectManager")
    }

    def writeModuleInitializer(w: IndentWriter) = {
      if (spec.jniUseOnLoad) {
        w.w("init").braced {
          w.w("try").braced {
            w.wl(s"Class.forName(${q(spec.javaPackage.getOrElse("") + "." + moduleClass)})")
          }
          w.w("catch (e: ClassNotFoundException)").braced {
            w.wl(s"throw IllegalStateException(${q("Failed to initialize djinni module")}, e)")
          }
        }
      }
    }

    writeKotlinFile(ident, origin, refs.java, w => {
      val javaClass = marshal.typename(ident, i)
      val typeParamList = javaTypeParams(typeParams)
      writeDoc(w, doc)

      val statics = i.methods.filter(m => m.static)

      javaAnnotationHeader.foreach(w.wl)

      // if no static and no cpp will use interface instead of abstract class
      val genJavaInterface = spec.javaGenInterface && !statics.nonEmpty && !i.ext.cpp
      val classPrefix = if (genJavaInterface) "interface" else "abstract class"
      val methodPrefix = if (genJavaInterface) "" else "abstract "
      val innerClassAccessibility = if (genJavaInterface) "" else "private "

      w.w(s"$classPrefix $javaClass$typeParamList").braced {
        // Implement the interface's static methods as direct calls to the exported cpp functions.
        if (i.consts.nonEmpty || i.methods.exists(_.static)) {
          w.wl
          w.w("companion object").braced {
            writeModuleInitializer(w)

            generateJavaConstants(w, i.consts)

            val skipFirst = SkipFirst()
            for (m <- statics) {
              skipFirst {
                w.wl
              }
              val ret = marshal.returnType(m.ret)
              val params = m.params.map(p => {
                idJava.local(p.ident) + ": " + marshal.paramType(p.ty)
              })
              w.wl("@JvmStatic")
              w.wl("external fun " + idJava.method(m.ident) + params.mkString("(", ", ", ")") + ": " + ret)
            }
          }
        }

        for (m <- i.methods if !m.static) {
          w.wl
          writeMethodDoc(w, m, idJava.local)
          val ret = marshal.returnType(m.ret)
          val returnSignature = if (ret == "Unit") "" else s": $ret"
          val params = m.params.map(p => {
            idJava.local(p.ident) + ": " + marshal.paramType(p.ty)
          })
          w.wl(s"${methodPrefix}fun " + idJava.method(m.ident) + params.mkString("(", ", ", ")") + returnSignature)
        }

        if (i.ext.cpp) {
          w.wl
          javaAnnotationHeader.foreach(w.wl)
          w.w(s"${innerClassAccessibility}class CppProxy$typeParamList : $javaClass$typeParamList").braced {
            w.wl("private val nativeRef: Long")
            w.wl("private val destroyed: AtomicBoolean = AtomicBoolean(false)")
            w.wl
            w.w(s"private constructor(nativeRef: Long)").braced {
              w.wl("if (nativeRef == 0L) error(\"nativeRef is zero\")")
              w.wl(s"this.nativeRef = nativeRef")
              w.wl("NativeObjectManager.register(this, nativeRef)")
            }
            w.wl
            w.wl("external fun nativeDestroy(nativeRef: Long)")

            // Implement the interface's non-static methods.
            for (m <- i.methods if !m.static) {
              w.wl
              val ret = marshal.returnType(m.ret)
              val returnSignature = if (ret == "Unit") "" else s": $ret"
              val returnStmt = m.ret.fold("")(_ => "return ")
              val params = m.params.map(p => idJava.local(p.ident) + ": " + marshal.paramType(p.ty)).mkString(", ")
              val args = m.params.map(p => idJava.local(p.ident)).mkString(", ")
              val meth = idJava.method(m.ident)
              w.w(s"override fun $meth($params)$returnSignature").braced {
                w.wl("assert(!this.destroyed.get()) { error(\"trying to use a destroyed object\") }")
                w.wl(s"${returnStmt}native_$meth(this.nativeRef${preComma(args)})")
              }
              w.wl(s"private external fun native_$meth(_nativeRef: Long${preComma(params)})$returnSignature")
            }
          }
        }
      }
    })
  }

  override def generateRecord(origin: String, ident: Ident, doc: Doc, params: Seq[TypeParam], r: Record) {
    val refs = new JavaRefs()
    r.fields.foreach(f => refs.find(f.ty))
    if (spec.kotlinRecordsSerializable)
      refs.java.add(s"java.io.Serializable")

    val javaName = if (r.ext.java) (ident.name + "_base") else ident.name

    writeKotlinFile(javaName, origin, refs.java, w => {
      writeDoc(w, doc)
      javaAnnotationHeader.foreach(w.wl)
      val self = marshal.typename(javaName, r)

      val interfaces = scala.collection.mutable.ArrayBuffer[String]()
      if (r.derivingTypes.contains(DerivingType.Ord))
          interfaces += s"Comparable<$self>"
      if (spec.kotlinRecordsSerializable)
          interfaces += s"Serializable"
      val implementsSection = if (interfaces.isEmpty) "" else " : " + interfaces.mkString(", ")
      w.wl(s"data class ${self + javaTypeParams(params)}(")
      w.nested {
        for (f <- r.fields) {
          w.wl(s"var ${idJava.field(f.ident)}: ${marshal.fieldType(f.ty)},")
        }
      }
      w.w(s")$implementsSection")

      if (r.consts.nonEmpty || r.derivingTypes.contains(DerivingType.Ord)) {
        w.braced {
          if (r.consts.nonEmpty) {
            w.wl
            w.w("companion object").braced {
              generateJavaConstants(w, r.consts)
            }
          }

          if (r.derivingTypes.contains(DerivingType.Ord)) {
            def primitiveCompare(ident: Ident) {
              w.wl(s"if (this.${idJava.field(ident)} < other.${idJava.field(ident)}) {").nested {
                w.wl(s"tempResult = -1;")
              }
              w.wl(s"} else if (this.${idJava.field(ident)} > other.${idJava.field(ident)}) {").nested {
                w.wl(s"tempResult = 1;")
              }
              w.wl(s"} else {").nested {
                w.wl(s"tempResult = 0;")
              }
              w.wl("}")
            }

            w.wl
            w.w(s"override fun compareTo(other: $self): Int").braced {
              w.wl("var tempResult = 0")
              for (f <- r.fields) {
                f.ty.resolved.base match {
                  case MString | MDate => w.wl(s"tempResult = this.${idJava.field(f.ident)}.compareTo(other.${idJava.field(f.ident)})")
                  case t: MPrimitive => primitiveCompare(f.ident)
                  case df: MDef => df.defType match {
                    case DRecord => w.wl(s"tempResult = this.${idJava.field(f.ident)}.compareTo(other.${idJava.field(f.ident)})")
                    case DEnum => w.w(s"tempResult = this.${idJava.field(f.ident)}.compareTo(other.${idJava.field(f.ident)})")
                    case _ => throw new AssertionError("Unreachable")
                  }
                  case e: MExtern => e.defType match {
                    case DRecord => if (e.java.reference) w.wl(s"tempResult = this.${idJava.field(f.ident)}.compareTo(other.${idJava.field(f.ident)})") else primitiveCompare(f.ident)
                    case DEnum => w.w(s"tempResult = this.${idJava.field(f.ident)}.compareTo(other.${idJava.field(f.ident)})")
                    case _ => throw new AssertionError("Unreachable")
                  }
                  case _ => throw new AssertionError("Unreachable")
                }
                w.w("if (tempResult != 0)").braced {
                  w.wl("return tempResult")
                }
              }
              w.wl("return 0")
            }
          }
        }
      }
    })
  }

  def javaTypeParams(params: Seq[TypeParam]): String =
    if (params.isEmpty) "" else params.map(p => idJava.typeParam(p.ident)).mkString("<", ", ", ">")

}

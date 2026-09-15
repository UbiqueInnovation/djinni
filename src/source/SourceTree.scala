package djinni

import java.io.{File, FileInputStream, StringWriter, Writer}
import java.nio.file.FileSystems
import scala.collection.JavaConversions._
import scala.collection.mutable
import org.yaml.snakeyaml.Yaml
import djinni.ast._
import djinni.generatorTools._

/** Directory discovery, source ownership, and the deliberately small project configuration. */
class SourceTree(rootFile: File, configFile: Option[File], exclusions: Seq[String], manifestFile: Option[File]) {
  val root = rootFile.getCanonicalFile
  private def fail(message: String): Nothing = throw GenerateException(message)
  private def mapping(value: Any): Map[String, Any] = value match {
    case m: java.util.Map[_, _] => m.toSeq.map {
      case (k: String, v) => k -> v
      case _ => fail("Project configuration keys must be strings")
    }.toMap
    case _ => fail("Expected a mapping in project configuration")
  }
  private def strings(value: Any): Seq[String] = value match {
    case xs: java.util.List[_] => xs.map {
      case s: String => s
      case _ => fail("Expected a list of strings in project configuration")
    }.toVector
    case _ => fail("Expected a list in project configuration")
  }
  private val config = configFile.map { file =>
    val in = new FileInputStream(file)
    try mapping(new Yaml().load(in)) finally in.close()
  }.getOrElse(Map.empty[String, Any])
  private def checkKeys(m: Map[String, Any], allowed: Set[String]): Unit =
    m.keys.filterNot(allowed).foreach(k => fail("Unknown project setting: " + k))
  checkKeys(config, Set("exclude", "rules", "externs"))
  private val excludes = exclusions ++ config.get("exclude").map(strings).getOrElse(Nil)
  private val rules = config.get("rules").map {
    case xs: java.util.List[_] => xs.map(mapping).toVector
    case _ => fail("Project rules must be a list")
  }.getOrElse(Vector.empty)
  private val externs = config.get("externs").map(mapping).getOrElse(Map.empty)
  private val outputNames = Set("cpp", "java", "kotlin", "jni", "objc", "objcpp", "wasm", "ts", "yaml", "kmp")
  rules.foreach { r =>
    checkKeys(r, Set("path", "disable", "java-package", "ts-module"))
    if (!r.get("path").exists(_.isInstanceOf[String])) fail("Each project rule needs a path glob")
    r.get("disable").map(strings).getOrElse(Nil).filterNot(outputNames).foreach(x => fail("Unknown output: " + x))
  }
  private def relative(file: File): String = {
    val path = file.getCanonicalFile.toPath
    // Explicit imports outside the tree use the base output settings.
    if (path.startsWith(root.toPath)) root.toPath.relativize(path).toString.replace(File.separatorChar, '/')
    else file.getName
  }
  private def matches(pattern: String, path: String) = FileSystems.getDefault.getPathMatcher("glob:" + pattern).matches(new File(path).toPath)
  private def discover(): Seq[File] = {
    if (!root.isDirectory) fail("IDL root is not a directory: " + root)
    val visited = mutable.Set[File]()
    val found = mutable.Set[File]()
    def walk(file: File): Unit = {
      val canonical = file.getCanonicalFile
      if (!excludes.exists(matches(_, relative(file)))) {
        if (file.isDirectory && visited.add(canonical)) {
          val children = Option(file.listFiles()).getOrElse(fail("Cannot read directory: " + file))
          children.sortBy(_.getName).foreach(walk)
        } else if (file.isFile && file.getName.endsWith(".djinni")) found += canonical
      }
    }
    walk(root)
    found.toSeq.sortBy(_.getPath)
  }
  private var parser: Parser = null
  def parse(includePaths: List[String], writer: Option[Writer]): (Seq[TypeDecl], Seq[String]) = {
    parser = new Parser(includePaths, directoryMode = true)
    val types = mutable.ArrayBuffer[TypeDecl]()
    val flags = mutable.ArrayBuffer[String]()
    discover().foreach { file =>
      if (!parser.visitedFiles(file)) {
        val (t, f) = parser.parseFile(file, writer)
        types ++= t
        flags ++= f
      }
    }
    if (flags.nonEmpty) fail("Directory mode does not accept @flag; pass common options on the CLI and exceptions in --idl-project")
    (types.toVector, Nil)
  }

  private var sourceSpecs = Map.empty[File, Spec]

  private def sourceSpec(base: Spec, file: File): Spec = {
    val path = relative(file)
    val parent = Option(new File(path).getParent).getOrElse("").replace(File.separatorChar, '/')
    val suffix = if (parent.isEmpty) "" else parent + "/"
    def folder(f: Option[File]) = f.map(new File(_, parent))
    def cppPrefix(from: Option[File], explicit: String): String = {
      if (explicit.nonEmpty || from.isEmpty || base.cppHeaderOutFolder.isEmpty) explicit
      else {
        val path = from.get.getCanonicalFile.toPath.relativize(base.cppHeaderOutFolder.get.getCanonicalFile.toPath).toString.replace(File.separatorChar, '/')
        if (path.isEmpty) "" else path + "/"
      }
    }
    val pkg = (base.javaPackage.toSeq.filter(_.nonEmpty) ++ parent.split('/').filter(_.nonEmpty)).mkString(".")
    var spec = base.copy(javaPackage = if (pkg.isEmpty) None else Some(pkg),
      javaOutFolder = folder(base.javaOutFolder), kotlinOutFolder = folder(base.kotlinOutFolder),
      jniOutFolder = folder(base.jniOutFolder), jniHeaderOutFolder = folder(base.jniHeaderOutFolder),
      jniIncludePrefix = base.jniIncludePrefix + suffix,
      jniIncludeCppPrefix = cppPrefix(folder(base.jniHeaderOutFolder), base.jniIncludeCppPrefix),
      wasmIncludeCppPrefix = cppPrefix(base.wasmOutFolder, base.wasmIncludeCppPrefix),
      tsOutFolder = folder(base.tsOutFolder), tsModule = file.getName.stripSuffix(".djinni"),
      tsImportPrefix = base.tsImportPrefix + suffix, moduleName = file.getName.stripSuffix(".djinni"))
    for (rule <- rules if matches(rule("path").toString, path)) {
      rule.get("java-package").foreach(p => spec = spec.copy(javaPackage = Some(p.toString)))
      rule.get("ts-module").foreach(m => spec = spec.copy(tsModule = m.toString))
      for (disabled <- rule.get("disable").map(strings).getOrElse(Nil)) spec = disabled match {
        case "cpp" => spec.copy(cppOutFolder = None, cppHeaderOutFolder = None)
        case "java" => spec.copy(javaOutFolder = None)
        case "kotlin" => spec.copy(kotlinOutFolder = None)
        case "jni" => spec.copy(jniOutFolder = None, jniHeaderOutFolder = None)
        case "objc" => spec.copy(objcOutFolder = None, objcSwiftBridgingHeaderWriter = None)
        case "objcpp" => spec.copy(objcppOutFolder = None)
        case "wasm" => spec.copy(wasmOutFolder = None)
        case "ts" => spec.copy(tsOutFolder = None)
        case "yaml" => spec.copy(yamlOutFolder = None)
        case "kmp" => spec.copy(kotlinKmpCommonOutFolder = None, kotlinKmpAndroidOutFolder = None, kotlinKmpIosOutFolder = None)
      }
    }
    if ((spec.javaOutFolder.nonEmpty || spec.kotlinOutFolder.nonEmpty || spec.jniOutFolder.nonEmpty) &&
        spec.javaPackage.exists(p => !p.split("\\.", -1).forall(_.matches("[A-Za-z_][A-Za-z_0-9]*"))))
      fail("Invalid Java/Kotlin package for " + file + ": " + spec.javaPackage.get + "; use a java-package path rule")
    if (spec.tsModule.isEmpty || spec.tsModule.contains("/") || spec.tsModule.contains("\\") || spec.tsModule == "." || spec.tsModule == "..")
      fail("Invalid TypeScript module filename for " + file + ": " + spec.tsModule)
    spec
  }

  def resolveExterns(idl: Seq[TypeDecl], base: Spec, writer: Option[Writer]): Seq[TypeDecl] = {
    val internal = idl.collect { case t: InternTypeDecl => t }
    val byFile = internal.groupBy(_.ident.file.getCanonicalFile)
    sourceSpecs = byFile.keys.map(file => file -> sourceSpec(base, file)).toMap
    val ownedYaml = internal.flatMap { t =>
      val s = sourceSpecs(t.ident.file)
      s.yamlOutFolder.toSeq.flatMap(out => (s.yamlOutFile.toSeq :+ (s.yamlPrefix + t.ident.name + ".yaml")).map(name => new File(out, name).getCanonicalFile -> t.ident.file))
    }.groupBy(_._1).map { case (path, owners) => path -> owners.map(_._2).toSet }
    val conventionalOwners = internal.flatMap { t =>
      Seq(t.ident.name, base.yamlPrefix + t.ident.name, t.ident.file.getName.stripSuffix(".djinni")).distinct.map(_ -> t)
    }.groupBy(_._1).map { case (name, entries) => name -> entries.map(_._2) }
    val explicit = externs.map { case (ref, source) =>
      val target = new File(root, source.toString).getCanonicalFile
      if (!byFile.contains(target)) fail("Extern mapping does not name a discovered source: " + source)
      new File(root, ref).getCanonicalFile -> target
    }
    explicit.foreach { case (path, source) =>
      if (ownedYaml.get(path).exists(owners => !owners(source)))
        fail("Conflicting extern ownership for " + path + ": " + source + " and " + ownedYaml(path).toSeq.sortBy(_.getPath).mkString(", "))
    }
    val additional = mutable.ArrayBuffer[TypeDecl]()
    for (file <- parser.deferredExterns.toSeq.sortBy(_.getPath)) {
      val stem = file.getName.stripSuffix(".yaml").stripSuffix(".yml")
      val candidates = conventionalOwners.getOrElse(stem, Nil)
      if (explicit.contains(file) || ownedYaml.contains(file)) {
        // Source owns this YAML path, even when a stale generated YAML file exists.
      } else if (!file.exists && candidates.nonEmpty) {
        if (candidates.map(_.ident.file).distinct.size != 1)
          fail("Ambiguous local extern " + file + ": " + candidates.map(_.ident.loc).mkString(", ") + "; use project externs mapping")
      } else {
        val types = parser.parseExternFile(file, writer)
        val conflicts = types.flatMap { t =>
          val name = t match {
            case e: ExternTypeDecl => e.ident.name.stripPrefix(e.properties.getOrElse("prefix", "").toString)
            case _ => t.ident.name
          }
          internal.filter(i => i.ident.name == name || i.ident.name == t.ident.name).map(i => t.ident.loc + " and " + i.ident.loc)
        }
        if (conflicts.nonEmpty) fail("Conflicting external ownership: " + conflicts.mkString(", ") + "; use project externs mapping for local YAML")
        additional ++= types
      }
    }
    (idl ++ additional).sortBy(_.ident.name)
  }

  def generate(idl: Seq[TypeDecl], base: Spec, lists: Seq[(File, Writer)]): Unit = {
    val transaction = new TreeOutput(manifestFile.getOrElse(new File(root, ".djinni-manifest")), root)
    val internal = idl.collect { case t: InternTypeDecl => t }
    val specs = internal.map(t => t.ident.name -> sourceSpecs(t.ident.file)).toMap
    val groups = internal.groupBy(_.ident.file).toSeq.sortBy(_._1.getCanonicalPath)
    for ((file, types) <- groups) {
      val spec = sourceSpecs(file).copy(typeSpecs = specs, outputTransaction = Some(transaction), objcSwiftBridgingHeaderWriter = None,
        yamlOutFolder = if (base.yamlOutFile.nonEmpty) None else sourceSpecs(file).yamlOutFolder)
      generatorTools.generate(types.sortBy(_.ident.name), spec).foreach(fail)
    }
    if (base.yamlOutFile.nonEmpty && base.yamlOutFolder.nonEmpty) {
      new YamlGenerator(base.copy(typeSpecs = specs, outputTransaction = Some(transaction))).generate(
        internal.filter(t => specs(t.ident.name).yamlOutFolder.nonEmpty))
    }
    if (base.objcSwiftBridgingHeaderName.nonEmpty && base.objcOutFolder.nonEmpty) {
      val out = new StringWriter()
      val headerSpec = base.copy(objcSwiftBridgingHeaderWriter = Some(out))
      SwiftBridgingHeaderGenerator.writeAutogenerationWarning(base.objcSwiftBridgingHeaderName.get, out)
      SwiftBridgingHeaderGenerator.writeBridgingVars(base.objcSwiftBridgingHeaderName.get, out)
      new SwiftBridgingHeaderGenerator(headerSpec).generate(internal.filter(t => specs(t.ident.name).objcOutFolder.nonEmpty).sortBy(_.ident.name))
      val header = new File(base.objcOutFolder.get, base.objcSwiftBridgingHeaderName.get + ".h")
      base.outFileListWriter.foreach(_.write(header.getPath + "\n"))
      transaction.add(header, out.toString.getBytes("UTF-8"))
    }
    lists.foreach { case (file, writer) => transaction.add(file, (writer.toString.split("\n").filter(_.nonEmpty).sorted.mkString("", "\n", "\n")).getBytes("UTF-8")) }
    if (!base.skipGeneration) transaction.commit()
  }
}

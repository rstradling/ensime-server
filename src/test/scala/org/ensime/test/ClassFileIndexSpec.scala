package org.ensime.test

import java.io.{ File => JFile }
import org.scalatest.Spec
import org.scalatest.matchers.ShouldMatchers
import org.ensime.config.ProjectConfig
import org.ensime.indexer.ClassFileIndex
import org.ensime.util.SExp
import org.ensime.util.FileUtils._
import org.ensime.util.CanonFile
import scala.tools.nsc.{ Global, Settings }
import scala.tools.nsc.io.{ Jar, File, Directory, Path, AbstractFile, PlainFile, ZipArchive }
import scala.tools.nsc.reporters.{ConsoleReporter}

class ClassFileIndexSpec extends Spec with ShouldMatchers{

  def config(s:String): ProjectConfig = {
    ProjectConfig.fromSExp(SExp.read(s)) match{
      case Right(c) => c
      case Left(t) => throw t
    }
  }

  def setup_sources() = {
    val tmpdir = Path(createTemporaryDirectory)
    val src = tmpdir / Directory("src")
    val target = tmpdir / Directory("target")
    val jars = tmpdir / Directory("jars")
    src.createDirectory()
    target.createDirectory()
    jars.createDirectory()

    val sources = List(
      "com/example1/Duplicate.scala" -> """package com.example1
class Test1 {}
class Test2 {}
""",
      "com/example1/Unique.scala" -> """package com.example2
class Test1 {}
""",
      "com/example2/Duplicate.scala" -> """package com.example3
class Test1 {}
"""
    ).map { case (subpath, contents) => src / subpath -> contents }

    sources.foreach { case (path, contents) =>
      path.parent.createDirectory()
      path.toFile.writeAll(contents)
    }
    val paths = (sources.map { case (path, _) => path.toString }).toList

    val settings = new Settings
    settings.outputDirs.setSingleOutput(target.toString)
    val reporter = new ConsoleReporter(settings)
    settings.embeddedDefaults[ClassFileIndexSpec]
    val g = new Global(settings, reporter)
    val run = new g.Run
    run.compile(paths)

    Jar.create(jars / File("sources.jar"), src, null)
    Jar.create(jars / File("lib.jar"), target, null)

    tmpdir
  }

  val tmpdir = setup_sources()

  def get_index(sources: Path, classes: Path, reference_sources: Path = "/foo/bar") = {
    val conf = config("""(
        :root-dir """" + tmpdir + """"
        :source-roots ("""" + sources + """")
        :reference-source-roots ("""" + reference_sources + """")
)""")
    val index = new ClassFileIndex(conf)
    index.indexFiles(List(classes.jfile))
    index
  }

  describe("ClassFileIndex") {

    it("should find multiple candidates") {
      val index = get_index(tmpdir / "src", tmpdir / "target")
      val cand = index.sourceFileCandidates("com.example1", "Test1")
      val expected = Set(
        new PlainFile(tmpdir / "src/com/example1/Duplicate.scala"),
        new PlainFile(tmpdir / "src/com/example2/Duplicate.scala")
      )
      assert(cand == expected, "assert " + cand + " != " + expected)
    }

    it("should find unique candidate") {
      val index = get_index(tmpdir / "src", tmpdir / "target")
      val cand = index.sourceFileCandidates("com.example2", "Test1")
      val expected = Set(
        new PlainFile(tmpdir / "src/com/example1/Unique.scala")
      )
      assert(cand == expected, "assert " + cand + " != " + expected)
    }

    it("should find candidates for jar") {
      val index = get_index(tmpdir / "src", tmpdir / "jars/lib.jar")
      val cand = index.sourceFileCandidates("com.example2", "Test1")
      val expected = Set(
        new PlainFile(tmpdir / "src/com/example1/Unique.scala")
      )
      assert(cand == expected, "assert " + cand + " != " + expected)
    }

    it("should find candidates in source jar") {
      val index = get_index("/FOO", tmpdir / "target", tmpdir / "jars")
      val cand = index.sourceFileCandidates("com.example2", "Test1").map(_.toString)
      val expected = Set(
        tmpdir/Directory("jars/sources.jar") + "(com/example1/Unique.scala)"
      )
      assert(cand == expected, "assert " + cand + " != " + expected)
    }

    it("should allow indexing jar files directly") {
      val index = get_index("/FOO", tmpdir / "target", tmpdir / "jars/sources.jar")
      val cand = index.sourceFileCandidates("com.example2", "Test1").map(_.toString)
      val expected = Set(
        tmpdir/Directory("jars/sources.jar") + "(com/example1/Unique.scala)"
      )
      assert(cand == expected, "assert " + cand + " != " + expected)
    }
  }

}
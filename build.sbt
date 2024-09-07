ThisBuild / organization         := "org.felher"
ThisBuild / organizationName     := "Felix Herrmann"
ThisBuild / version              := "1.0.0-SNAPSHOT"
ThisBuild / organizationHomepage := Some(url("https://felher.org"))
ThisBuild / scalaVersion         := "3.3.3"

ThisBuild / scmInfo := Some(
  ScmInfo(
    url("https://github.com/felher/laminiroute/"),
    "scm:git@github.com:felher/laminiroute.git"
  )
)

ThisBuild / developers := List(
  Developer(
    id = "felher",
    name = "Felix Herrmann",
    email = "felix@herrmann-koenigsberg.de",
    url = url("https://felher.org")
  )
)

ThisBuild / description       := "An ergonomic but minimalistic-to-a-fault router for Laminar"
ThisBuild / licenses          := List(sbt.librarymanagement.License.MIT)
ThisBuild / homepage          := Some(url("https://github.com/felher/laminiroute/"))
ThisBuild / publishTo         := sonatypePublishToBundle.value
ThisBuild / publishMavenStyle := true

lazy val root = project
  .in(file("."))
  .enablePlugins(ScalaJSPlugin)
  .settings(
    name         := "laminiroute",
    usePgpKeyHex("DE132E3B66E5239F490F52AB3DA07E9E7CFDB415"),
    Test / publishArtifact := true,
    mimaPreviousArtifacts  := Set("org.felher" %%% "laminiroute" % "1.0.0"),
    scalacOptions ++= Seq(
      "-language:strictEquality",
      "-feature",
      "-deprecation",
      "-Ykind-projector:underscores",
      "-Ysafe-init",
      "-Xmax-inlines:256",
      "-Wunused:all",
      "-Wvalue-discard",
      "-Wconf:any:verbose"
    ),
    libraryDependencies ++= Seq(
      "org.scala-js" %%% "scalajs-dom" % "2.8.0",
      "com.raquo"    %%% "laminar"     % "17.1.0" % Provided,
      "com.lihaoyi"  %%% "utest"       % "0.8.4"  % Test
    ),
    jsEnv                  := new org.scalajs.jsenv.jsdomnodejs.JSDOMNodeJSEnv(),
    testFrameworks += new TestFramework("utest.runner.Framework")
  )

lazy val testMatrix = project
  .in(file("test-matrix"))
  .enablePlugins(ScalaJSPlugin)
  .settings(
    libraryDependencies ++= Seq(
      "com.raquo"   %%% "laminar" % "17.1.0",
      "org.felher"  %%% "laminiroute" % "1.0.0",
      "org.felher"  %%% "laminiroute" % "1.0.0" % Test classifier "tests",
      "com.lihaoyi" %%% "utest"   % "0.8.4" % Test
    ),
    jsEnv := new org.scalajs.jsenv.jsdomnodejs.JSDOMNodeJSEnv(),
    testFrameworks += new TestFramework("utest.runner.Framework"),
    commands += Command.command("testLaminarCompatibility") { state =>
      final case class CompatTestKey(
          scalaVersion: String,
          laminarVersion: String,
          laminirouteVersion: String
      )

      val laminarVersions = List("0.14.5", "15.0.1", "16.0.0", "17.0.0", "17.1.0")
      val laminirouteVersions = List("0.16.0", "1.0.0")
      val scalaVersions   = List("3.3.3")
      val allTestKeys     = for {
        scalaVersion   <- scalaVersions
        laminarVersion <- laminarVersions
        laminirouteVersion <- laminirouteVersions
      } yield CompatTestKey(scalaVersion, laminarVersion, laminirouteVersion)

      def setVersions(depList: Seq[ModuleID], laminarVersion: String, laminirouteVersion: String): Seq[ModuleID] =
        depList.map(dep => {
          if (dep.organization == "com.raquo" && dep.name == "laminar") {
            dep.withRevision(laminarVersion)
          } else if (dep.organization == "org.felher" && dep.name == "laminiroute") {
            dep.withRevision(laminirouteVersion)
          } else {
            dep
          }
        })

      val results = allTestKeys.foldLeft(Map.empty[CompatTestKey, Boolean])((results, testKey) => {
        // this code is tricky. The scalajs plugin changes the dependency list depending on the scala version.
        // So just adding `scalaVersion := "3.3.3"` to the `newSettings`
        // list doesn't work, because the old dependency list is out of date.
        //
        // Instead of adding a setting, we set the new scala using the `set` command, which seems
        // to trigger scalajs updating the dependency list.
        val withNewScala = Command.process("set scalaVersion := \"" + testKey.scalaVersion + "\"", state)
        val oldDepList   = Project.extract(withNewScala).get(libraryDependencies)
        val newDepList   = setVersions(oldDepList, testKey.laminarVersion, testKey.laminirouteVersion)
        val newSettings  = Seq(libraryDependencies := newDepList)
        val testState    = Project.extract(withNewScala).appendWithSession(newSettings, withNewScala)
        val testsOk      = Project.runTask(Test / test, testState).fold(false)(_._2.toEither.isRight)
        results + (testKey -> testsOk)
      })

      val header  = "||" + laminirouteVersions.map("Beminar " + _).mkString("|") + "|"
      val sepator = "|" + List.fill(laminirouteVersions.size + 1)("-").mkString("|") + "|"

      def makeCell(laminarVersion: String, laminirouteVersion: String): String = {
        val passed = results.filter(entry =>
          entry._1.laminarVersion == laminarVersion && entry._1.laminirouteVersion == laminirouteVersion && entry._2
        )
        if (passed.isEmpty) {
          "❌"
        } else {
          "scala " + passed.keys.map(_.scalaVersion).toList.sorted.map(_.replaceAll("\\.\\d+$", "")).mkString(", ")
        }
      }
      def makeRow(laminarVersion: String): String                          = {
        val cells = laminirouteVersions.map(makeCell(laminarVersion, _))
        s"| Laminar $laminarVersion | ${cells.mkString("|")} |"
      }

      println(header)
      println(sepator)
      laminarVersions.foreach(laminarVersion => println(makeRow(laminarVersion)))

      state
    }
  )

val scala213 = "2.13.16"
val scala3 = "3.3.6"
val supportedScalaVersions = List(scala213, scala3)

ThisBuild / scalaVersion := scala213
ThisBuild / crossScalaVersions := supportedScalaVersions
crossScalaVersions := supportedScalaVersions
publishArtifact := false
publish := {}
publishLocal := {}

lazy val commonSettings = Seq(
  name := "play-json-mapping",
  organization := "null-vector",
  version := "1.1.2",
  scalaVersion := scala213,
  crossScalaVersions := supportedScalaVersions,
  scalacOptions ++= Seq(
    "-encoding", "UTF-8",
    "-release:17",
    "-deprecation",
    "-feature",
    "-unchecked",
  ) ++ (CrossVersion.partialVersion(scalaVersion.value) match {
    case Some((2, _)) => Seq(
      "-language:experimental.macros",
      "-language:implicitConversions",
      "-language:postfixOps",
    )
    case _ => Seq(
      "-language:implicitConversions",
    )
  }),
  libraryDependencies ++= Seq(
    "org.playframework" %% "play-json" % "3.0.6",
    "ch.qos.logback" % "logback-classic" % "1.2.3",
    "org.scalatest" %% "scalatest" % "3.2.19" % Test,
    "joda-time" % "joda-time" % "2.12.7" % Test,
  ),
  libraryDependencies ++= {
    CrossVersion.partialVersion(scalaVersion.value) match {
      case Some((2, _)) => Seq("org.scala-lang" % "scala-reflect" % scalaVersion.value)
      case _ => Nil
    }
  },
  licenses += ("MIT", url("https://opensource.org/licenses/MIT")),
  coverageExcludedPackages := "<empty>",

  Test / fork := true,
  Test / javaOptions += "-Xmx1G",
)

lazy val core = (project in file("core"))
  .dependsOn(
    macros % "compile-internal, test-internal",
    api)
  .settings(
    commonSettings,
    publishMavenStyle := true,
    publishTo := Some("GitHub Package Registry" at "https://maven.pkg.github.com/null-vector/play-json-mapping"),
    credentials ++= {
      val token = sys.env.get("GITHUB_TOKEN")
      token.toSeq.map { t =>
        Credentials("GitHub Package Registry", "maven.pkg.github.com", "_", t)
      }
    },
    Compile / packageDoc / publishArtifact := false,
    Compile / packageBin / mappings ++= (macros / Compile / packageBin / mappings).value,
    Compile / packageSrc / mappings ++= (macros / Compile / packageSrc / mappings).value,
    Compile / packageBin / mappings ++= (api / Compile / packageBin / mappings).value,
    Compile / packageSrc / mappings ++= (api / Compile / packageSrc / mappings).value,
  )

lazy val macros = (project in file("macros"))
  .dependsOn(api)
  .settings(
    commonSettings,
    publishArtifact := false,
    publish := {},
    publishLocal := {}
  )

lazy val api = (project in file("api"))
  .settings(
    commonSettings,
    publishArtifact := false,
    publish := {},
    publishLocal := {}
  )

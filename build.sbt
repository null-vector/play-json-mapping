lazy val scala212 = "2.12.9"
lazy val scala213 = "2.13.2"
lazy val supportedScalaVersions = List(scala212, scala213)

lazy val commonSettings = Seq(
  name := "play-json-mapping",
  organization := "null-vector",
  version := "1.1.0",
  scalaVersion := scala213,
  crossScalaVersions := supportedScalaVersions,
  scalacOptions := Seq(
    "-encoding", "UTF-8", "-target:jvm-1.8", "-deprecation",
    "-language:experimental.macros",
//    "-Ymacro-annotations",
    "-feature",
    "-unchecked",
    "-language:implicitConversions",
    "-language:postfixOps"
  ),
  libraryDependencies += "com.typesafe.play" %% "play-json" % "2.8.1",
  libraryDependencies += "ch.qos.logback" % "logback-classic" % "1.2.3",
  libraryDependencies += "org.scala-lang" % "scala-reflect" % scalaVersion.value,
  libraryDependencies += "org.scalatest" %% "scalatest" % "3.1.1" % Test,
  licenses += ("MIT", url("https://opensource.org/licenses/MIT")),
  coverageExcludedPackages := "<empty>",

  Test / fork := true,
  Test / javaOptions += "-Xmx4G",
  Test / javaOptions += "-XX:+CMSClassUnloadingEnabled",
  Test / javaOptions += "-XX:+UseConcMarkSweepGC",
  Test / javaOptions += "-Dfile.encoding=UTF-8",
)

lazy val core = (project in file("core"))
  .dependsOn(
    macros % "compile-internal, test-internal",
    api)
  .settings(
    commonSettings,
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
    publish := {},
    publishLocal := {}
  )

lazy val api = (project in file("api"))
  .settings(
    commonSettings,
    publish := {},
    publishLocal := {}
  )
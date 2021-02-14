val scala212 = "2.12.13"
val scala213 = "2.13.4"
val supportedScalaVersions = List(scala212, scala213)
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
    publishTo := Some("nullvector" at "https://nullvector.jfrog.io/artifactory/releases"),
    credentials += Credentials(Path.userHome / ".jfrog" / "credentials"),
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
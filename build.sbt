import Dependencies._
import sbtrelease.ReleasePlugin.autoImport.ReleaseTransformations._
import scalariform.formatter.preferences._
import com.typesafe.sbt.SbtScalariform.ScalariformKeys

lazy val commonSettings = Seq(
  organization := "de.envisia",
  scalaVersion := "2.11.8",
  crossScalaVersions in ThisBuild := Seq(scalaVersion.value, "2.12.1"),
  scalacOptions in(Compile, doc) ++= Seq(
    "-target:jvm-1.8",
    "-deprecation",
    "-encoding", "UTF-8",
    "-feature",
    "-unchecked",
    "-deprecation"
  ),
  testOptions in Test += Tests.Argument(TestFrameworks.ScalaTest, "-o"),
  publishMavenStyle in ThisBuild := true,
  pomIncludeRepository in ThisBuild := { _ => false },
  publishTo in ThisBuild := Some("Artifactory Realm" at "https://maven.envisia.de/open")
)

val formattingSettings = Seq(
  ScalariformKeys.preferences := ScalariformKeys.preferences.value
      .setPreference(SpacesAroundMultiImports, true)
      .setPreference(SpaceInsideParentheses, false)
      .setPreference(DanglingCloseParenthesis, Preserve)
      .setPreference(PreserveSpaceBeforeArguments, true)
      .setPreference(DoubleIndentClassDeclaration, true)
)

lazy val `akka-lpd` = (project in file("."))
    .settings(commonSettings)
    .settings(formattingSettings)
    .settings(
      libraryDependencies ++= Seq(
        akka,
        scalaTest % Test
      )
    )

// To sync with Maven central, you need to supply the following information:
pomExtra in Global := {
  <url>https://github.com/sto/akka-lpr</url>
      <licenses>
        <license>
          <name>Envisia License</name>
          <url>http://git.envisia.de/envisia</url>
        </license>
      </licenses>
      <scm>
        <connection>scm:git@git.envisia.de:sto/akka-lpr.git</connection>
        <developerConnection>scm:git:git@git.envisia.de:sto/akka-lpr.git</developerConnection>
        <url>git.envisia.de/sto/akka-lpr</url>
      </scm>
      <developers>
        <developer>
          <id>schmitch</id>
          <name>Christian Schmitt</name>
          <url>https://git.envisia.de/schmitch</url>
        </developer>
        <developer>
          <id>envisia</id>
          <name>envisia GmbH</name>
          <url>http://git.envisia.de/envisia</url>
        </developer>
      </developers>
}

releaseCrossBuild := true

releaseProcess := Seq[ReleaseStep](
  checkSnapshotDependencies,
  inquireVersions,
  runClean,
  runTest,
  setReleaseVersion,
  commitReleaseVersion,
  tagRelease,
  ReleaseStep(action = Command.process("publishSigned", _), enableCrossBuild = true),
  setNextVersion,
  commitNextVersion,
  pushChanges
)
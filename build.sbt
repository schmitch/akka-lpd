import Dependencies._

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
  publishTo in ThisBuild := Some("Artifactory Realm" at "https://maven.envisia.de/internal")
)


lazy val `akka-lpd` = (project in file("."))
    .settings(commonSettings)
    .settings(
      libraryDependencies ++= Seq(
        akka,
        scalaTest % Test
      )
    )



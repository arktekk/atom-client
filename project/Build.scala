import sbt._
import sbt.Keys._
import sbtrelease.Release._
import sbtrelease.ReleasePart
import sbtrelease.ReleaseKeys._

object AtomClient extends Build {

  val logbackVersion = "0.9.18"
  val abderaVersion = "1.1.2"
  val liftVersion = "2.4-M4"

  lazy val buildSettings = Defaults.defaultSettings ++ Seq(
    organization := "no.arktekk.atom-client",
    scalaVersion := "2.9.1",
    crossScalaVersions := Seq("2.9.1") // Seq("2.9.0", "2.9.1"),
  )

  lazy val root = Project(
    id = "root",
    base = file("."),
    settings = buildSettings ++ releaseSettings ++ Seq(
      description := "Atom Client",
      releaseProcess <<= thisProjectRef apply { ref =>
        import sbtrelease.ReleaseStateTransformations._
        Seq[ReleasePart](
          initialGitChecks,
          checkSnapshotDependencies,
          inquireVersions,
          runTest,
          setReleaseVersion,
          commitReleaseVersion,
          tagRelease,
        // Enable when we're deploying to Sonatype
  //        releaseTask(publish in Global in ref),
          setNextVersion,
          commitNextVersion
        )
      }
    )
  ).aggregate(core, lift)

  lazy val core = Project(
    id = "atom-client-core",
    base = file("atom-client-core"),
    settings = buildSettings ++ Seq(
      description := "Atom Client, core",
      libraryDependencies := Seq(
        "org.apache.abdera" % "abdera-core" % abderaVersion,
        "org.apache.abdera" % "abdera-client" % abderaVersion,
        "org.apache.abdera" % "abdera-parser" % abderaVersion,
        "org.apache.abdera" % "abdera-i18n" % abderaVersion,
        "org.apache.abdera" % "abdera-extensions-opensearch" % abderaVersion,
        "commons-httpclient" % "commons-httpclient" % "3.1",
        "commons-io" % "commons-io" % "1.4",
        "net.sf.ehcache" % "ehcache-core" % "2.3.0",
        "org.apache.geronimo.specs" % "geronimo-jta_1.1_spec" % "1.1.1",
        "joda-time" % "joda-time" % "1.6",

        "org.mortbay.jetty" % "jetty" % "6.1.22" % "test",
        "junit" % "junit" % "4.5" % "test",
        "org.specs2" %% "specs2" % "1.6.1" % "test",
        "com.h2database" % "h2" % "1.2.138" % "test",
        "org.slf4j" % "slf4j-simple" % "1.6.1" % "test"
      )
    )
  )

  lazy val lift = Project(
    id = "atom-client-lift",
    base = file("atom-client-lift"),
    settings = buildSettings ++ Seq(
      description := "Atom Client, Lift support",
      libraryDependencies := Seq(
        "net.liftweb" %% "lift-webkit" % liftVersion
      )
    )
  ).dependsOn(core)
}

import sbt._
import sbt.Keys._

object AtomClientServletFilter extends Build {

  lazy val buildSettings = Defaults.defaultSettings ++ Seq(
    organization := "no.arktekk.atom-client",
    scalaVersion := "2.9.1",
    crossScalaVersions := Seq("2.9.1"),
    resolvers := Seq(Resolvers.sonatypeNexusSnapshots, Resolvers.sonatypeNexusStaging),
    publishTo := Some(Resolver.file("file", new File(Path.userHome.absolutePath + "/.m2/repository"))))

  lazy val core = Project(
    id = "atom-client-servlet-filter",
    base = file("."),
    settings = buildSettings ++ Seq(
      description := "Atom Client, servlet filter",
      libraryDependencies := Seq(
        "no.arktekk.atom-client" %% "atom-client-core" % "1.1-SNAPSHOT",
        "javax.servlet" % "servlet-api" % "2.5",
        "org.specs2" %% "specs2" % "1.6.1" % "test",
        "org.scalamock" %% "scalamock-specs2-support" % "2.4" % "test",
        "org.mockito" % "mockito-core" % "1.9.0" % "test",
        "org.slf4j" % "slf4j-simple" % "1.5.11" % "test"
      )
    ))

}

object Resolvers {
  val sonatypeNexusSnapshots = "Sonatype Nexus Snapshots" at "https://oss.sonatype.org/content/repositories/snapshots"
  val sonatypeNexusStaging = "Sonatype Nexus Staging" at "https://oss.sonatype.org/service/local/staging/deploy/maven2"
}
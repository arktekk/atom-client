import sbt._
import Keys._

object AtomClient extends Build {

  val logbackVersion = "0.9.18"
  val abderaVersion = "1.1.2"
  val liftVersion = "2.4-M4"

  lazy val buildSettings = Defaults.defaultSettings ++ Seq(
    organization := "no.arktekk.atom-client",
    version := "1.0-SNAPSHOT",
    scalaVersion := "2.9.1",
    crossScalaVersions := Seq("2.9.1") // Seq("2.9.0", "2.9.1"),
  )

  lazy val root = Project(
    id = "root",
    base = file("."),
    settings = buildSettings ++ Seq(
      description := "Constretto Scala API"
    )
  ).aggregate(core, lift)

  lazy val core = Project(
    id = "atom-client-core",
    base = file("atom-client-core"),
    settings = buildSettings ++ Seq(
      description := "Atom Client, core",
      libraryDependencies := Seq(
        "org.apache.abdera" % "abdera-core" % abderaVersion withSources(),
        "org.apache.abdera" % "abdera-client" % abderaVersion withSources(),
        "org.apache.abdera" % "abdera-parser" % abderaVersion withSources(),
        "org.apache.abdera" % "abdera-i18n" % abderaVersion withSources(),
        "org.apache.abdera" % "abdera-extensions-opensearch" % abderaVersion withSources(),
        "commons-httpclient" % "commons-httpclient" % "3.1" /*withSources()*/,
        "commons-io" % "commons-io" % "1.4" withSources(),
        "net.sf.ehcache" % "ehcache-core" % "2.3.0" withSources(),
        "org.apache.geronimo.specs" % "geronimo-jta_1.1_spec" % "1.1.1" withSources(),
        "joda-time" % "joda-time" % "1.6" withSources(),

        "org.mortbay.jetty" % "jetty" % "6.1.22" % "test->default" withSources(),
        "junit" % "junit" % "4.5" % "test->default",
        "org.specs2" %% "specs2" % "1.6.1" % "test" withSources(),
        "com.h2database" % "h2" % "1.2.138",
        "org.slf4j" % "slf4j-simple" % "1.6.1" % "test" withSources()
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

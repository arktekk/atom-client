import sbt._
import sbt.Keys._
import aether._

object AtomClient extends Build {

  val logbackVersion = "0.9.18"
  val abderaVersion = "1.1.2"
  val liftVersion = "2.4-M4"

  lazy val buildSettings = Defaults.defaultSettings ++ Seq(
    organization := "no.arktekk.atom-client",
    scalaVersion := "2.9.1",
    crossScalaVersions := Seq("2.9.1"),
	publishSetting,
	credentials += Credentials(Path.userHome / ".sbt" / "arktekk-credentials")
  ) ++ mavenCentralFrouFrou

  lazy val root = Project(
    id = "root",
    base = file("."),
    settings = buildSettings ++ Aether.aetherPublishSettings ++ Seq(
      description := "Atom Client"
    )
  ).aggregate(core, servletFilter)

  lazy val publishSetting = publishTo <<= (version) { version: String =>
    if (version.trim.endsWith("SNAPSHOT"))
      Some(Resolvers.sonatypeNexusSnapshots)
    else
      Some(Resolvers.sonatypeNexusStaging)
  }

  lazy val manifestSetting = packageOptions <+= (name, version, organization) map {
    (title, version, vendor) =>
      Package.ManifestAttributes(
        "Created-By" -> "Simple Build Tool",
        "Built-By" -> System.getProperty("user.name"),
        "Build-Jdk" -> System.getProperty("java.version"),
        "Specification-Title" -> title,
        "Specification-Version" -> version,
        "Specification-Vendor" -> vendor,
        "Implementation-Title" -> title,
        "Implementation-Version" -> version,
        "Implementation-Vendor-Id" -> vendor,
        "Implementation-Vendor" -> vendor
      )
  }

  // Things we care about primarily because Maven Central demands them
  lazy val mavenCentralFrouFrou = Seq(
    homepage := Some(new URL("https://github.com/arktekk/atom-client")),
    startYear := Some(2011),
    licenses := Seq(("Apache 2", new URL("http://www.apache.org/licenses/LICENSE-2.0.txt"))),
    pomExtra <<= (pomExtra, name, description) {(pom, name, desc) => pom ++ xml.Group(
      <scm>
        <url>https://github.com/arktekk/atom-client</url>
        <connection>scm:git:git://github.com/arktekk/atom-client.git</connection>
        <developerConnection>scm:git:git@github.com:arktekk/atom-client.git</developerConnection>
      </scm>
      <developers>
      	<developer>
          <id>trygvis</id>
          <name>Trygve Laugstøl</name>
          <url>http://twitter.com/trygvis</url>
        </developer>
        <developer>
          <id>hamnis</id>
          <name>Erlend Hamnaberg</name>
          <url>http://twitter.com/hamnis</url>
        </developer>
      </developers>
    )}
  )

  val testDependencies = Seq(
    "org.mortbay.jetty" % "jetty" % "6.1.22" % "test",
    "org.specs2" %% "specs2" % "1.12" % "test",
    "com.h2database" % "h2" % "1.2.138" % "test",
    "org.slf4j" % "slf4j-simple" % "1.6.1" % "test")

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
        "org.scalaz" %% "scalaz-core" % "6.0.4"
      ) ++ testDependencies
    )
  )

  lazy val servletFilter = Project(
    id = "atom-client-servlet-filter",
    base = file("atom-client-servlet-filter"),
    settings = buildSettings ++ Seq(
      description := "Atom Client, servlet filter",
      libraryDependencies := Seq(
        "javax.servlet" % "servlet-api" % "2.5" % "provided",
        "org.specs2" %% "specs2" % "1.12" % "test",
        "org.scalamock" %% "scalamock-specs2-support" % "2.4" % "test",
        "org.mockito" % "mockito-core" % "1.9.0" % "test",
        "org.slf4j" % "slf4j-simple" % "1.6.1" % "test"
      )
    )
  ).dependsOn(core)
}

object Resolvers {
    val sonatypeNexusSnapshots = "Sonatype Nexus Snapshots" at "https://oss.sonatype.org/content/repositories/snapshots"
    val sonatypeNexusStaging = "Sonatype Nexus Staging" at "https://oss.sonatype.org/service/local/staging/deploy/maven2"
}

import sbt._

class AtomClientProject(info: ProjectInfo) extends ParentProject(info) with IdeaProject
{
  val logbackVersion = "0.9.18"
  val abderaVersion = "1.1"
  val liftVersion = "2.2"

  override def managedStyle = ManagedStyle.Maven

  //val repo = "central" at "http://dev.eventsystems.no/nexus/content/repositories/arktekk-public-snapshot"
  //val publishTo = repo
  Credentials(Path.userHome / ".ivy2" / "eventsystems.properties", log)

  lazy val core = project("atom-client-core", "atom-client-core", new CoreProject(_))
  lazy val lift = project("atom-client-lift", "atom-client-lift", new LiftProject(_), core)

  class CoreProject(info: ProjectInfo) extends DefaultProject(info) with IdeaProject {
    override def libraryDependencies = Set(
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

      "org.scala-tools.testing" %% "specs" % "1.6.7" % "test" withSources(),
      "org.mortbay.jetty" % "jetty" % "6.1.22" % "test->default" withSources(),
      "junit" % "junit" % "4.5" % "test->default",
      "org.scala-tools.testing" %% "specs" % "1.6.7" % "test->default" withSources(),
      "com.h2database" % "h2" % "1.2.138",
      "org.slf4j" % "slf4j-simple" % "1.5.8" % "test" withSources()
    ) ++ super.libraryDependencies
  }

  class LiftProject(info: ProjectInfo) extends DefaultProject(info) with IdeaProject {
    def lift(name: String) = "net.liftweb" %% ("lift-" + name) % liftVersion withSources ()

    override def libraryDependencies = Set(
      lift("webkit")
    ) ++ super.libraryDependencies
  }
}

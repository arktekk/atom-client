package no.arktekk.cms

import java.io.File
import java.net.URL
import no.arktekk.cms.atompub.AtomPubClientConfiguration
import org.apache.commons.io.FileUtils._
import org.mortbay.jetty.handler._
import org.mortbay.jetty.{MimeTypes, Server}
import org.specs._

class CmsClientSpec extends Specification {
  //  LoggingAutoConfigurer()()
  CmsUtil.skipEhcacheUpdateCheck

  val tmpFile = new File(System.getProperty("java.io.tmpdir"), "cms-client")
  if (tmpFile.exists)
    deleteDirectory(tmpFile)

  def hubCallback(x: URL, y: URL) = {}

  val client = CmsClient(AtomPubClientConfiguration(ConsoleLogger, "yo", tmpFile), "http://localhost:8908/service.atomsvc.xml", "javazone11 Workspace", "javazone11 Posts", "javazone11 Pages", hubCallback)

  val server = new Server(8908)
  val contextPath = classOf[CmsClientSpec].getResource("/cms-client").getFile
  val mimeTypes = new MimeTypes {
    addMimeMapping("atom.xml", "application/atom+xml")
    addMimeMapping("atomsvc.xml", "application/atomsvc+xml")
  }
  server.addHandler(new ResourceHandler() {
    setResourceBase(contextPath)
    setMimeTypes(mimeTypes)
  })
  server.start

  doAfterSpec {
    println("Stopping Jetty")
    server.stop
  }

  "cms client" should {
    "getEntries" in {
      val entries = client.getEntries(0, Positive.fromInt(1000))

      entries must haveSize(16)
    }

    //"getChildrenOf" in {
    //  val entries = client.getChildrenOf(AtomId("http://javazone11.wordpress.com/?p=74"))
    //  entries must beSome
    //
    //  entries.get.map(_.title) must containInOrder(List("Backstory", "ClubZone", "Crew", "Entertainment", "Expo", "Oslo Spektrum", "The Organiser", "Whiteboards"))
    //}
    //
    //"getSiblingsOf top page" in {
    //  val siblings = client.getSiblingsOf(CmsSlug("about_javazone"))
    //
    //  siblings must beSome
    //
    //  val (left, item, right) = siblings.get
    //  left.map(_.title) must containInOrder(List("Agenda"))
    //  item.title must_== "About JavaZone"
    //  right.map(_.title) must containInOrder(List("Partners", "Press"))
    //}
    //
    //"getSiblingsOf" in {
    //  val siblings = client.getSiblingsOf(CmsSlug("entertainment"))
    //
    //  siblings must beSome
    //
    //  val (left, item, right) = siblings.get
    //  left.map(_.title) must containInOrder(List("Backstory", "ClubZone", "Crew"))
    //  item.title must_== "Entertainment"
    //  right.map(_.title) must containInOrder(List("Expo", "Oslo Spektrum", "The Organiser", "Whiteboards"))
    //}
  }
}

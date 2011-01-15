package no.arktekk.cms

import java.io.File
import java.net.URL
//import net.liftweb.util.LoggingAutoConfigurer

object CmsClientTester extends Application {
//  LoggingAutoConfigurer()()
  val dir = new File(System.getProperty("user.home"), ".cms")

//  val url = "http://localhost/~trygvis/wordpress/?atompub=service"
  val url = "http://wp.java.no/?atompub=service"
  val client = CmsClient(ConsoleLogger, "cms", dir, url, "javazone11 Workspace", "javazone11 Posts", "javazone11 Pages", (_: URL, _: URL) => {})

  System.setProperty("net.sf.ehcache.skipUpdateCheck", "true")
  println("Getting service document...")

//  val entries = client.getEntries(None, None)
//  println("Entries:")
//  entries.foreach(entry => println(entry.title))
//  println("------------------------------------------")

  val tree = client.getTopPages().map(dumpPageTree(0)).flatten
  println("Tree:")
  tree.foreach(println)
  println("------------------------------------------")

//  val partners = client.getPageBySlug(CmsSlug("partners")).get
//  println("partners=" + partners.id)

//  val children = client.getEntriesByParentId(partners.id).get
//  println("children:")
//  println(children.map(_.title))

//  val (left, entry, right) = client.getSiblingsOf(CmsSlug("backstory")).get
//  println("left")
//  left.foreach(entry => println(entry.title))
//  println("entry: " + entry.title)
//  println("right")
//  right.foreach(entry => println(entry.title))

  def dumpPageTree(indent: Int)(entry: CmsEntry): List[String] = {
    println("entry.id=" + entry.id + ", title=" + entry.title)
    List(" " * indent + entry.title) ++
        client.getChildrenOf(entry.id).get.map(dumpPageTree(indent + 1)).flatten
  }
}

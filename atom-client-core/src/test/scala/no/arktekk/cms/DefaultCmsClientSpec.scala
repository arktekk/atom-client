package no.arktekk.cms

import org.apache.abdera.parser.stax.FOMEntry
import org.apache.abdera.model.{Content, Entry}
import org.specs2.mutable._
import java.net.URL
import no.arktekk.cms.atompub.{AtomPubEntry, AtomPubClient}
import scala.xml.{Text => XmlText}

class DefaultCmsClientSpec extends Specification {

  val entries = {
    def entry(title: String, content: String, categories: List[String]) = {
      val entry = new FOMEntry()
      entry.setTitle(title)
      entry.setContent(content, Content.Type.TEXT)
      println("categories: " + categories)
      categories.foreach(println)
      categories.foreach(entry.addCategory(_))
      entry.addCategory("yo")
      entry
    }

    entry("title 1", "content 1", List("cat 1")) ::
        entry("title 2", "content 2", List("cat 1")) ::
        entry("title 3", "content 3", List("cat 2")) ::
        entry("title 4", "content 4", List("cat 1")) ::
        entry("title 5", "content 5", List("cat 1")) ::
        entry("title 6", "content 6", List("cat 1")) ::
        entry("title 7", "content 7", List("cat 1")) ::
        entry("title 8", "content 8", List("cat 1")) ::
        entry("title 9", "content 9", List("cat 1")) ::
        Nil
  }

  def hubCallback(x: URL, y: URL) {}

  val client = new DefaultCmsClient(ConsoleLogger, new MockClient(entries), new CmsClient.ServiceDocumentConfiguration(new URL("http://serviceUrl"), "workspace", "posts", "pages"), hubCallback)

  private class MockClient(val entries: List[Entry]) extends AtomPubClient {
    def fetchEntries = entries.map(new AtomPubEntry(_))

    def fetchService(serviceUrl: URL) = error("not implemented")

    def fetchFeed(url: URL) = error("not implemented")

    def fetchEntry(url: URL) = error("not implemented")

    def emptyCache() {}

    def close() {}
  }

  def haveTitle(title: String) = (beEqualTo(_: String)) ^^^ ((_: CmsEntry).title)

//  "getEntriesForCategory" should {
//    "return a category filtered list" in {
//      //      entries.foreach(_ must beSome[Node])
//      val list = client.getEntriesForCategory("cat 1", 0, Positive.fromInt(1000)).page
//
//      list.foreach(println)
//
//      list must haveSize(8)
//      list(0) must verify((entry: CmsEntry) => entry.title == "title 1")
//      list(1).title must_== "title 2"
//      list(7) must verify((entry: CmsEntry) => entry.title == "title 9")
//      list.foreach(_ must verify((entry: CmsEntry) => entry.categories.exists("cat 1" ==)))
//    }
//
//    "return a limited list" in {
//      //      entries.foreach(_ must beSome[Node])
//      val list = client.getEntriesForCategory("cat 1", 0, Positive.fromInt(3)).page
//
//      println(list)
//      list must haveSize(3)
//      list(0).title must_== "title 1"
//      list(0).categories must contain("cat 1")
//      list(1).title must_== "title 2"
//      list(1).categories must contain("cat 1")
//      list(2).title must_== "title 4"
//      list(2).categories must contain("cat 1")
//    }
//  }
}

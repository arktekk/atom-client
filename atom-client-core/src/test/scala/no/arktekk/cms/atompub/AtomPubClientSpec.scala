package no.arktekk.cms.atompub

import java.net.URL
import javax.activation.MimeType
import no.arktekk.cms.CmsConstants._
import org.apache.abdera.Abdera
import org.specs.Specification

class AtomPubClientSpec extends Specification {
  import AtomPubClient._

  val abdera = new Abdera
  val href = new URL("http://tjoho")

  def link(rel: String) = AtomPubLink(rel, None, href)

  def link(rel: String, mimeType: MimeType) = AtomPubLink(rel, Some(mimeType), href)

  "AtomPubClient" should {
    "find links" in {
      findLink(List(link("up")), "next", atomMimeType) must beNone

      findLink(List(link("up", serviceMimeType)), "next", atomMimeType) must beNone

      findLink(List(link("next", serviceMimeType)), "next", atomMimeType) must beNone

      findLink(List(link("next")), "next", atomMimeType).map(_.href) must beSome(href)

      findLink(List(link("next", atomMimeType)), "next", atomMimeType).map(_.href) must beSome(href)
    }
  }
}

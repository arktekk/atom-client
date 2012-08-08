package no.javabin.atomclientservletfilter

import org.mockito.ArgumentCaptor
import org.mockito.Matchers
import org.mockito.Mockito._
import org.specs2.mutable._
import javax.servlet.{FilterConfig, FilterChain}
import javax.servlet.http.{HttpServletResponse, HttpServletRequest}
import java.net.URL
import org.apache.abdera.Abdera
import no.arktekk.cms.atompub._


object filter extends AtomClientServletFilter {
  val abdera = new Abdera
  override def createAtomPubClient(atomPubClientConfiguration: AtomPubClientConfiguration) =
    new AtomPubClient {
      def fetchService(serviceUrl: URL): Either[String, AtomPubService] = Left("Not implemented")
      def fetchFeed(url: URL): Either[String, AtomPubFeed] = {
        Right(abdera.getParser.parse(url.openStream()).getRoot).right.map(AtomPubFeed)
      }
      def fetchEntry(url: URL): Either[String, AtomPubEntry] = {
        Right(abdera.getParser.parse(url.openStream()).getRoot).right.map(AtomPubEntry)
      }
      def emptyCache() {}
      def close() {}
    }

  setPagesUrl(getClass.getResource("pages.xml"))
  setPostsUrl(getClass.getResource("posts.xml"))
  init(mock(classOf[FilterConfig]))
}

class AtomClientServletFilterSpec extends Specification {

  val request = mock(classOf[HttpServletRequest])
  when(request.getRequestURI).thenReturn("/about.jspx")

  "pages contain about page" in {
    val requestCaptor = ArgumentCaptor.forClass(classOf[HttpServletRequest])
    filter.doFilter(request, mock(classOf[HttpServletResponse]), mock(classOf[FilterChain]))
    verify(request).setAttribute(Matchers.eq("cms"), requestCaptor.capture())
    requestCaptor.getValue.asInstanceOf[CmsProperties].getPage.getContent must startWith("<div><h2 xmlns=\"h")
  }
}
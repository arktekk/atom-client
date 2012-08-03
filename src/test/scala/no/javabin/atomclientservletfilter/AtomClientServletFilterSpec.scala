package no.javabin.atomclientservletfilter

import org.mockito.ArgumentCaptor
import org.mockito.Matchers
import org.mockito.Mockito._
import org.specs2.mutable._
import javax.servlet.{FilterConfig, FilterChain}
import javax.servlet.http.{HttpServletResponse, HttpServletRequest}


object filter extends AtomClientServletFilter {
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
    requestCaptor.getValue.asInstanceOf[CmsProperties].getPage.getContent must startWith("tull")
  }
}
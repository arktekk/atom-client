package no.javabin.atomclientservletfilter

import javax.servlet.http.HttpServletRequest
import javax.servlet._
import java.net.URL
import org.joda.time.Minutes._
import no.arktekk.cms.{ConsoleLogger, DefaultCmsClient, CmsClient}
import reflect.BeanProperty
import no.arktekk.cms.atompub.{AtomPubClient, CachingAbderaClient, AtomPubClientConfiguration}
import java.io.File

class AtomClientServletFilter extends Filter {
  private var cmsClient: CmsClient = null
  @BeanProperty var pagesUrl: URL = null
  @BeanProperty var postsUrl: URL = null

  override def init(filterConfig: FilterConfig) {
    implicit def toURL(s: String) = new URL(s)
    def filterConfigOverride[T](propertyName: String, defaultValue: T)(implicit cstr: String => T): T =
      Option(filterConfig.getInitParameter(propertyName)).map(s => cstr(s)).getOrElse(defaultValue)
    pagesUrl = filterConfigOverride("pagesUrl", pagesUrl)
    postsUrl = filterConfigOverride("postsUrl", postsUrl)
    val logger = ConsoleLogger
    val atomPubClientConfiguration = new AtomPubClientConfiguration(
      logger, "CMS", createTempDirectory(), None, Some(minutes(10)),
      Some(CachingAbderaClient.confluenceFriendlyRequestOptions))
    val configuration = new CmsClient.ExplicitConfiguration(postsUrl, pagesUrl)
    cmsClient = new DefaultCmsClient(logger, AtomPubClient(atomPubClientConfiguration), configuration, (_, _) => ())
  }

  private def createTempDirectory(): File = {
    var cmsCacheDir: File = null
    cmsCacheDir = File.createTempFile("cms_cache", null)
    assert(cmsCacheDir.delete)
    assert(cmsCacheDir.mkdir)
    cmsCacheDir
  }

  def doFilter(request: ServletRequest, response: ServletResponse, chain: FilterChain) {
    var pathInfo: String = (request.asInstanceOf[HttpServletRequest]).getRequestURI
    pathInfo = pathInfo.replaceAll("^.*\\/", "").replaceAll("\\.jspx", "").toLowerCase
    request.setAttribute("cms", new CmsProperties(cmsClient, pathInfo))
    chain.doFilter(request, response)
  }

  override def destroy() {
  }

}
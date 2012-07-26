package no.javabin.atomclientservletfilter

import no.arktekk.cms.{CmsEntry, CmsSlug, CmsClient}
import scalaz.NonEmptyList

class CmsProperties(cmsClient: CmsClient, pathInfo: String) {
  def getPage: CmsEntryProperties = {
    var cmsEntryOption = cmsClient.fetchPageBySlug(NonEmptyList(CmsSlug.fromString(pathInfo)))
    cmsEntryOption.map(cmsEntry => new CmsEntryProperties(cmsEntry)).getOrElse(null)
  }
}
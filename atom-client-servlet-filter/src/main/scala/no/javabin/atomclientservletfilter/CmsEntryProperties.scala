package no.javabin.atomclientservletfilter

import no.arktekk.cms.CmsEntry
import collection.JavaConverters.seqAsJavaListConverter

class CmsEntryProperties(cmsEntry: CmsEntry) {

  def getTitle: String = cmsEntry.title

  def getContent: String = cmsEntry.content.toString()

  def getId: String = cmsEntry.id.toString()

  def getSlug: String = cmsEntry.slug.toString()

  def getLinks: java.util.List[AtomPubLinkProperties] =
    cmsEntry.links.map(atomPubLink => new AtomPubLinkProperties(atomPubLink)).asJava
}
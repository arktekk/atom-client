package no.javabin.atomclientservletfilter

import no.arktekk.cms.atompub.AtomPubLink
import java.net.URL
import javax.activation.MimeType

class AtomPubLinkProperties(atomPubLink: AtomPubLink) {
  def getHref: URL = atomPubLink.href

  def getMimeType: MimeType = atomPubLink.mimeType.getOrElse(null)

  def getRel: String = atomPubLink.rel
}
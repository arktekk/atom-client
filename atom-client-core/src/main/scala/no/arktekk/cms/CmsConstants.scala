package no.arktekk.cms

import org.apache.abdera.util.Constants._
import javax.activation.MimeType

object CmsConstants {
  val atomMimeType: MimeType = new MimeType(ATOM_MEDIA_TYPE)
  val atomEntryMimeType: MimeType = new MimeType(ENTRY_MEDIA_TYPE)
  val serviceMimeType: MimeType = new MimeType(APP_MEDIA_TYPE)
}

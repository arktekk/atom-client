package no.javabin.atomclientservletfilter

import collection.JavaConverters.seqAsJavaListConverter
import java.lang.{Object => JObject, String => JString, Integer => JInteger}
import java.util.{List => JList, Map => JMap}
import scalaz.NonEmptyList
import no.arktekk.cms.{Positive, CmsEntry, CmsSlug, CmsClient}

class CmsProperties(val cmsClient: CmsClient, pathInfo: String) {
  private def asCmsEntryProperties(cmsEntry: CmsEntry) = new CmsEntryProperties(cmsEntry)
  def getPage: CmsEntryProperties = {
    var cmsEntryOption = cmsClient.fetchPageBySlug(NonEmptyList(CmsSlug.fromString(pathInfo)))
    cmsEntryOption.map(asCmsEntryProperties).getOrElse(null)
  }

  def getPosts: JMap[JString, JMap[JString, JList[CmsEntryProperties]]] =
    new DynamicMap[JString, JMap[JString, JList[CmsEntryProperties]]] {
      override def get(pageLength: JObject): JMap[JString, JList[CmsEntryProperties]] =
        new DynamicMap[JString, JList[CmsEntryProperties]] {
          override def get(pageNumber: JObject): JList[CmsEntryProperties] = {
            def toInt(o: Object): Int = JInteger.valueOf(o.asInstanceOf[JString])
            val pnr = toInt(pageNumber)
            val plength = toInt(pageLength)
            cmsClient.fetchEntries(pnr * plength, new Positive(plength)).map(asCmsEntryProperties).asJava
          }
        }
    }
}
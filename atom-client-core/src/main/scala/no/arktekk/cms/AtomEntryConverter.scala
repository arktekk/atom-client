package no.arktekk.cms

import java.net.URL
import no.arktekk.cms.atompub._
import no.arktekk.cms.CmsUtil._
import org.apache.abdera.model.{Link => AtomLink, Category => AtomCategory, Content => AtomContent, DateTime => AtomDateTime, Element => AtomElement, Div => AtomDiv, Text => AtomText}
import org.joda.time.DateTime
import org.apache.abdera.i18n.iri.IRI
import scala.io.Source
import scala.xml._
import scala.collection.immutable.WrappedString
import scala.xml.parsing.XhtmlParser

object AtomEntryConverter {
  val atomElementClass: Class[AtomElement] = classOf[AtomElement]
  val atomDivClass: Class[AtomDiv] = classOf[AtomDiv]

  def atomEntryToCmsEntry(entry: AtomPubEntry): Either[String, CmsEntry] = for {
    title <- entry.titleElement.toRight("Missing 'title' element").
        right.flatMap(atomTextToString).
        left.map("Unable to map entry '" + entry.id + "': " + _).
        right
    content <- entry.contentElement.toRight("Missing 'content' element").
        right.flatMap(atomContentToNode).
        left.map("Unable to map entry '" + entry.id + "': " + _).
        right
    slug <- CmsSlug.fromAtomPub(entry).toRight("Missing 'title' element").
        right
  } yield {
    val updatedOrPublished = entry.updatedElement.
        orElse(entry.publishedElement).
        map(atomDateTimeToDateTime)
    val categories = entry.categories.map(atomCategoryToString)
    CmsEntry(entry.id, updatedOrPublished, title, slug, content, categories)
  }

  def atomDateTimeToDateTime(dateTime: AtomDateTime) =
    new DateTime(dateTime.getTime)

  def atomTextToString(text: AtomText): Either[String, String] = text.getTextType match {
    case AtomText.Type.HTML =>
      val source: Source = Source.fromString("<span>" + text.getText + "</span>")
      for {
        document <- Option(XhtmlParser(source)).
            toRight("Unable to parse content as xhtml.").right
      } yield document.headOption.map(_.text).getOrElse("") // This effectively strips all markup from the title
    case AtomText.Type.XHTML =>
      Right(text.getValue)
    case AtomText.Type.TEXT =>
      Right(text.getText)
  }

  def filterWordpressJunk(xml: Node): NodeSeq = xml match {
    case Elem(_, "img", metadata, _, x@_*) if metadata.asAttrMap.get("height").exists("1" ==) => NodeSeq.Empty
    case Elem(_, "a", metadata, _, x@_*) if metadata.asAttrMap.get("rel").exists("nofollow" ==) => NodeSeq.Empty
    case Elem(prefix, label, attributes, scope, children@_*) => Elem(prefix, label, attributes, scope, children.flatMap(filterWordpressJunk): _*)
    case x => x
  }

  def atomCategoryToString(category: AtomCategory) = category.getTerm

  def atomContentToNode(element: AtomContent): Either[String, NodeSeq] = element.getContentType match {
    case AtomContent.Type.XHTML =>
      Right(scala.xml.XML.loadString("<div>" + element.getValue + "</div>"))
    case AtomContent.Type.HTML =>
      val string = "<span>" + element.getValue + "</span>"

      parseXmlString(string).right.
          // Remote the <span>
          map(_.head).right.
          map(filterWordpressJunk).right.
          flatMap(_.headOption.toRight("internal error")).right.
          map(_.child)

    case AtomContent.Type.TEXT =>
      val body = new WrappedString(element.getValue).lines.map(_.stripLineEnd).filter(_.length > 0).foldLeft(NodeSeq.Empty)({(a,b) => a ++ <p>{b}</p>})
      Right(<div>{body}</div>)
    case _ => Left("Unknown content type: " + element.getContentType)
  }

  def linkToUrl(link: AtomLink) = fromNull(link.getResolvedHref).flatMap(iriToUrl)

  def atomLinkToLink(link: AtomLink): Option[AtomPubLink] = for {
    rel <- fromNull(link.getRel)
    url <- fromNull(link.getHref).flatMap(iriToUrl)
    val mimeType = fromNull(link.getMimeType)
  } yield AtomPubLink(rel, mimeType, url)

  def iriToUrl(iri: IRI): Option[URL] =
    try {
      Some(iri.toURL)
    } catch {
      case _ => None
    }

  private def parseXmlString(s: String): Either[String, NodeSeq] =
    try {
      Option(XhtmlParser(Source.fromString(s))).
          toRight("Unable to parse content.")
    } catch {
      case ex: RuntimeException =>
        Left("Unable to parse content.")
    }
}

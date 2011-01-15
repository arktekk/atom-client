package no.arktekk.cms

import org.joda.time.format._

/*
private object CurrentEntry extends DynamicVariable[Box[CmsEntry]](Empty)

class CmsSnippet(val cmsClient: CmsClient, val fallbackLimit: Option[Int]) extends DispatchSnippet with Logger {

  val dispatch: DispatchIt = {
    case "category" => category _
    case "page" => page _
    case "children" => children _
    case "entry" => entry _
  }

  /**
   * TODO: Ideally this should return an error message if the offset or count attributes
   * are invalid Ints
   */
  def category(body: NodeSeq): NodeSeq = {
    val categoryOption: Option[String] = S.attr.~("category").map(_.text)
    val offsetParameterName: String = S.attr("offsetParameter").toOption.getOrElse("offset")
    val limitAttributeOption: Option[Int] = S.attr("limit").toOption.flatMap(parseInt)
    val limitParameterOption: Option[String] = S.attr.~("limitParameter").map(_.text)

    if (limitAttributeOption.isDefined && limitParameterOption.isDefined) {
      return <span color="red">Only "limit" or "limitParameter" can be specified.</span>
    }

    val limitOption: Option[Int] = limitAttributeOption.
        orElse(limitParameterOption.flatMap(S.param(_).toOption).flatMap(parseInt)).
        orElse(fallbackLimit)

    val offsetOption: Option[Int] = S.param(offsetParameterName).toOption.flatMap(parseInt)

    info("category=" + categoryOption + ", offset=" + offsetOption + ", limit=" + limitOption)

    if (categoryOption.isEmpty) {
      return redSpan("Missing required attribute: category")
    }

    (for {
      category <- categoryOption
      session <- S.session.toOption
    } yield {
      val entries = cmsClient.getEntriesForCategory(category, offsetOption, limitOption) // zip
      val entriesAsXml = entries.map((entry: CmsEntry) => CurrentEntry.withValue(Full(entry)) {
        session.processSurroundAndInclude(PageName.get, body)
      })
      entriesAsXml.foldLeft(NodeSeq.Empty)(_ ++ _)
    }).getOrElse(NodeSeq.Empty)
  }

  def page(body: NodeSeq): NodeSeq = {
    val either: Either[NodeSeq, NodeSeq] = for {
      entry <- cmsPageById(S.attrsFlattenToMap).right
      session <- S.session.toOption.toRight(redSpan("No session")).right
    } yield {
      CurrentEntry.withValue(Full(entry)) {
        session.processSurroundAndInclude(PageName.get, body)
      }
    }
    either.merge
  }

  def children(body: NodeSeq): NodeSeq = {
    val either = for {
      session <- S.session.toOption.toRight(redSpan("No session")).right
      children <- childrenById(S.attrsFlattenToMap).right
    } yield {
      children.map(entry =>
        CurrentEntry.withValue(Full(entry)) {
          session.processSurroundAndInclude(PageName.get, body)
        }
      ).foldLeft(NodeSeq.Empty)(_ ++ _)
    }
    either.merge
  }

  def entry(body: NodeSeq): NodeSeq = {
    CurrentEntry.value match {
      case Full(entry) =>
        bind("entry", body,
          "title" -> entry.title,
          "updatedOrPublished" -> entry.updatedOrPublished.
              map((x: DateTime) => Text(CmsSnippet.dateTimeFormatter.print(x))).getOrElse(NodeSeq.Empty),
          "content" -> entry.content,
          "link" -> {(body: NodeSeq) => CmsSnippet.cmsEntryToLink(entry)})
      case _ => <span style="color: red">empty</span>
    }
  }

  private def cmsPageById(attrs: Map[String, String]): Either[NodeSeq, CmsEntry] = {
    for {
      id <- attrs.get("id").map(AtomId).toRight(redSpan("Missing or invalid required attribute 'id'.")).right
      entry <- cmsClient.getPageById(id).toRight(Text("Not found")).right
    } yield entry
  }

  private def childrenById(attrs: Map[String, String]): Either[NodeSeq, List[CmsEntry]] = {
    for {
      id <- attrs.get("id").map(AtomId).
          toRight(redSpan("Missing or invalid required attribute 'id'.")).right
      list <- cmsClient.getChildrenOf(id).
          toRight(redSpan("Could not find parent: " + id)).right
    } yield list
  }
}
*/

object CmsSnippet {
  def cmsEntryToLink(entry: CmsEntry) = <a href={"/about/" + entry.slug}>{entry.title}</a>

  val dateTimeFormatter: DateTimeFormatter = new DateTimeFormatterBuilder().
      appendDayOfMonth(2).
      appendLiteral(' ').
      appendMonthOfYearText().
      appendLiteral(' ').
      appendYear(4, 4).
      toFormatter()
}

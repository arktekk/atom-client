package no.arktekk.cms

import java.io._
import java.util.Properties
import java.net.{URI, URL}
import no.arktekk.cms.atompub._
import no.arktekk.cms.CmsUtil._
import org.apache.abdera.model.{Collection => AtomCollection}
import org.apache.commons.io.IOUtils._
import scala.xml._
import org.joda.time.{Minutes, DateTime}

case class CmsSlug(private val value: String) {
  override def toString = value

  lazy val atomEntryFilter: (AtomPubEntry) => Boolean = entry => entry.title.exists(CmsSlug.fromTitle(_).value.equals(value))

  lazy val cmsEntryFilter: (CmsEntry) => Boolean = entry => this.value.equals(entry.slug.value)
}

object CmsSlug {
  def fromAtomPub(entry: AtomPubEntry) = entry.title.map(fromTitle)

  private def fromTitle(title: String) = CmsSlug(title.
      replace(" ", "_").
      replace("/", "_").
      toLowerCase)

  def fromString(s: String) = new CmsSlug(s)
}

case class CmsEntry(
  id: AtomId,
  updatedOrPublished: Option[DateTime],
  title: String,
  slug: CmsSlug,
  categories: List[String],
  content: NodeSeq)

/**
 * See http://www.opensearch.org/Specifications/OpenSearch/1.1#OpenSearch_response_elements
 *
 * Note that startIndex is 1-based.
 */
case class OpenSearchResponse(page: List[CmsEntry], totalResults: Int, index: Int, itemsPerPage: Int) {
  lazy val pageSize = page.size
  /**
   * Page indexes start at 1
   */
  lazy val pageIndex = (index / itemsPerPage) + 1
  lazy val pageCount = totalResults / itemsPerPage.toInt
  lazy val prevPageIndex = Some(pageIndex - 1).filter(_ > 0)
  lazy val nextPageIndex = Some(pageIndex + 1).filter(_ <= pageCount)

  lazy val prevStart = prevPageIndex.map(i => (i - 1) * itemsPerPage)
  lazy val nextStart = nextPageIndex.map(i => (i - 1) * itemsPerPage)
}

trait CmsClient extends Closeable {

  def getEntries(offset: Int, limit: Positive): List[CmsEntry]

  def getEntriesForCategory(category: String, offset: Int, limit: Positive): OpenSearchResponse

  def getPageById(id: AtomId): Option[CmsEntry]

  def getChildrenOf(parent: AtomId): Option[List[CmsEntry]]

  def getChildrenOf(parent: CmsSlug): Option[List[CmsEntry]]

  def getPageBySlug(slug: CmsSlug): Option[CmsEntry]

  def getSiblingsOf(slug: CmsSlug): Option[(List[CmsEntry], CmsEntry, List[CmsEntry])]

  def getTopPages(): List[CmsEntry]

  def getPostBySlug(slug: CmsSlug): Option[CmsEntry]

  def getParentOfPageBySlug(slug: CmsSlug): Option[CmsEntry]
}

object CmsClient {
  type HubCallback = (URL, URL) => Unit

  private val EOL = System.getProperty("line.separator")

  def apply(logger: Logger, name: String, dir: File, hubCallback: HubCallback): CmsClient = {
    logger.info("Creating new CmsClient from " + dir + " called " + name)

    if (!dir.isDirectory) {
      throw new IOException("Not a directory: " + dir);
    }

    val file = new File(dir, "config.properties")

    if (!file.canRead) {
      throw new IOException("Can't read file " + file);
    }

    val properties = new Properties;
    var inputStream: FileInputStream = null;
    try
    {
      inputStream = new FileInputStream(file);
      properties.load(inputStream);
    } finally {
      closeQuietly(inputStream);
    }

    val serviceUrl = properties.getProperty("serviceUrl");
    val workspaceName = properties.getProperty("workspace");
    val postsCollection = Option(properties.getProperty("postsCollection")).getOrElse("")
    val pagesCollection = Option(properties.getProperty("pagesCollection")).getOrElse("")

    val proxyConfiguration: Option[ProxyConfiguration] = for {
      proxyHost <- Option(properties.getProperty("proxyHost"))
      proxyPort <- Option(properties.getProperty("proxyPort")).map(Integer.parseInt(_))
    } yield {
      ProxyConfiguration(proxyHost, proxyPort)
    }

    val ttl = Option(properties.getProperty("ttl")).map(Minutes.parseMinutes(_));

    val clientConfiguration = AtomPubClientConfiguration(logger, name, new File(dir, "cache"), proxyConfiguration, ttl)
    apply(clientConfiguration, serviceUrl, workspaceName, postsCollection, pagesCollection, hubCallback)
  }

  def apply(clientConfiguration: AtomPubClientConfiguration, serviceUrl: String, workspaceName: String, postsCollection: String, pagesCollection: String, hubCallback: HubCallback): CmsClient = {
    val atomPubClient = AtomPubClient(clientConfiguration)
    new DefaultCmsClient(clientConfiguration.logger, atomPubClient, URI.create(serviceUrl).toURL, workspaceName, postsCollection, pagesCollection, hubCallback)
  }
}

class DefaultCmsClient(val logger: Logger, val atomPubClient: AtomPubClient, serviceUrl: URL, workspaceName: String, postsCollection: String, pagesCollection: String, hubCallback: CmsClient.HubCallback) extends CmsClient {
  import CmsConstants._
  import AtomEntryConverter._

  def close = {
    logger.info("Closing cms client...")
    atomPubClient.close
  }

  def getEntries(offset: Int, limit: Positive) = {
//    logger.info("getEntries: offset=" + offset + ", limit=" + limit);
    getAllAtomEntriesIn(postsCollection).
        flatMap(atomEntryToCmsEntry).
        drop(offset).
        take(limit.toInt)
  }

  def getEntriesForCategory(category: String, offset: Int, limit: Positive) = {
//    logger.info("getEntriesForCategory: category=" + category + ", offset=" + offset + ", limit=" + limit);
    var list = getAllAtomEntriesIn(postsCollection).
        flatMap(atomEntryToCmsEntry)

    val totalResults = list.size

    list = list.filter(_.categories.exists(category ==)).
        drop(offset).
        take(limit.toInt)

    OpenSearchResponse(list, totalResults, offset, limit.toInt)
  }

  def getPageById(id: AtomId) = {
//    logger.info("getPageById: id=" + id)
    getAllAtomEntriesIn(pagesCollection).
        find(id.atomEntryFilter).
        flatMap(atomEntryToCmsEntry)
  }

  def getChildrenOf(parent: AtomId): Option[List[CmsEntry]] = {
//    logger.info("getChildrenOf: parent=" + parent);
    for {
      parent <- getAllAtomEntriesIn(pagesCollection).find(parent.atomEntryFilter)
      collection <- fromNull(parent.entry.getExtension(classOf[AtomCollection]))
      url <- fromNull(collection.getResolvedHref).flatMap(iriToUrl)
      list <- dumpLeftGetRight(logger)(downloadAllEntries(url, "next"))
    } yield {
      val entries = list.flatMap(atomEntryToCmsEntry)
//      logger.info("getChildrenOf: entries=" + entries.map(_.title));
      entries
    }
  }

  def getChildrenOf(parent: CmsSlug): Option[List[CmsEntry]] = {
//    logger.info("getChildrenOf: parent=" + parent);
    for {
      parent <- getAllAtomEntriesIn(pagesCollection).find(parent.atomEntryFilter)
      collection <- fromNull(parent.entry.getExtension(classOf[AtomCollection]))
      url <- fromNull(collection.getResolvedHref).flatMap(iriToUrl)
      list <- dumpLeftGetRight(logger)(downloadAllEntries(url, "next"))
    } yield list.flatMap(atomEntryToCmsEntry)
  }

  def getPageBySlug(slug: CmsSlug): Option[CmsEntry] = {
//    logger.info("getPageBySlug: slug=" + slug)
    getAllAtomEntriesIn(pagesCollection).
        flatMap(atomEntryToCmsEntry).
        find(slug.cmsEntryFilter)
  }

  def getSiblingsOf(slug: CmsSlug) = {
//    logger.info("getSiblingsOf: slug=" + slug)
    for {
      entry <- getAllAtomEntriesIn(pagesCollection).find(slug.atomEntryFilter)
      val siblings: List[CmsEntry] = entry.parent.
          flatMap(getChildrenOfParent).
          getOrElse(getTopPages)
      index <- Some(siblings.indexWhere(slug.cmsEntryFilter)) if index != -1
      cmsEntry <- atomEntryToCmsEntry(entry)
    } yield (siblings.take(index), cmsEntry, siblings.drop(index + 1))
  }

  def getTopPages() = {
//    logger.info("getTopPages")
    val x = getAllAtomEntriesIn(pagesCollection).
        filter(_.parent.isEmpty)
    val y = x.
        flatMap(atomEntryToCmsEntry)
    y
  }

  def getPostBySlug(slug: CmsSlug): Option[CmsEntry] = {
//    logger.info("getPostBySlug: slug=" + slug)
    getAllAtomEntriesIn(postsCollection).
        flatMap(atomEntryToCmsEntry).
        find(slug.cmsEntryFilter)
  }

  def getParentOfPageBySlug(slug: CmsSlug): Option[CmsEntry] = (for {
    entry <- getAllAtomEntriesIn(pagesCollection).
        find(slug.atomEntryFilter)
    link <- entry.parent
    feed <- dumpLeftGetRight(logger)(getFeed(link.href))
    parent <- feed.entries.headOption
  } yield parent).flatMap(atomEntryToCmsEntry(_))

  def getChildrenOfParent(link: AtomPubLink) = for {
    feed <- dumpLeftGetRight(logger)(getFeed(link.href))
    parent <- feed.entries.headOption
    siblings <- getChildrenOf(parent.id)
  } yield siblings

  /**
   * This should perhaps return a stream to minimize how many hits that has to be done.
   */
  private def getAllAtomEntriesIn(collection: String): List[AtomPubEntry] = {
//    logger.info("getAllAtomEntriesIn: collection=" + collection)
    val either: Either[String, List[AtomPubEntry]] = for {
      service <- atomPubClient.getService(serviceUrl).right
      workspace <- service.findWorkspace(workspaceName).
          toRight("Could not find workspace '" + workspaceName + "'.").right
      collection <- workspace.collections.find(_.title.filter(_.equals(collection)).isDefined).
          toRight("Could not find collection '" + collection + "'").right
      url <- collection.href.
          toRight("Missing or invalid 'href' on collection '" + collection + "'.").right
      entries <- downloadAllEntries(url, "next").right
    } yield entries

    dumpLeftGetRight(logger)(either).getOrElse(List.empty[AtomPubEntry])
  }

  def getFeed(url: URL) = atomPubClient.getFeed(url) match {
    case Left(error) => Left(error)
    case r@Right(feed) =>
      for {
        hub <- feed.findLink("hub")
        first <- feed.findLink("first")
      } yield hubCallback(hub.href, first.href)
      r
  }

  def downloadAllEntries(url: URL, rel: String): Either[String, List[AtomPubEntry]] =
    downloadAllEntries(url, rel, Nil, Set.empty).right.map({entries =>
//      logger.info("root download, entries=" + entries.map(list => list.map(_.title)))
      val x = entries.reverse.foldLeft(List.empty[AtomPubEntry])(_ ++ _)
//      logger.info("downloadAllEntries: x=" + x.map(_.title))
      x
    })

  /**
   * TODO: To prevent bugs, check the visitedUrls to see if the URL has already been visited.
   */
  def downloadAllEntries(url: URL, rel: String, head: List[List[AtomPubEntry]], visitedUrls: Set[URL]): Either[String, List[List[AtomPubEntry]]] = {
    getFeed(url) match {
      case Left(s) => Left(s)
      case Right(feed) =>
//        logger.info("downloadAllEntries: size=" + feed.entries.size + ", head.size=" + head.size)
//        logger.info("downloadAllEntries: entries=" + feed.entries.map(_.title))
        feed.findLink(rel, atomMimeType) match {
          case Some(next) =>
            downloadAllEntries(next.href, rel, feed.entries :: head, visitedUrls + url)
          case None =>
            Right(feed.entries :: head)
        }
    }
  }

  def atomEntryToCmsEntry(entry: AtomPubEntry) = {
    dumpLeftGetRight(logger)(AtomEntryConverter.atomEntryToCmsEntry(entry))
  }
}

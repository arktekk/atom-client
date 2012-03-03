package no.arktekk.cms

import java.io._
import java.net._
import java.util.Properties
import no.arktekk.cms.CmsUtil._
import no.arktekk.cms.atompub._
import org.apache.abdera.model.{Collection => AtomCollection}
import org.apache.commons.io.IOUtils._
import org.joda.time._
import scala.xml._
import no.arktekk.cms.CmsClient.{ExplicitConfiguration, ServiceDocumentConfiguration}

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

  def fetchEntries(offset: Int, limit: Positive): List[CmsEntry]

  def fetchEntriesForCategory(category: String, offset: Int, limit: Positive): OpenSearchResponse

  def fetchPageById(id: AtomId): Option[CmsEntry]

  def fetchChildrenOf(parent: AtomId): Option[List[CmsEntry]]

  def fetchChildrenOf(parent: CmsSlug): Option[List[CmsEntry]]

  def fetchPageBySlug(slug: CmsSlug): Option[CmsEntry]

  def fetchSiblingsOf(slug: CmsSlug): Option[(List[CmsEntry], CmsEntry, List[CmsEntry])]

  def fetchTopPages(): List[CmsEntry]

  def fetchPostBySlug(slug: CmsSlug): Option[CmsEntry]

  def fetchParentOfPageBySlug(slug: CmsSlug): Option[CmsEntry]

  def fetchEntry(url: URL): Option[CmsEntry]

  def flushCaches()
}

object CmsClient {
  type HubCallback = (URL, URL) => Unit

//  case class Configuration(serviceUrl: URL, workspaceName: String, postsCollection: String, pagesCollection: String)
  trait Configuration

  /**
   * Use this configuration if you want the CMS client to find the pages/posts feeds based on a service document and
   * collection names.
   */
  case class ServiceDocumentConfiguration(serviceUrl: URL, workspaceName: String, postsCollection: String, pagesCollection: String) extends Configuration

  /**
   * Use this configuration if you want to tell the CMS client where the pages/posts feeds URLs are.
   */
  case class ExplicitConfiguration(postsUrl: URL, pagesUrl: URL) extends Configuration

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

    val serviceUrl = new URL(properties.getProperty("serviceUrl"));
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

    val clientConfiguration = AtomPubClientConfiguration(logger, name, new File(dir, "cache"), proxyConfiguration, ttl, None)

    apply(clientConfiguration, ServiceDocumentConfiguration(serviceUrl, workspaceName, postsCollection, pagesCollection), hubCallback)
  }

  def apply(clientConfiguration: AtomPubClientConfiguration, configuration: CmsClient.ServiceDocumentConfiguration, hubCallback: HubCallback): CmsClient = {
    val atomPubClient = AtomPubClient(clientConfiguration)
    new DefaultCmsClient(clientConfiguration.logger, atomPubClient, configuration, hubCallback)
  }
}

class DefaultCmsClient(val logger: Logger, val atomPubClient: AtomPubClient, config: CmsClient.Configuration, hubCallback: CmsClient.HubCallback) extends CmsClient {

  import CmsConstants._
  import AtomEntryConverter._

  def close() {
    logger.info("Closing cms client...")
    atomPubClient.close()
  }

  def fetchEntries(offset: Int, limit: Positive) = {
//    logger.info("fetchEntries: offset=" + offset + ", limit=" + limit);
    fetchPosts().
        flatMap(atomEntryToCmsEntry).
        drop(offset).
        take(limit.toInt)
  }

  def fetchEntriesForCategory(category: String, offset: Int, limit: Positive) = {
//    logger.info("fetchEntriesForCategory: category=" + category + ", offset=" + offset + ", limit=" + limit);
    var list = fetchPosts().
        flatMap(atomEntryToCmsEntry)

    val totalResults = list.size

    list = list.filter(_.categories.exists(category ==)).
        drop(offset).
        take(limit.toInt)

    OpenSearchResponse(list, totalResults, offset, limit.toInt)
  }

  def fetchPageById(id: AtomId) = {
//    logger.info("fetchPageById: id=" + id)
    fetchPages().
        find(id.atomEntryFilter).
        flatMap(atomEntryToCmsEntry)
  }

  def fetchChildrenOf(parent: AtomId): Option[List[CmsEntry]] = {
//    logger.info("fetchChildrenOf: parent=" + parent);
    for {
      parent <- fetchPages().find(parent.atomEntryFilter)
      collection <- fromNull(parent.entry.getExtension(classOf[AtomCollection]))
      url <- fromNull(collection.getResolvedHref).flatMap(iriToUrl)
      list <- dumpLeftGetRight(logger)(downloadAllEntries(url, "next"))
    } yield {
      val entries = list.flatMap(atomEntryToCmsEntry)
//      logger.info("fetchChildrenOf: entries=" + entries.map(_.title));
      entries
    }
  }

  def fetchChildrenOf(parent: CmsSlug): Option[List[CmsEntry]] = {
//    logger.info("fetchChildrenOf: parent=" + parent);
    for {
      parent <- fetchPages().find(parent.atomEntryFilter)
      collection <- fromNull(parent.entry.getExtension(classOf[AtomCollection]))
      url <- fromNull(collection.getResolvedHref).flatMap(iriToUrl)
      list <- dumpLeftGetRight(logger)(downloadAllEntries(url, "next"))
    } yield list.flatMap(atomEntryToCmsEntry)
  }

  def fetchPageBySlug(slug: CmsSlug): Option[CmsEntry] = {
//    logger.info("fetchPageBySlug: slug=" + slug)
    fetchPages().
        flatMap(atomEntryToCmsEntry).
        find(slug.cmsEntryFilter)
  }

  def fetchSiblingsOf(slug: CmsSlug) = {
//    logger.info("fetchSiblingsOf: slug=" + slug)
    for {
      entry <- fetchPages().find(slug.atomEntryFilter)
      val siblings: List[CmsEntry] = entry.parent.
          flatMap(fetchChildrenOfParent).
          getOrElse(fetchTopPages())
      index <- Some(siblings.indexWhere(slug.cmsEntryFilter)) if index != -1
      cmsEntry <- atomEntryToCmsEntry(entry)
    } yield (siblings.take(index), cmsEntry, siblings.drop(index + 1))
  }

  def fetchTopPages() = {
//    logger.info("fetchTopPages")
    val x = fetchPages().
        filter(_.parent.isEmpty)
    val y = x.
        flatMap(atomEntryToCmsEntry)
    y
  }

  def fetchPostBySlug(slug: CmsSlug): Option[CmsEntry] = {
//    logger.info("fetchPostBySlug: slug=" + slug)
    fetchPosts().
        flatMap(atomEntryToCmsEntry).
        find(slug.cmsEntryFilter)
  }

  def fetchParentOfPageBySlug(slug: CmsSlug): Option[CmsEntry] = (for {
    entry <- fetchPages().
        find(slug.atomEntryFilter)
    link <- entry.parent
    feed <- dumpLeftGetRight(logger)(fetchFeed(link.href))
    parent <- feed.entries.headOption
  } yield parent).flatMap(atomEntryToCmsEntry(_))

  def fetchEntry(url: URL) = for {
    feed <- dumpLeftGetRight(logger)(fetchFeed(url))
    entry <- feed.entries.headOption
    cmsEntry <- atomEntryToCmsEntry(entry)
  } yield cmsEntry

  def flushCaches() {
    atomPubClient.emptyCache()
  }

  def fetchChildrenOfParent(link: AtomPubLink) = for {
    feed <- dumpLeftGetRight(logger)(fetchFeed(link.href))
    parent <- feed.entries.headOption
    siblings <- fetchChildrenOf(parent.id)
  } yield siblings

  private def fetchPosts() = {
    config match {
      case ServiceDocumentConfiguration(serviceUrl, workspaceName, postsCollection, _) =>
        fetchAllAtomEntriesIn(serviceUrl, workspaceName, postsCollection)
      case ExplicitConfiguration(postsUrl, _) =>
        val either = downloadAllEntries(postsUrl, "next")
        dumpLeftGetRight(logger)(either).getOrElse(List.empty[AtomPubEntry])
    }
  }

  private def fetchPages() = {
    config match {
      case ServiceDocumentConfiguration(serviceUrl, workspaceName, _, pagesCollection) =>
        fetchAllAtomEntriesIn(serviceUrl, workspaceName, pagesCollection)
      case ExplicitConfiguration(_, pagesUrl) =>
        val either = downloadAllEntries(pagesUrl, "next")
        dumpLeftGetRight(logger)(either).getOrElse(List.empty[AtomPubEntry])
    }
  }

  /**
   * This should perhaps return a stream to minimize how many hits that has to be done.
   */
  private def fetchAllAtomEntriesIn(serviceUrl: URL, workspaceName: String, collection: String): List[AtomPubEntry] = {
//    logger.info("fetchAllAtomEntriesIn: collection=" + collection)
    val either: Either[String, List[AtomPubEntry]] = for {
      service <- atomPubClient.fetchService(serviceUrl).right
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

  def fetchFeed(url: URL) = atomPubClient.fetchFeed(url) match {
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
    fetchFeed(url) match {
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

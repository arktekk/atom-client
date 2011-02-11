package no.arktekk.cms.atompub

import net.sf.ehcache.CacheManager
import net.sf.ehcache.config.{DiskStoreConfiguration, Configuration, CacheConfiguration}
import net.sf.ehcache.management.ManagementService
import no.arktekk.cms.CmsUtil._
import org.apache.abdera._
import org.apache.abdera.model.{Base => AtomBase, Collection => AtomCollection, Entry => AtomEntry, Feed => AtomFeed, Service => AtomService, Workspace => AtomWorkspace}
import org.apache.abdera.parser.ParseException
import org.apache.abdera.protocol.client._
import org.joda.time.Minutes
import java.io.{Closeable, File}
import java.lang.management.ManagementFactory
import java.net.URL
import javax.activation.MimeType
import no.arktekk.cms.AtomEntryConverter._
import no.arktekk.cms.CmsConstants._
import no.arktekk.cms.Logger
import scala.collection.JavaConversions

case class AtomPubLink(rel: String, mimeType: Option[MimeType], href: URL)

trait AtomLinks {
  val links: List[AtomPubLink]

  def findLink(rel: String) = AtomPubClient.findLink(links, rel)

  def findLinks(rel: String) = AtomPubClient.findLinks(links, rel)

  def findLink(rel: String, mimeType: MimeType) = AtomPubClient.findLink(links, rel, mimeType)
}

case class AtomId(private val value: String) {
  override def toString = value

  lazy val atomEntryFilter: (AtomPubEntry) => Boolean = entry => this.value.equals(entry.id.value)
}

case class AtomPubEntry(entry: AtomEntry) extends AtomLinks {

  lazy val id = AtomId(entry.getId.toASCIIString)
  lazy val self = findLink("self", atomMimeType)
  lazy val parent = findLink("parent", atomMimeType)
  lazy val updatedElement = fromNull(entry.getUpdatedElement)
  lazy val publishedElement = fromNull(entry.getPublishedElement)
  lazy val title = fromNull(entry.getTitle)
  lazy val titleElement = fromNull(entry.getTitleElement)
  lazy val contentElement = fromNull(entry.getContentElement)
  lazy val categories = fromNull(entry.getCategories).map(JavaConversions.asIterable(_).toList).getOrElse(Nil)
  lazy val links: List[AtomPubLink] = fromNull(entry.getLinks).map(JavaConversions.asIterable(_).toList).getOrElse(Nil).flatMap(atomLinkToLink)
}

case class AtomPubService(service: AtomService) {
  lazy val workspaces: List[AtomPubWorkspace] = fromNull(service.getWorkspaces).
      map(JavaConversions.asIterable(_).toList).getOrElse(Nil).
      map(AtomPubWorkspace)

  def findWorkspace(title: String): Option[AtomPubWorkspace] =
    fromNull(service.getWorkspace(title)).
        map(AtomPubWorkspace)
}

case class AtomPubWorkspace(workspace: AtomWorkspace) {
  lazy val collections: List[AtomPubCollection] = fromNull(workspace.getCollections).
      map(JavaConversions.asIterable(_).toList).getOrElse(Nil).
      map(AtomPubCollection)
}

case class AtomPubCollection(collection: AtomCollection) {
  lazy val title = fromNull(collection.getTitle)
  lazy val href = fromNull(collection.getHref).flatMap(iriToUrl)
}

case class AtomPubFeed(feed: AtomFeed) extends AtomLinks {
  lazy val entries: List[AtomPubEntry] = JavaConversions.asIterable(feed.getEntries).map(AtomPubEntry).toList
  lazy val links: List[AtomPubLink] = fromNull(feed.getLinks).map(JavaConversions.asIterable(_).toList).getOrElse(Nil).flatMap(atomLinkToLink)
}

trait AtomPubClient extends Closeable {
  def getService(serviceUrl: URL): Either[String, AtomPubService]

  def getFeed(url: URL): Either[String, AtomPubFeed]
}

object AtomPubClient {
  private val abdera = new Abdera;

  private val cacheConfiguration = new CacheConfiguration
  private var cacheManager: CacheManager = null

  def apply(logger: Logger, name: String, dir: File, proxyHost: Option[String], proxyPort: Option[Int]): AtomPubClient = {
    val abderaClient = new AbderaClient(abdera)

    if(proxyHost.isDefined) {
      logger.info("Proxy: " + proxyHost.get + ":" + proxyPort.get)
      abderaClient.setProxy(proxyHost.get, proxyPort.get)
    }

//    abderaClient.setAuthenticationSchemePriority("digest", "basic");
//    abderaClient.usePreemptiveAuthentication(true);
//    abderaClient.addCredentials(null, "WordPress Atom Protocol", null, new UsernamePasswordCredentials(properties.getProperty("username"), properties.getProperty("password")))

    val ttl = Minutes.minutes(10)

    // TODO: Add a listener to log when elements are timed out from the cache

    val serviceCache = new CacheConfiguration().timeToLiveSeconds(ttl.toStandardSeconds.getSeconds).
        timeToIdleSeconds(ttl.toStandardSeconds.getSeconds).
        maxElementsInMemory(1000).
        maxElementsOnDisk(1000).
        //        diskPersistent(true).   The objects must be serializable first
        name("service")

    val atomCache = new CacheConfiguration().timeToLiveSeconds(ttl.toStandardSeconds.getSeconds).
        timeToIdleSeconds(ttl.toStandardSeconds.getSeconds).
        maxElementsInMemory(1000).
        maxElementsOnDisk(1000).
        //        diskPersistent(true).   The objects must be serializable first
        name("atom")

    cacheManager = CacheManager.create(new Configuration().diskStore(new DiskStoreConfiguration().path(dir.getAbsolutePath)).
        defaultCache(new CacheConfiguration()).
        cache(serviceCache).
        cache(atomCache))
    cacheManager.setName(name)

    logger.info("Registering MBeans..")
    ManagementService.registerMBeans(cacheManager,
      ManagementFactory.getPlatformMBeanServer(), true, true, true, true, true)

    new DefaultAtomPubClient(logger, abdera, abderaClient, cacheManager);
  }

  /**
   * Find all links with the specified rel.
   */
  def findLinks(links: List[AtomPubLink], rel: String): List[AtomPubLink] =
    links.filter(rel == _.rel)

  /**
   * Find the first links with the specified rel.
   */
  def findLink(links: List[AtomPubLink], rel: String): Option[AtomPubLink] =
    links.find(rel == _.rel)

  /**
   * Find the first links with the specified rel *and* mimeType, and if not found, the first link with the specified rel which *does not* have a mime type.
   */
  def findLink(links: List[AtomPubLink], rel: String, mediaType: MimeType): Option[AtomPubLink] =
    findLinks(links, rel, mediaType).headOption.
        orElse(findLinks(links, rel).find(_.mimeType.isEmpty))

  /**
   * Find all links with the specified rel *and* mimeType.
   */
  def findLinks(links: List[AtomPubLink], rel: String, mimeType: MimeType): List[AtomPubLink] =
    findLinks(links, rel).filter(link => link.mimeType.isDefined && mimeType.toString.equals(link.mimeType.get.toString))
}

class DefaultAtomPubClient(logger: Logger, abdera: Abdera, client: AbderaClient, cacheManager: CacheManager) extends AtomPubClient {
  private val serviceCache = CachingAbderaClient[String, AtomService](logger, client, cacheManager.getCache("service"));
  private val feedCache = CachingAbderaClient[String, AtomFeed](logger, client, cacheManager.getCache("atom"));

  def close {
    logger.info("Closing AtomPubClient")
    // TODO: Dump the cache statistics
    cacheManager.shutdown;
    client.teardown;
  }

  def getService(serviceUrl: URL) =
    serviceCache.get(serviceUrl, parseService).right.map(AtomPubService)

  def getFeed(url: URL) =
    feedCache.get(url, parseFeed).right.
        map(AtomPubFeed)

  private def parseService(response: ClientResponse) = for {
    status <- Some(response.getStatus).filter(_ == 200).
        toRight("Unexpected status code: " + response.getStatus).right
    contentType <- Some(response.getContentType).filter(_.`match`(serviceMimeType)).
        toRight("Unexpected content type: " + response.getContentType).right
  } yield response.getDocument[AtomService].getRoot.complete[AtomService]

  private def parseFeed(response: ClientResponse): Either[String, AtomFeed] = for {
    status <- Some(response.getStatus).filter(_ == 200).
        toRight("Unexpected status code: " + response.getStatus).right
    contentType <- Some(response.getContentType).filter(_.`match`(atomMimeType)).
        toRight("Unexpected content type: " + response.getContentType).right
    feed <- parseXml(response).right
  } yield {
    logger.info("Feed has " + feed.getEntries.size + " entries")
    feed
  }

  private def parseXml(response: ClientResponse): Either[String, AtomFeed] = try {
    val root = response.getDocument[AtomFeed].getRoot.complete[AtomBase]
    if(root.isInstanceOf[AtomFeed])
      Right(root.asInstanceOf[AtomFeed])
    else
      // This will happen on random parse errors too (for example inline PHP errors)
      Left("Not a feed: " + root.getClass)
  } catch {
    case e: ParseException => Left("Unable to parse document: " + e.toString)
  }
}

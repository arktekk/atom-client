package no.arktekk.cms.atompub

import java.io.{Closeable, File}
import java.lang.management.ManagementFactory
import java.net.URL
import javax.activation.MimeType
import net.sf.ehcache.{CacheException, CacheManager}
import net.sf.ehcache.config.{DiskStoreConfiguration, Configuration, CacheConfiguration}
import net.sf.ehcache.management.ManagementService
import no.arktekk.cms.AtomEntryConverter._
import no.arktekk.cms.CmsConstants._
import no.arktekk.cms.CmsUtil._
import no.arktekk.cms.Logger
import org.apache.abdera._
import org.apache.abdera.model.{Base => AtomBase, Collection => AtomCollection, Entry => AtomEntry, Feed => AtomFeed, Service => AtomService, Workspace => AtomWorkspace}
import org.apache.abdera.parser.ParseException
import org.apache.abdera.protocol.client._
import org.joda.time.Minutes
import scala.collection.JavaConversions._
import scala.util.control.Exception._

case class AtomPubLink(rel: String, mimeType: Option[MimeType], href: URL)

trait AtomLinks {
  def links: List[AtomPubLink]

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
  lazy val categories = fromNull(entry.getCategories).map(collectionAsScalaIterable(_).toList).getOrElse(Nil)
  lazy val links: List[AtomPubLink] = fromNull(entry.getLinks).map(collectionAsScalaIterable(_).toList).getOrElse(Nil).flatMap(atomLinkToLink)
}

case class AtomPubService(service: AtomService) {
  lazy val workspaces: List[AtomPubWorkspace] = fromNull(service.getWorkspaces).
      map(collectionAsScalaIterable(_).toList).getOrElse(Nil).
      map(AtomPubWorkspace)

  def findWorkspace(title: String): Option[AtomPubWorkspace] =
    fromNull(service.getWorkspace(title)).
        map(AtomPubWorkspace)
}

case class AtomPubWorkspace(workspace: AtomWorkspace) {
  lazy val collections: List[AtomPubCollection] = fromNull(workspace.getCollections).
      map(collectionAsScalaIterable(_).toList).getOrElse(Nil).
      map(AtomPubCollection)
}

case class AtomPubCollection(collection: AtomCollection) {
  lazy val title = fromNull(collection.getTitle)
  lazy val href = fromNull(collection.getHref).flatMap(iriToUrl)
}

case class AtomPubFeed(feed: AtomFeed) extends AtomLinks {
  lazy val entries: List[AtomPubEntry] = collectionAsScalaIterable(feed.getEntries).map(AtomPubEntry).toList
  lazy val links: List[AtomPubLink] = fromNull(feed.getLinks).map(collectionAsScalaIterable(_).toList).getOrElse(Nil).flatMap(atomLinkToLink)
}

trait AtomPubClient extends Closeable {
  def fetchService(serviceUrl: URL): Either[String, AtomPubService]

  def fetchFeed(url: URL): Either[String, AtomPubFeed]

  def fetchEntry(url: URL): Either[String, AtomPubEntry]

  def emptyCache()
}

case class ProxyConfiguration(host: String, port: Int)

case class AtomPubClientConfiguration(logger: Logger, name: String, dir: File, proxy: Option[ProxyConfiguration], ttl: Option[Minutes], requestOptions: Option[RequestOptions]) {
}

object AtomPubClientConfiguration {
  def apply(logger: Logger, name: String, dir: File) = new AtomPubClientConfiguration(logger, name, dir, None, None, None)
}

object AtomPubClient {
  private val abdera = new Abdera;

  def apply(configuration: AtomPubClientConfiguration): AtomPubClient = {
    val logger = configuration.logger
    val abderaClient = new AbderaClient(abdera)

    configuration.proxy.foreach(p => {
      logger.info("Proxy: " + p.host + ":" + p.port)
      abderaClient.setProxy(p.host, p.port)
    })

//    abderaClient.setAuthenticationSchemePriority("digest", "basic");
//    abderaClient.usePreemptiveAuthentication(true);
//    abderaClient.addCredentials(null, "WordPress Atom Protocol", null, new UsernamePasswordCredentials(properties.getProperty("username"), properties.getProperty("password")))

    val ttl = configuration.ttl.getOrElse(Minutes.minutes(10))

    // TODO: Add a listener to log when elements are timed out from the cache

    val serviceCache = new CacheConfiguration().timeToLiveSeconds(ttl.toStandardSeconds.getSeconds).
        timeToIdleSeconds(ttl.toStandardSeconds.getSeconds).
        maxElementsInMemory(1000).
        maxElementsOnDisk(1000).
        //        diskPersistent(true).   The objects must be serializable first
        name("service")

    val feedCache = new CacheConfiguration().timeToLiveSeconds(ttl.toStandardSeconds.getSeconds).
        timeToIdleSeconds(ttl.toStandardSeconds.getSeconds).
        maxElementsInMemory(1000).
        maxElementsOnDisk(1000).
        name("feed")

    val entryCache = new CacheConfiguration().timeToLiveSeconds(ttl.toStandardSeconds.getSeconds).
        timeToIdleSeconds(ttl.toStandardSeconds.getSeconds).
        maxElementsInMemory(1000).
        maxElementsOnDisk(1000).
        name("entry")

    val cacheManager = CacheManager.create(new Configuration().diskStore(new DiskStoreConfiguration().path(configuration.dir.getAbsolutePath)).
        defaultCache(new CacheConfiguration()).
        cache(serviceCache).
        cache(feedCache).
        cache(entryCache).
        name(configuration.name))

    logger.info("Registering MBeans..")

    val registry = new ManagementService(
      cacheManager,
      ManagementFactory.getPlatformMBeanServer,
      true, true, true, true, true)

    try {
      registry.init()
    }
    catch {
      case e: CacheException => {
        registry.dispose()
        registry.init()
      }
    }

    new DefaultAtomPubClient(configuration.logger, abdera, abderaClient, configuration.requestOptions.getOrElse(CachingAbderaClient.defaultRequestOptions), cacheManager, registry);
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

class DefaultAtomPubClient(logger: Logger, abdera: Abdera, client: AbderaClient, requestOptions: RequestOptions, cacheManager: CacheManager, registry: ManagementService) extends AtomPubClient {
  private val serviceCache = CachingAbderaClient[String, AtomService](logger, client, requestOptions, cacheManager.getCache("service"));
  private val feedCache = CachingAbderaClient[String, AtomFeed](logger, client, requestOptions, cacheManager.getCache("feed"));
  private val entryCache = CachingAbderaClient[String, AtomEntry](logger, client, requestOptions, cacheManager.getCache("entry"));

  def close() {
    logger.info("Unregistering JMX beans")
    allCatch {registry.dispose()}
    logger.info("Closing AtomPubClient")
    // TODO: Dump the cache statistics
    allCatch {cacheManager.shutdown()}
    allCatch {client.teardown}
  }

  def fetchService(serviceUrl: URL) =
    serviceCache.get(serviceUrl, parseService).right.map(AtomPubService)

  def fetchFeed(url: URL) =
    feedCache.get(url, parseFeed).right.
        map(AtomPubFeed)

  def fetchEntry(url: URL) =
    entryCache.get(url, parseEntry).right.
        map(AtomPubEntry)

  def emptyCache() {
    serviceCache.cache.flush()
    feedCache.cache.flush()
    entryCache.cache.flush()
  }

  private def parseService(response: ClientResponse) = for {
    status <- Some(response.getStatus).filter(_ == 200).
      toRight("Unexpected status code, got: " + response.getStatus + ", expected 200.").right
    contentType <- Some(response.getContentType).filter(_.`match`(serviceMimeType)).
      toRight("Unexpected content type, got: " + response.getContentType + ", expected: " + serviceMimeType + ".").right
  } yield response.getDocument[AtomService].getRoot.complete[AtomService]

  private def parseFeed(response: ClientResponse): Either[String, AtomFeed] = for {
    status <- Some(response.getStatus).filter(_ == 200).
      toRight("Unexpected status code, got: " + response.getStatus + ", expected 200.").right
    contentType <- Some(response.getContentType).filter(_.`match`(atomMimeType)).
      toRight("Unexpected content type, got: " + response.getContentType + ", expected: " + atomMimeType + ".").right
    feed <- parseXml(response).right
  } yield {
    logger.info("Feed has " + feed.getEntries.size + " entries")
    feed
  }

  private def parseEntry(response: ClientResponse): Either[String, AtomEntry] = for {
    status <- Some(response.getStatus).filter(_ == 200).
      toRight("Unexpected status code, got: " + response.getStatus + ", expected 200.").right
    contentType <- Some(response.getContentType).filter(_.`match`(atomEntryMimeType)).
      toRight("Unexpected content type, got: " + response.getContentType + ", expected: " + atomEntryMimeType + ".").right
    entry <- parseEntryXml(response).right
  } yield {
    entry
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

  private def parseEntryXml(response: ClientResponse): Either[String, AtomEntry] = try {
    val root = response.getDocument[AtomFeed].getRoot.complete[AtomBase]
    if(root.isInstanceOf[AtomEntry])
      Right(root.asInstanceOf[AtomEntry])
    else
      // This will happen on random parse errors too (for example inline PHP errors)
      Left("Not a feed: " + root.getClass)
  } catch {
    case e: ParseException => Left("Unable to parse document: " + e.toString)
  }
}

package no.arktekk.cms.atompub

import java.net.URL
import net.sf.ehcache.{Element, Cache}
import no.arktekk.cms.CmsUtil._
import org.apache.abdera.protocol.client.{ClientResponse, AbderaClient}
import no.arktekk.cms.Logger

object CachingAbderaClient {
  def apply[E, T](logger: Logger, client: AbderaClient, cache: Cache): CachingAbderaClient[E, T] = {
    new CachingAbderaClient(logger, client, cache);
  }
}

class CachingAbderaClient[E, T](logger: Logger, val client: AbderaClient, val cache: Cache) {
  def get(u: URL, generator: (ClientResponse => Either[E, T])): Either[E, T] = {

    val url = u.toExternalForm

    val element = cache.get(url);

    if (element != null) {
      logger.info("Cache hit: " + url)
      return element.getObjectValue.asInstanceOf[Either[E, T]];
    }

    cache.synchronized({
      val element = cache.get(url);

      if (element != null) {
        logger.info("Cache hit: " + url)
        return element.getObjectValue.asInstanceOf[Either[E, T]];
      }

      val response = time(logger)("Fetching " + url, client.get(url))

      //logger.info("------------------------------------------------------------")
      //logger.info(response.getStatus + " " + response.getStatusText)
      //response.getHeaderNames.
      //    map({header => (header, response.getHeader(header))}).
      //    foreach({t => logger.info(t._1 + ": " + t._2)})

      try {
        val value = time(logger)("Processing " + url, generator(response));

        logger.info("Store as " + url + ", ttl=" + cache.getCacheConfiguration.getTimeToLiveSeconds)
        cache.put(new Element(url, value));

        return value;
      } finally {
        response.release();
      }
    });
  }
}

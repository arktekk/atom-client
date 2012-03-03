package no.arktekk.cms.atompub

import java.net.URL
import net.sf.ehcache.{Element, Cache}
import no.arktekk.cms.CmsUtil._
import no.arktekk.cms.Logger
import org.apache.abdera.protocol.client.util.MethodHelper
import org.apache.abdera.protocol.client.{RequestOptions, ClientResponse, AbderaClient}

object CachingAbderaClient {
  def apply[E, T](logger: Logger, client: AbderaClient, requestOptions: RequestOptions, cache: Cache): CachingAbderaClient[E, T] = {
    new CachingAbderaClient(logger, client, requestOptions, cache);
  }

  val defaultRequestOptions = MethodHelper.createDefaultRequestOptions()

  /**
   * Confluence is unable to handle Accept-Encoding and Accept-Charset properly.
   *
   * Is fixed in version 3.5.?.
   */
  val confluenceFriendlyRequestOptions = MethodHelper.createDefaultRequestOptions().
    setAcceptEncoding().
    setAcceptCharset()

}

class CachingAbderaClient[E, T](logger: Logger, val client: AbderaClient, requestOptions: RequestOptions, val cache: Cache) {
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

      val response = time(logger)("Fetching " + url, client.get(url, requestOptions))

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

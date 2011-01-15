package no.arktekk.cms

import java.net.{MalformedURLException, URL}
import java.util.UUID

object CmsUtil {
  def time[T](logger: Logger)(operation: String, f: => T) = {
    logger.info("Timing " + operation)
    val start = System.currentTimeMillis
    val t: T = f
    val end = System.currentTimeMillis
    logger.info("Done, completed in " + (end - start) + "ms")
    t
  }

  /**
   * Replace with Option()
   */
  def fromNull[T](v: T) = if (v != null) Some(v) else None

  def parseInt(s: String): Option[Int] = {
    try
      Some(s.toInt)
    catch {
      case _: NumberFormatException => None
    }
  }

  def parsePositive(s: String): Option[Positive] = parseInt(s).flatMap(Positive.apply)

  def parseUrl(s: String): Option[URL] = {
    try
      Some(new URL(s))
    catch {
      case _: MalformedURLException => None
    }
  }

  def parseUuid(s: String): Option[UUID] = {
    try
      Some(UUID.fromString(s))
    catch {
      case _: IllegalArgumentException => None
    }
  }

  def redSpan(s: String) = <span style="color: red">{s}</span>

  def dumpLeftGetRight[A](logger: Logger)(either: Either[String, A]): Option[A] = either match {
    case Left(error) =>
      logger.warn(error)
      None
    case Right(a) =>
      Some(a)
  }

  // EHCache WTF
  def skipEhcacheUpdateCheck = System.setProperty("net.sf.ehcache.skipUpdateCheck", "true")
}

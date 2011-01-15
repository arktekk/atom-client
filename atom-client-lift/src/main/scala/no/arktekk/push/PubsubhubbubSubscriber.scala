package no.arktekk.push

import java.io.{IOException, Closeable}
import java.net.{ConnectException, URL}
import java.util.UUID
import net.liftweb.common._
import org.apache.commons.httpclient._
import org.apache.commons.httpclient.methods._
import org.apache.commons.httpclient.util.EncodingUtil
import no.arktekk.cms.CmsUtil._
import no.arktekk.cms.Positive
import scala.util.Random
import scala.actors.DaemonActor

/*
 * See http://pubsubhubbub.googlecode.com/svn/trunk/pubsubhubbub-core-0.3.html
 */

trait PubsubhubbubSubscriber extends Closeable {
  def addTopicToHub(hub: URL, topic: URL)

  def verify(topic: URL, mode: String, challenge: String, leaseSeconds: Option[Positive], verifyToken: Option[String]): VerificationResponse
}

class DefaultPubsubhubbubSubscriber(val random: Random, val subscribeUrl: URL) extends PubsubhubbubSubscriber {
  private val actor = {
    val actor = new PubsubhubbubActor(random, subscribeUrl)
    actor.start
    actor
  }

  def addTopicToHub(hub: URL, topic: URL) =
    actor ! AddTopicToHub(hub, topic)

  def verify(topic: URL, mode: String, challenge: String, leaseSeconds: Option[Positive], verifyToken: Option[String]) =
    (actor !? VerifyRequest(topic, mode, challenge, leaseSeconds, verifyToken)).asInstanceOf[VerificationResponse]

  def close = {
    actor.close
  }
}

sealed class PubsubhubbubMessage

private case class AddTopicToHub(topicUrl: URL, hubUrl: URL) extends PubsubhubbubMessage

private case object RequestSubscription extends PubsubhubbubMessage

private case class VerifyRequest(topic: URL, mode: String, challenge: String, leaseSeconds: Option[Positive], verifyToken: Option[String]) extends PubsubhubbubMessage

case class VerificationResponse(statusCode: Int, body: String) extends PubsubhubbubMessage

private case object Stop

private class PubsubhubbubActor(random: Random, subscriber: URL) extends DaemonActor with Logger with Closeable {
  var topics = Map.empty[URL, Map[URL, SubscriptionActor]]

  override def start() = {
    info("Pubsubhubbub subscriber starting..")
    super.start
  }

  def close = {
    this !? Stop
  }

  def act() = loop{
    react{
      case AddTopicToHub(hub, topicUrl) =>
        topics.get(topicUrl) match {
          case None =>
            info("New topic: " + topicUrl)
            val subscriptions = Map(hub -> newSubscription(subscriber, hub, topicUrl))
            topics = topics + (topicUrl -> subscriptions)
          case Some(topic) =>
            topic.get(hub) match {
              case None =>
                info("Existing topic, new hub. topic=" + topicUrl + ", hub=" + hub)
                val subscriptions = Map(hub -> newSubscription(subscriber, hub, topicUrl))
                topics = topics + (topicUrl -> subscriptions)
              case Some(subscription) =>
                info("Existing topic, existing subscription. topic=" + topicUrl + ", hub=" + hub)
//                subscription ! RequestSubscription
            }
        }
      case r@VerifyRequest(topic, _, _, _, token) =>
        info("Verifying " + topic + ", token=" + token)
        reply(verifyRequest(r))
      case Stop =>
        info("Stopping pubsubhubbub subscriber...")
        topics.foreach(topic => topic._2.foreach({
          hub =>
            info("Stopping subscription for topic=" + topic._1 + ", hub=" + hub._1)
            hub._2 !? Stop
        }))
        info("Stopped pubsubhubbub subscriber")
        reply(Unit)
      case x: AnyRef =>
        info("Unknown message: type=" + x.getClass)
        reply(Unit)
    }
  }

  def newSubscription(subscriber: URL, hub: URL, topic: URL) = new SubscriptionActor(random, subscriber, hub, topic) {
    start
    this ! RequestSubscription
  }

  def verifyRequest(r: VerifyRequest): VerificationResponse = {
    r.verifyToken.flatMap(parseUuid) match {
      case None =>
        VerificationResponse(404, "The token is required.")
      case Some(token) =>
        topics.get(r.topic) match {
          case None =>
            VerificationResponse(404, "Topic not found")
          case Some(subscriptions) =>
            subscriptions.find(_._2.token.equals(token)) match {
              case None =>
                VerificationResponse(404, "Invalid token")
              case Some(subscription) =>
                (subscription._2 !? r).asInstanceOf[VerificationResponse]
            }
        }
    }
  }
}

/**
 * An actor for a single topic at a single hub.
 */
class SubscriptionActor(random: Random, subscriber: URL, hub: URL, topic: URL) extends DaemonActor with Logger {
  val token = UUID.nameUUIDFromBytes({
    val bytes = Array.ofDim[Byte](8)
    random.nextBytes(bytes)
    bytes
  })
  private val httpClient = new HttpClient

  var subscribed = false
  var verified = false

  def act() = loop{
    react{
      case RequestSubscription =>
      // Always register for now
        requestSubscription
        subscribed = true
      case r@VerifyRequest(topic, mode, challenge, leaseSeconds, verifyToken) =>
        info("Verifying request for " + topic + ", mode=" + mode)
        info("topic=" + topic)
        info("mode=" + mode)
        info("challenge=" + challenge)
        info("leaseSeconds=" + leaseSeconds)
        info("verifyToken=" + verifyToken)
        verified = true
        reply(VerificationResponse(200, challenge))
      case Stop =>
        reply(Unit)
        exit
      case x: AnyRef =>
        info("Unknown message: type=" + x.getClass)
    }
  }

  def requestSubscription() {
    val post = new PostMethod(hub.toExternalForm)

    // with this actor model we can't support sync requests as this actor would be
    // calles by the web framework while we're waiting for the POST to complete
    val values = Array(
      new NameValuePair("hub.callback", subscriber.toExternalForm),
      new NameValuePair("hub.mode", "subscribe"),
      new NameValuePair("hub.topic", topic.toExternalForm),
      new NameValuePair("hub.verify", "async"),
      new NameValuePair("hub.verify_token", token.toString))

    info("Sending subscription request")
    info(" Hub URL        = " + hub)
    info(" Topic URL      = " + topic)
    info(" Subscriber URL = " + subscriber)
    info("body")
    info(EncodingUtil.formUrlEncode(values, "utf-8"))
    values.foreach(value => post.addParameter(value))

    try {
      val result = httpClient.executeMethod(post)
      info("Subscription result = " + result)
      info("Body:")
      info(post.getResponseBodyAsString)
    }
    catch {
      case _: ConnectException =>
        info("Unable to connect to hub. URL=" + hub)
      case _: IOException =>
        info("Error while connecting to/reading from hub. URL=" + hub)
    }
  }
}

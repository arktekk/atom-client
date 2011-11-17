package no.arktekk.push

import java.net.URL
import org.mortbay.jetty.Server
import org.mortbay.jetty.servlet._
import org.specs2.mutable._
import scala.util.Random
import scala.collection.JavaConversions
import no.arktekk.cms.CmsUtil._
import no.arktekk.cms.Positive
import javax.servlet.http._

class PubsubhubbubSubscriberSpec extends Specification {
  val httpPort = 8908
  val subscriber = new URL("http://localhost:" + httpPort + "/subscriber")
  val hub = new URL("http://localhost:4567/")
  val topic = new URL("http://localhost/~trygvis/wordpress/?atompub=list&pt=abc&pg=1")
  val random = new Random(0)
  val pubsubhubbub = new DefaultPubsubhubbubSubscriber(random, subscriber)

  val servlet = new HttpServlet() {
    override def doGet(request: HttpServletRequest, response: HttpServletResponse) = {
      println("query: " + request.getQueryString)
      JavaConversions.asIterator(request.getParameterNames).foreach(println)

      for {
        topic <- Option(request.getParameter("hub.topic")).flatMap(parseUrl)
        mode <- Option(request.getParameter("hub.mode"))
        challenge <- Option(request.getParameter("hub.challenge"))
        val leaseSeconds = Option(request.getParameter("hub.lease_seconds")).flatMap(parseInt).flatMap(Positive.apply)
        val verifyToken = Option(request.getParameter("hub.verify_token"))
      } yield {
        val r = pubsubhubbub.verify(topic, mode, challenge, leaseSeconds, verifyToken)
        println("status=" + r.statusCode)
        println("Body:")
        println(r.body)
        response.setStatus(r.statusCode);
        response.getWriter.print(r.body)
      }
    }

    override def doPost(request: HttpServletRequest, response: HttpServletResponse) = {
      println("POST")
//      JavaConversions.asIterator(request.getParameterNames).foreach({
//        (name: String) => println(name + "=" + request.getParameter(name))
//      })
    }
  }

  val server = new Server(httpPort)
  server.addHandler(new ServletHandler() {
    addServletWithMapping(new ServletHolder(servlet), "/subscriber")
  })
  server.start

  doAfterSpec {
    println("Stopping...")
    pubsubhubbub.close
    server.stop
  }

  "Pubsubhubbub" should {
    "work" in {

      pubsubhubbub.addTopicToHub(hub, topic)

//      pubsubhubbub.addTopicToHub(hub2, topic)
//      pubsubhubbub.addTopicToHub(hub2, topic)

//      pubsubhubbub.verify(topic, "subscribe", )

      Thread.sleep(10000 * 1000)
      "a" must_== "a"
    }
  }
}

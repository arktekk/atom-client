package no.arktekk.push

import net.liftweb.common._
import net.liftweb.http._
import no.arktekk.cms.CmsUtil._
import no.arktekk.cms.Logger

class PubsubhubbubSubscriberLift(logger: Logger, pubsubhubbub: PubsubhubbubSubscriber) {
  def dispatch: LiftRules.DispatchPF = {
    case r@Req("pubsubhubbub" :: Nil, _, method) => method match {
      case GetRequest =>
        logger.info("Pubsubhubbub request, parameters:")
        r.request.params.foreach(param => println("name=" + param.name + ", values=" + param.values))
        val result: Either[String, VerificationResponse] = for {
          mode <- r.request.param("hub.mode").headOption.
              toRight("Missing required parameter 'hub.mode'").right
          topic <- r.request.param("hub.topic").headOption.flatMap(parseUrl).
              toRight("Missing or invalid required parameter 'hub.topic'").right
          challenge <- r.request.param("hub.challenge").headOption.
              toRight("Missing required parameter 'hub.challenge'").right
        } yield {
          // TODO leaseSeconds should not be ignored if the value is there but invalid
          val leaseSeconds = r.request.param("hub.lease_seconds").headOption.flatMap(parsePositive)
          val verifyToken = r.request.param("hub.verify_token").headOption
          pubsubhubbub.verify(topic, mode, challenge, leaseSeconds, verifyToken)
        }

        logger.info("Pubsubhubbub request, result:")

        result match {
          case Left(error) =>
            println("error:")
            println(error)
            // Bad request
            () => Full(PlainTextResponse(error, List.empty, 400))
          case Right(VerificationResponse(code, body)) =>
            () => Full(PlainTextResponse(body, List.empty, code))
          case _ =>
            () => Full(PlainTextResponse("Unknown error", List.empty, 500))
        }
      case m =>
        () => Full(MethodNotAllowedResponse())
    }
  }
}

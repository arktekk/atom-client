package no.arktekk.cms

import java.io.File
import java.net.URL
import no.arktekk.cms.atompub._
import no.arktekk.cms.CmsUtil._
import org.apache.abdera.ext.opensearch.model.OpenSearchDescription
import scala.collection.immutable.SortedMap
import scala.collection.JavaConversions

import Utils._
import org.apache.abdera.model.{ExtensibleElement, Collection => AtomCollection}

object AtomPubCli extends Application {
  val dir = new File(System.getProperty("user.home"), ".cache")

  val client = AtomPubClient(ConsoleLogger, "cli", dir)
  val logger = ConsoleLogger

  System.setProperty("net.sf.ehcache.skipUpdateCheck", "true")
  logger.info("Getting service document...")

  try {
    loop(ServiceCommand(logger, new URL("http://localhost/~trygvis/wordpress/?atompub=service")))
  }
  catch {
    case e => e.printStackTrace
  }

  client.close

  def loop(startCommand: Command) {
    var command: Option[Command] = Some(startCommand)
    var commands: Map[String, Command] = Map.empty

    while (true) {
      commands = command.get.execute match {
        case Left(error) =>
          println(error);
          Map(("retry" -> command.get)) // Re-run the last command
        case Right(commands) =>
          commands
      }

      println
      println("Options")
      command = selectCommand(commands)
      if (command.isEmpty) {
        return;
      }

      println
    }
  }

  def selectCommand(c: Map[String, Command]): Option[Command] = {
    val commands: SortedMap[String, Command] = SortedMap.empty[String, Command] ++ c

    commands.foreach(t => println(t._1 + ": " + t._2.text))

    readLine("Select: (q quits): ") match {
      case "q" => None
      case choice => c.get(choice) match {
        case None =>
          println("fail..")
          None
        case s =>
          s
      }
    }
  }
}

trait Command {
  val text: String

  def execute: Either[String, Map[String, Command]]
}

case class ServiceCommand(logger: Logger, url: URL) extends Command {
  val text = "Service"

  def execute = for {
    service <- AtomPubCli.client.getService(url).right
  } yield {
    dumpService(service)
    toCommands(service)
  }

  def dumpService(service: AtomPubService) {
    println("dumping service..")
  }

  def toCommands(service: AtomPubService): Map[String, Command] = {
    commandsToMap("", service.workspaces.
        map(workspace => workspace.collections.
        flatMap(collection => collection.href.map(FeedCommand(logger, this, _, collection.title)))).
        flatten)
  }
}

case class FeedCommand(logger: Logger, up: Command, url: URL, title: Option[String]) extends Command {
  lazy val text = "Fetch " + title.map("'" + _ + "', ").getOrElse("untitled feed ") + url

  def execute = for {
    feed <- AtomPubCli.client.getFeed(url).right
  } yield {
    println("Feed overview")
    println("Id: " + feed.feed.getId)
    println("Title: " + feed.feed.getTitle)

    println("Links:")
    feed.links.map(linkToString).map(" " + _).foreach(println)

    val extensions = fromNull(feed.feed.getExtensionAttributes).map(JavaConversions.asIterable(_).toList).getOrElse(Nil)
    extensions.foreach(println)
    feed.entries.zipWithIndex.foldLeft(Map.empty[String, Command])(
      (map, t) => map + ("e" + t._2 -> EntryOverviewCommand(logger, this, feed, t._1))
    )

    Map(("up" -> up)) ++
        commandsToMap("e", feed.entries.map(EntryOverviewCommand(logger, this, feed, _))) ++
        commandsToMap("l", feed.links.map(link => FeedCommand(logger, this, link.href, Some("rel=" + link.rel)))) ++
        findExtensions(logger, this, feed.feed)
  }
}

case class EntryOverviewCommand(logger: Logger, feedCommand: FeedCommand, feed: AtomPubFeed, entry: AtomPubEntry) extends Command {
  val text = "View entry '" + entry.title.getOrElse("No title") + "'"

  def execute = Right({
    println("Entry:")
    println(" Feed URL: " + feedCommand.url)
    println(" Feed id: " + feed.feed.getId)
    println(" Entry id: " + entry.entry.getId)
    println(" Title: " + entry.entry.getTitle)

    println(" Links")
    def f = (link: AtomPubLink) => "  " + linkToString(link)
    entry.links.map(f).foreach(println)

    Map(("up" -> feedCommand),
      ("details" -> EntryDetailsCommand(this))) ++
        commandsToMap("l", entry.links.map(link => FeedCommand(logger, this, link.href, Some("rel=" + link.rel)))) ++
        findExtensions(logger, this, entry.entry)
  })
}

case class EntryDetailsCommand(up: EntryOverviewCommand) extends Command {
  val text = "Entry details"

  def execute = Right({
    val entry = up.entry
    println(" " + fromNull(entry.entry.getSummary).map("Summary: " + _).getOrElse("No summary"))
    val content = fromNull(entry.entry.getContent)

    if (content.isEmpty) {
      println(" No content")
    }
    else {
      println("Content:")
      println(content.get)
    }

    Map(("up" -> up))
  })
}

object Utils {
  def linkToString(link: AtomPubLink) =
    "rel=" + link.rel + link.mimeType.map(", type=" + _).getOrElse("") + ", url=" + link.href

  def commandsToMap(prefix: String, commands: List[Command]) =
    commands.zipWithIndex.map(t => (prefix + t._2) -> t._1).toMap

  def findExtensions(logger: Logger, up: Command, extensible: ExtensibleElement): Map[String, Command] = {
    logger.info("collection=" + extensible.getExtension(classOf[AtomCollection]))

    for {
      openSearch <- Option(extensible.getExtension(classOf[OpenSearchDescription]))
    }
    yield {
      println("Has an element open search")
//      println(openSearch)
    }

    val collection = for {
      collection <- Option(extensible.getExtension(classOf[AtomCollection]))
      url <- AtomEntryConverter.iriToUrl(collection.getHref)
    } yield {
      val title = Option(collection.getTitle)
      println("Atom collection: " + title.getOrElse("<untitled>"))
      println(" href=" + collection.getHref)
      Map("c" -> FeedCommand(logger, up, url, title))
    }

    collection.getOrElse(Map.empty)
  }
}

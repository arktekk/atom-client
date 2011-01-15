package no.arktekk.cms

import org.apache.abdera.Abdera
import org.apache.abdera.model._
import org.apache.axiom.om.impl.llom.OMTextImpl
import org.apache.commons.io.IOUtils
import org.specs._
import scala.collection.JavaConversions
import scala.xml.{Text => XmlText, _}

class AtomEntryConverterSpec extends Specification {
  import AtomEntryConverter._

  val nbsp: Char = '\u00A0'
  val EOL = System.getProperty("line.separator")
  val feed = {
    val abdera = new Abdera
    val url = classOf[AtomEntryConverterSpec].getResource("/posts.atom.xml")
    val stream = url.openStream
    try
    {
      abdera.getParser.parse[Feed](stream).
          getRoot.
          complete[Feed]
    } catch {
      case e => e.printStackTrace; throw e
    } finally {
      IOUtils.closeQuietly(stream)
    }
  }

  def div(text: String) = Elem(null, "div", Null, TopScope, XmlText(text))

//  "adjustDivs" should {
//    "Convert a single line to a <div>" in {
//      CmsClient.adjustDivs(<div>abc</div>) must_== <div>abc</div>
//    }
//
//    "Convert a single line starting with extra EOL to a <div>" in {
//      CmsClient.adjustDivs(<div>{EOL + EOL + "abc"}</div>) must_== <div>abc</div>
//    }
//
//    "Convert a single line with extra EOL to a <div>" in {
//      CmsClient.adjustDivs(<div>{"abc" + EOL + EOL}</div>) must_== <div>abc</div>
//    }
//
//    "Convert a divs recursively" in {
//      CmsClient.adjustDivs(<div><div>{"abc"}</div></div>) must_== <div><div>abc</div></div>
//    }
//
//    "Convert two lines to two paragraphs" in {
//      CmsClient.adjustDivs(<div>{"abc" + EOL + "123"}</div>) must_== <div><p>abc</p><p>123</p></div>
//    }
//
//    "Convert two lines with double EOL to two paragraphs" in {
//      CmsClient.adjustDivs(<div>{"abc" + EOL + EOL + "123"}</div>) must_== <div><p>abc</p><p>123</p></div>
//    }
//
//    "Convert two lines with extra at the end EOL to two paragraphs" in {
//      CmsClient.adjustDivs(<div>{"abc" + EOL + "123" + EOL + EOL}</div>) must_== <div><p>abc</p><p>123</p></div>
//    }
//
//    "Convert text interleaved with divs" in {
//      CmsClient.adjustDivs(<div>{"abc" + EOL + "rst"}<div>{"123" + EOL}</div>{"xyz" + EOL}</div>) must_== <div><p>abc</p><p>rst</p><div>123</div>xyz</div>
//    }
//
//    "meh" in {
//      CmsClient.adjustDivs(<div><div>pre<a href="href">link</a>post</div></div>) must_== <div><div>pre<a href="href">link</a>post</div></div>
//    }
//  }
//
//  "omTextTo" should {
//    "Convert a single line to a Text() node" in {
//      CmsClient.omTextToSeqNode(new OMTextImpl("abc", null)) must_== XmlText("abc")
//    }
//
//    "Convert two lines to two paragraphs" in {
//      CmsClient.omTextToSeqNode(new OMTextImpl("abc" + EOL + "123", null)) must_== List(<p>abc</p>, <p>123</p>)
//    }
//
//    "Convert two lines with double EOL to two paragraphs" in {
//      CmsClient.omTextToSeqNode(new OMTextImpl("abc" + EOL + EOL + "123", null)) must_== List(<p>abc</p>, <p>123</p>)
//    }
//
//    "Convert two lines with extra at the end EOL to two paragraphs" in {
//      CmsClient.omTextToSeqNode(new OMTextImpl("abc" + EOL + "123" + EOL + EOL, null)) must_== List(<p>abc</p>, <p>123</p>)
//    }
//  }

  "atomTextToString" should {
    "parse HTML text" in {
      val entry = feed.getEntry("http://javazone11.wordpress.com/?p=28")
      Text.Type.HTML must_== entry.getTitleType
//      println(entry.getTitle)
      val actual = atomTextToString(entry.getTitleElement)

      actual.right.get must_== "JavaZone will be streamed live in HD to NTNU Campus"
    }
  }

  "atomContentToNode" should {
//    "work for all entries" in {
//      JavaConversions.asIterable(feed.getEntries).
//          foreach(entry => if(CmsClient.atomContentToNode(entry.getContentElement).isEmpty) fail(entry.getId.toURL.toExternalForm))
//    }

//    "parse XHTML entries" in {
//      val entry = feed.getEntry("http://javazone11.wordpress.com/?p=34")
//      Content.Type.XHTML must_== entry.getContentType
//
//      val expected =
//        <div><p>There has been quite an intense public debate regarding the Data Retention Directive (abbreviated DLD in Norwegian) for a while now. As this is a topic that many people in the Java community have strong opinions about we have decided to enable the JavaZone audience to take part in the debate as well. If you are wondering what DRD (DLD) is all about you should not miss this debate!</p><p>On the pro-DRD-side we two experts from the Norwegian FBI (Kripos): Reidar Brusgaard and Rune Utne Reitan. Their opponents are: Martin Bekkelund (Friprogsenteret) and Torgeir Waterhouse (IKT-Norge).</p><p>The debate will take place on the 8th of September in room 1, lead by Christer Gundersen from The National Center for Open Source Software (Friprogsenteret). Both sides will make an initial short presentation outlining their view on this issue, and then the debate will start. The audience will be able to ask questions to the debate participants.</p><p>We hope that this debate will give the audience a more facts-based view on the matter and enable the Java community to voice their opinion.</p><a href="http://javazone.no/incogito10/events/JavaZone%202010/sessions/Paneldebatt:%20Datalagringsdirektivet%20(DLD)">Read more here</a>. Note that the debate will be held in Norwegian.</div>
//      val actual = CmsClient.atomContentToNode(entry.getContentElement)
//      println(actual)
//      actual.get must ==/(expected).ordered
//    }

    // This spec does not apply anymore as we un-fucked wordpress instead.
    "parse HTML entries" in {
      val entry = feed.getEntry("http://javazone11.wordpress.com/?p=28")
      Content.Type.HTML must_== entry.getContentType

      val expected: NodeSeq =
        <wrapper><div>
<div>
<p>For the first time in the JavaZone history, the entire event will be streamed live in HD to Campus Gløshaugen at <a href="http://www.ntnu.no/">NTNU</a> in Trondheim.</p>
<p>JavaZone HD is a brand new concept which aims to involve students from Trondheim in the knowledge exchange made possible by JavaZone. The event is free for students to attend and the different speakers will be streamed simultaneously to one of auditoriums on campus. You will be able to choose which speaker you would like to listen to thru your headset.</p>
<p>javaBin, the organization behind JavaZone, aims to establish contacts and to promote exchange of knowledge, experience and views between those who are interested in Java technology. An important audience that has never been adequately involved in this objective is the students.</p>
<p>Together with our partner Tandberg, we want to involve and share knowledge with students in a much greater extent. We wish to set up a live stream in of all speeches in HD quality, where students can sit in same auditorium and hear the speaker they want. Sound is selected by the individual via headsets. JavaZone has used a similar setup for overflow of the halls in Oslo Spektrum the last three years with great success.</p>
<p>We think it is important to create an intimate experience. Therefore, among other things, all speeches will appear in the same auditorium. The atmosphere created here in this hall will be continued in a social ClubZone event with entertainment throughout the evening.</p>
<p>JavaZone HD will:</p>
<ul>
<li>Use a solution with high technical quality</li>
<li>Linking students closer to the academic environment</li>
<li>Hold an event with food, entertainment and a party</li>
</ul>
<p>Agenda Wednesday 8th Sept:</p>
<ul>
<li>Live streaming of the lecture to an auditorium at Gløshaugen</li>
<li>Free lunch and dinner</li>
<li>ClubZone at a nightclub in Trondheim</li>
</ul>
<p>{nbsp}</p>
<p>Agenda Thursday 9th Sept:</p>
<ul>
<li>Live streaming of the lecture to an auditorium at Gløshaugen</li>
<li>Free lunch</li>
</ul>
<p>{nbsp}</p>
<p><img src="http://jz10.java.no/images/javazone-hd.png"/></p>
</div>
</div>
<p>{nbsp}</p>
<br></br>          </wrapper>.asInstanceOf[Node].child

      val actual = atomContentToNode(entry.getContentElement)

//      println("------------------------------------------------------------")
//      println("Actual: ")
//      println(actual.get)
//      println("------------------------------------------------------------")

      actual.right.get must ==/(expected).ordered
    }

//    "parse text entries" in {
//      val entry = feed.getEntry("http://javazone11.wordpress.com/?p=22")
//      entry.getContentType must_== Content.Type.TEXT
//
//      val expected = <div><p>After a lot of hard work in the JavaZone Program Committee, we are happy to announce that we will start to distribute feedback to everyone who have submitted abstracts for this year's conference in a couple of days.</p><p>Shortly after the speakers have received their feedback, we will start to confirm them officially. The conference schedule will then be published gradually.</p><p>Our agenda application will be available to July 1st and we will add speakers and presentations to the schedule on as soon as the speakers confirm that they will appear at the conference.</p></div>
//
//      val actual = CmsClient.atomContentToNode(entry.getContentElement)
//      actual.get must ==/(expected).ordered
//    }
  }
}

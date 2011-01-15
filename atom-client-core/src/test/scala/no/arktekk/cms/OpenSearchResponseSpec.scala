package no.arktekk.cms

import org.specs._
import scala.xml.NodeSeq
import no.arktekk.cms.atompub.AtomId

class OpenSearchResponseSpec extends Specification {
  "OpenSearchResponse" should {
    "have a useful .left" in {
      val entry = CmsEntry(AtomId("a"), None, "title", CmsSlug("slug"), NodeSeq.Empty, List.empty)
      val page = List(entry, entry, entry)

      OpenSearchResponse(page, 100, 0, 10).pageCount must_== 10
      OpenSearchResponse(page, 100, 0, 10).pageIndex must_== 1
      OpenSearchResponse(page, 100, 0, 10).prevPageIndex must beNone
      OpenSearchResponse(page, 100, 0, 10).prevStart must beNone
      OpenSearchResponse(page, 100, 0, 10).nextPageIndex must beSome(2)
      OpenSearchResponse(page, 100, 0, 10).nextStart must beSome(10)

      OpenSearchResponse(page, 17, 0, 2).pageCount must_== 8
      OpenSearchResponse(page, 17, 0, 2).pageIndex must_== 1
      OpenSearchResponse(page, 17, 0, 2).prevPageIndex must beNone
      OpenSearchResponse(page, 17, 0, 2).prevStart must beNone
      OpenSearchResponse(page, 17, 0, 2).nextPageIndex must beSome(2)
      OpenSearchResponse(page, 17, 0, 2).nextStart must beSome(2)

      OpenSearchResponse(page, 17, 1, 2).pageCount must_== 8
      OpenSearchResponse(page, 17, 1, 2).pageIndex must_== 1
      OpenSearchResponse(page, 17, 1, 2).prevPageIndex must beNone
      OpenSearchResponse(page, 17, 1, 2).prevStart must beNone
      OpenSearchResponse(page, 17, 1, 2).nextPageIndex must beSome(2)
      OpenSearchResponse(page, 17, 1, 2).nextStart must beSome(2)

      OpenSearchResponse(page, 17, 2, 2).pageCount must_== 8
      OpenSearchResponse(page, 17, 2, 2).pageIndex must_== 2
      OpenSearchResponse(page, 17, 2, 2).prevPageIndex must beSome(1)
      OpenSearchResponse(page, 17, 2, 2).prevStart must beSome(0)
      OpenSearchResponse(page, 17, 2, 2).nextPageIndex must beSome(3)
      OpenSearchResponse(page, 17, 2, 2).nextStart must beSome(4)

      OpenSearchResponse(page, 100, 10, 10).pageCount must_== 10
      OpenSearchResponse(page, 100, 10, 10).pageIndex must_== 2
      OpenSearchResponse(page, 100, 10, 10).prevPageIndex must beSome(1)
      OpenSearchResponse(page, 100, 10, 10).prevStart must beSome(0)
      OpenSearchResponse(page, 100, 10, 10).nextPageIndex must beSome(3)
      OpenSearchResponse(page, 100, 10, 10).nextStart must beSome(20)

      OpenSearchResponse(page, 100, 30, 10).pageCount must_== 10
      OpenSearchResponse(page, 100, 30, 10).pageIndex must_== 4

      OpenSearchResponse(page, 100, 0, 10).pageIndex must_== 1
      OpenSearchResponse(page, 100, 0, 10).prevPageIndex must beNone
      OpenSearchResponse(page, 100, 0, 10).nextPageIndex must beSome(2)

      OpenSearchResponse(page, 100, 95, 10).pageIndex must_== 10
      OpenSearchResponse(page, 100, 95, 10).prevPageIndex must beSome(9)
      OpenSearchResponse(page, 100, 95, 10).nextPageIndex must beNone

//      OpenSearchResponse(List.empty, 0, one, ten).left must_== 0
      OpenSearchResponse(List.empty, 0, 1, 10).pageIndex must_== 1
//      OpenSearchResponse(page, 10, one, three).left must_== 7
    }
  }
}

package org.felher.laminouter

import utest.*
import org.scalajs.dom

import com.raquo.laminar.api.L.*

object LaminouterSpec extends TestSuite:
  private val routes = summon[Routes[Route]]

  def tests = Tests {
    test("Non matching URL should decode to None") - assert(
      routes.decode(dom.URL("https://www.heise.de:8080/Non-Existant")) == None
    )

    test("URLs should encode and decode correctly") {
      for t <- routesToTest do
        val baseUrl = dom.URL("https://www.heise.de:8080")
        val url = dom.URL(t.url)
        assert(routes.encode(baseUrl, t.route).toString == t.url)
        assert(routes.decode(url).exists(compareRoutes(_, t.route)))
    }
  }

  private enum Route derives CanEqual:
    case Home
    case SingleParam(id: Int)
    case MultiParam(user: String, message: Int)
    case MultiParamQuery(user: String, message: Int)(val page: Int, val filter: String)
    case MultiParamQueryOptional(user: String, message: Int)(val page: Int, val filter: Option[String])

  private def compareRoutes(a: Route, b: Route): Boolean = (a, b) match
    case (Route.Home, Route.Home)                                                               => true
    case (Route.SingleParam(a), Route.SingleParam(b))                                           => a == b
    case (Route.MultiParam(a1, a2), Route.MultiParam(b1, b2))                                   => a1 == b1 && a2 == b2
    case (a @ Route.MultiParamQuery(a1, a2), b @ Route.MultiParamQuery(b1, b2))                 =>
      a1 == b1 && a2 == b2 && a.page == b.page && a.filter == b.filter
    case (a @ Route.MultiParamQueryOptional(a1, a2), b @ Route.MultiParamQueryOptional(b1, b2)) =>
      a1 == b1 && a2 == b2 && a.page == b.page && a.filter == b.filter
    case _                                                                                      => false

  private final case class Test(
    route: Route,
    url: String,
  )

  private final val routesToTest = List(
    Test(Route.Home, "https://www.heise.de:8080/home"),
    Test(Route.SingleParam(123), "https://www.heise.de:8080/singleParam/123"),
    Test(Route.MultiParam("user-name", 999), "https://www.heise.de:8080/multiParam/user-name/999"),
    Test(Route.MultiParamQuery("user-name", 999)(123, "red"), "https://www.heise.de:8080/multiParamQuery/user-name/999?page=123&filter=red"),
    Test(Route.MultiParamQueryOptional("user-name", 999)(123, Some("red")), "https://www.heise.de:8080/multiParamQueryOptional/user-name/999?page=123&filter=red"),
    Test(Route.MultiParamQueryOptional("user-name", 999)(123, None), "https://www.heise.de:8080/multiParamQueryOptional/user-name/999?page=123"),
  )

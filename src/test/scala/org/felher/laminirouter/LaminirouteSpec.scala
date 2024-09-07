package org.felher.laminiroute

import utest.*
import org.scalajs.dom

import com.raquo.laminar.api.L.*

object LaminirouteSpec extends TestSuite:
  val routes = summon[Routes[Route]]

  def tests = Tests {
    test("Non matching URL should decode to None") - assert(
      routes.decode(dom.URL("https://www.heise.de:8080/Non-Existant")) == None
    )

    test("Single object should decode correctly") - assert(
      routes.decode(dom.URL("https://www.heise.de:8080/Home")) == Some(Route.Home)
    )

    test("Class with one parameter should decode correctly") - assert(
      routes.decode(dom.URL("https://www.heise.de:8080/SingleParam/123")) == Some(Route.SingleParam(123))
    )

    test("Class with two parameters should decode correctly") - assert(
      routes.decode(dom.URL("https://www.heise.de:8080/MultiParam/user-name/999")) == Some(
        Route.MultiParam("user-name", 999)
      )
    )

    test("Class with path and search parameters should decode correctly") - {
      val decoded =
        routes.decode(dom.URL("https://www.heise.de:8080/MultiParamQuery/user-name/999?page=123&filter=red"))
      assert(decoded.nonEmpty)
      assert(decoded.get == Route.MultiParamQuery("user-name", 999)(0, ""))
      assert(decoded.get.asInstanceOf[Route.MultiParamQuery].page == 123)
      assert(decoded.get.asInstanceOf[Route.MultiParamQuery].filter == "red")
    }

    println(routes.encode(dom.URL("https://www.heise.de:8080/a/b/c?test"), Route.Home))
    println(routes.encode(dom.URL("https://www.heise.de:8080/a/b/c?test"), Route.MultiParam("user-name", 999)))
    println(routes.encode(dom.URL("https://www.heise.de:8080/a/b/c?test"), Route.MultiParamQuery("user-name", 999)(333, "red")))
  }

  enum Route derives CanEqual:
    case Home
    case SingleParam(id: Int)
    case MultiParam(user: String, message: Int)
    case MultiParamQuery(user: String, message: Int)(val page: Int, val filter: String)

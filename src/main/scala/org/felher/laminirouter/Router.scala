package org.felher.laminouter

import org.scalajs.dom

import com.raquo.laminar.api.L.*

/**
  * A router that parses the current URL, gives you a signal of the routes and let's navigate away.
  */
trait Router[A]:
  /** The current route. None if the URL does not match any route. */
  def route: Signal[Option[A]]

  /** The current route. Fallback if the URL does not match any route. */
  def routeWithFallback(fallback: A): Signal[A] = route.map(_.getOrElse(fallback))

  /**
    * A modifier to bind to an element to navigate to a route.
    *
    * For example:
    * {{{
    *  val router = Router[Route]
    *  a(router.target(Route.Home), "Home")
    *  button(router.target(Route.BlogPost(123)), "Blog post 123")
    * }}}
    */
  def target(a: A): Binder[HtmlElement]

  /**
    * Navigate immediately to a route.
    */
  def goTo(a: A): Unit

object Router:
  private def getCurrentURL(): dom.URL = dom.URL(dom.window.location.href)
  private val currentURL               = Var(getCurrentURL())

  windowEvents(_.onPopState).foreach(_ => currentURL.set(getCurrentURL()))(using unsafeWindowOwner)

  def apply[A](using routes: Routes[A]): Router[A] =
    new Router[A]:
      def route: Signal[Option[A]] = currentURL.signal.map(url => routes.decode(url))

      def target(a: A): Binder[HtmlElement] = Binder: el =>
        val isLinkElement = el.ref.isInstanceOf[dom.html.Anchor]

        if isLinkElement then el.amend(href(routes.encode(getCurrentURL(), a).toString))

        (onClick
          .filter(ev => !(isLinkElement && (ev.ctrlKey || ev.metaKey || ev.shiftKey || ev.altKey)))
          .preventDefault
          --> (_ =>
            val newURL = routes.encode(getCurrentURL(), a)
            dom.window.history.pushState(null, "", newURL.toString)
            currentURL.set(newURL)
          )).bind(el)

      def goTo(a: A): Unit =
        val current = Router.getCurrentURL()
        val newURL  = routes.encode(current, a)
        dom.window.history.pushState(null, "", newURL.toString)
        Router.currentURL.set(newURL)

package org.felher.laminiroute

import org.scalajs.dom

import com.raquo.laminar.api.L.*

trait Router[A]:
  def route: Signal[Option[A]]
  def routeWithFallback(fallback: A): Signal[A] = route.map(_.getOrElse(fallback))

  def target(a: A): Binder[HtmlElement]

object Router:
  private def getCurrentURL(): dom.URL = dom.URL(dom.window.location.href)
  private val currentURL                       = Var(getCurrentURL())

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

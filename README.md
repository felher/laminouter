# Laminouter

Laminouter is an ergonomic, minimalistic-to-a-fault router for Laminar on Scala 3.

## Table of Contents
- [Usage](#usage)
- [Who should use it](#who-should-use-it)
- [Notes](#notes)
  - [How get rid of the `asInstanceOf`?](#how-get-rid-of-the-asinstanceof)
  - [What's up with the multiple parameter lists?](#whats-up-with-the-multiple-parameter-lists)
- [Binary, Output And Laminar Compatibility](#binary-output-and-laminar-compatibility)
- [Contributions](#contributions)

## Usage

The usage is extremely simple.
1. Import the dependency
    ```
    libraryDependencies ++= Seq(
      "org.felher"  %%% "laminouter" % "0.17.0"
    )
    ```
2. Declare your routes:
    ```scala
    enum Route:
        case Home                                          // site.com/home
        case Category(category: String))                   // site.com/category/scala
        case BlogPost(post: Int)(val comment: Option[Int]) // site.com/blogpost/123 or
                                                           // site.com/blogpost/123?comment=456
    ```
    You might have noticed the `val` keyword and use of multiple parameter lists. Find out more 
    [here](#whats-up-with-the-multiple-parameter-lists).

3. Create a router:
    ```scala
    val router = Router[Route]
    ```

4. Define some renderers for each route (or don't if you want to render them inline)
    ```scala
    def renderHome(): HtmlElement = div("Home")

    def renderCategory(routeData: Signal[Route.Category]): HtmlElement =
      div("Category: ", child.text <-- routeData.map(_.category))

    def renderBlogPost(routeData: Signal[Route.BlogPost]): HtmlElement =
      div(
        p("Blog Post: ", child.text <-- routeData.map(_.post.toString)),
        p("Comment: ", child.text <-- routeData.map(_.comment.toString))
      )
    ```
4. Get a signal of the current route and do something with it:
    ```scala
    div(
        child <-- router
          .routeWithFallback(Route.Home)
          .splitOne(_.ordinal): (_, init, sig) =>
            init match
              case Route.Home        => renderHome()
              case _: Route.Category => renderCategory(sig.asInstanceOf)
              case _: Route.BlogPost => renderBlogPost(sig.asInstanceOf)
    )
    ```
    If you find the `asInstanceOf` ugly, take a look at the [notes](#how-get-rid-of-the-asinstanceof).

5. Create buttons or links using the router:
    ```scala
    a(router.target(Route.Home), "Home")
    button(router.target(Route.BlogPost(123)(Some(456))), "Blog Post 123 with comment 456")
    ```
    

## Who should use it
Laminouter is an extremely simple router. The most important design goal was to provide a router for small sites so you don't have to remember anything on how to use it. Declaration of routes should be as easy as declaring an enum (which it in fact is), the output should just be a signal and adding navigation to an element should only need a single modifier.

That said, to keep things simple, we had to make some serious concessions. If the following list describes you, Laminouter is made for you:

1. I only care about scala 3
2. I don't care about nesting routes
3. I don't really care how my routes are represented in the URL
4. I'm fine with History-API based routing and don't need fragment (`#`) based routing

Still here? Welcome to the I-Just-Dont-Care-Club then!

## Notes
### How get rid of the `asInstanceOf`?
Laminouter only gives you a signal of the current route. That's by design. At the time of this writing, Laminar doesn't have a way to destructure an enum in a typesafe way. But it will. As soon as [116](https://github.com/raquo/Airstream/pull/116) is merged, we will be able to do it just fine.

Until then, you can either copy the code from the PR, or copy good-enough solution from my gist [here](https://gist.github.com/felher/5515eb1124268b0e10eadc78778f49a8).

Or, of course, you can switch to, for example, [Waypoint](https://github.com/raquo/Waypoint), which does include a `SplitRender` abstraction which let's you do this, albeit without exhaustivity checking.

### What's up with the multiple parameter lists?

As you can see in the example above, Laminouter uses multiple parameter lists to separate path parameters from search/query parameter. This has a couple of drawbacks. It was only chosen because the most important aspect of this library is ergonomics.

The drawbacks are that enum cases (and not coincidentally case classes as well), only provide the full power of them for the first parameter list. You can't destructure the second parameter list and pattern matches and they won't be part of the cases `toString` or `equals` methods, meaning that `Route.BlogPost(123)(Some(456))` is "equal" to `Route.BlogPost(123)(None)`. This also means that you have declare query string parameters as `val` to be able to access them later on.

## Binary, Output And Laminar Compatibility

### Binary Compatibility
We follow semantic versioning, i.e. semver 2.0. Keeping binary (and to a somewhat lesser degree source) compatibility as long as possible is a high priority. The next major version of the library will probably come with a new major version of Laminar which breaks binary compatibility with us.

When we need to break binary compatibility for a reason other than to keep up with Laminar, we will consider changing the namespace of the library in the process.

### Laminar Compatibility
Laminouter doesn't list Laminar as normal dependency, but as "provided", so that you can use it with any version of Laminar you want. For example, Laminouter 0.17.0 works with Laminar 15, 16 and 17. Chances are that when Laminar updates to 18, this library will just work without any changes and will not need a new release.

We generate a compatibility matrix for all releases, which lists the Laminar versions as well as the Scala versions the library is binary compatible with. Here it is:

||Laminouter 0.17.0|
|-|-|
| Laminar 0.14.5 | scala 3.3 |
| Laminar 15.0.1 | scala 3.3 |
| Laminar 16.0.0 | scala 3.3 |
| Laminar 17.0.0 | scala 3.3 |
| Laminar 17.1.0 | scala 3.3 |

## Contributions

We welcome contributions. Create an issue if you need something or go straight to creating a PR!

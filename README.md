# Laminouter

Laminouter is an ergonomic, but minimalistic-to-a-fault router for Laminar.

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
        // https://example.com/home
        case Home

        // https://example.com/category/scala
        case Category(category: String))

        // https://example.com/blog/123 or
        // https://example.com/blog/123?comment=456
        case BlogPost(post: Int)(val comment: Option[Int])
    ```
    XXXXXXXXXXXXXXXXXXXXXXX Link to as multiple parameter lists here

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
    XXXXXXXXXXXXXXXXXXXXXXX Link to the as instance of note here

5. Create buttons or links using the router:
    ```scala
    a(router.target(Route.Home), "Home")
    button(router.target(Route.BlogPost(123)(Some(456))), "Blog Post 123 with comment 456")
    ```
    

## Notes
### How get rid of the `asInstanceOf`?
### What's up with the multiple parameter lists?

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

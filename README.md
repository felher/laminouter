# Beminar

Beminar is a very simple library to help you add BEM style CSS classes to your Laminar components. It mainly just keeps you from having to write code like this:

```scala
div(
  cls(disabled.signal.combineWith(buttonSize.signal).map((disabled, size) =>
    val disStr = if disabled then "user-list__delete-button--disabled" else ""
    s"user-list__delete-button $disStr user-list__delete-button--size-$size"
  ),
  "Delete"
)
```
and instead lets you write code like this:

```scala
// once, for each component
val bem = Bem("/user-list")
// then, whenever you need it
div(
  bem("/delete-button", "disabled" -> disabled, "size" -> buttonSize),
  "Delete"
)
```

## Installation

Just add
```
libraryDependencies += "org.felher" %%% "beminar" % "1.0.0"
```
to your `build.sbt` file.

## Scaladocs

[here](https://www.javadoc.io/doc/org.felher/beminar_sjs1_3/latest/index.html)

## Usage

### Small example

Create a `org.felher.beminar.Bem` block object somewhere, for example in the companion object of your component:

```scala
import org.felher.beminar.Bem

object UserList:
    val bem = Bem("/user-list")
```

Then, whenever you need to add BEM classes to an element, use the `bem` entity:

```scala
div(
    bem("empty" -> users.isEmpty),
    users.map(user =>
        div(
            bem("/user", "disabled" -> user.disabled),
            user.name
        )
    )
)
```

### Creating BEM entities

The heart of the library is the `apply` method of `Bem`, which allows you to add BEM fragments (blocks, modifiers, elements, ...) to the `Bem` entity.

#### Adding a block or element
You can just use `String`s to create blocks or elements, but you need to prefix them with "/". For example,
`Bem("/button", "/text")` would generate the `button__text` css class. If you forget the "/" you get a modifier instead.

####  Adding a boolean modifier
 You could just use a `String` again. `Bem("/button", "primary")` would yield "button button--primary" while `Bem("/button", "/text", "primary")` would yield "button__text button__text--primary"

If you want to turn the boolean modifier on or off, you can use a tuple syntax as well. I.e., you can write `Bem("/button", "primary" -> true)`. Of course, this isn't too useful, so Beminar also allows you to use a `Var` or `Signal` for the toggle,. i.e. `Bem("/button", "primary" -> isPrimarySignal)`. If you want to use multiple, you can also pass a `Map` like `Bem("/button", Map("primary" -> isPrimarySignal, "centered" -> isCenteredSignal))`

#### Adding a value modifier
You can also pass in tuple of `String`s to add value modifiers to your BEM entity: `Bem("/button", "size" -> "large")` would yield "button button--size-large". Of course, you can also use a `Var` or `Signal` or a `Map` or a combination of those.

### Cascading `apply`s
Beminar is designed to be used by calling `apply` multiple times to add more stuff to your entity. For example, you often declare the block entity in your component, so that you reuse it later. It is idiomatic to just name it "bem" since you need it often and it can be referenced from outside of your component by doing `YourComponent.bem`. 
```scala
val bem = Bem("
div(
  bem,
  div(bem("/text", "large"))
 )
```

### Output configuration
You can decide how you want your classes to be generated. For example, you can call `modConfig` on every `Bem`: `Bem("/button").modifyConfig(_.withElementSeparator("_"))` to change the separator for blocks and elements from "__" to "_".

You can also use the fact that `Bem` is designed for cascading uses of `apply` to do something like `val defaultBem = Bem().modifyConfig(... your config here)` and then use your `defaultBem` everywhere. For example `val bem = defaultBem("/button")` in a button component.

Take a look at the [scaladocs](https://www.javadoc.io/doc/org.felher/beminar_sjs1_3/latest/index.html) for `Bem` and `BemConfig` to find more options.

### More stuff
There is more stuff to find, like how BEM elements may inherit modifiers from their parent. Go ahead and browse the scaladocs. They are fairly comprehensive but still easy to wrap your head around.


## Larger Example

Here is an example of a counter, which you can increment, reset and freeze. The counter changes color when it reaches 10.


```scala
import com.raquo.laminar.api.L.*
import org.felher.beminar.Bem

object Counter:
  def render(isFrozen: Signal[Boolean]): HtmlElement =
    // this sets up the BEM entity, "disabled" is inherited by the children
    val bem   = Bem("/counter", "frozen" -> isFrozen)
    val count = Var(0)

    div(
      bem,
      div(
        bem("/count", "wow" -> count.signal.map(_ > 9)),
        child.text <-- count.signal.map(_.toString)
      ),
      button(
        disabled <-- isFrozen,
        bem("/increment"),
        "Increment",
        onClick --> (_ => count.update(_ + 1))
      ),
      button(
        disabled <-- isFrozen,
        bem("/reset"),
        "Reset",
        onClick --> (_ => count.set(0))
      )
    )
```

Here is the scss file to style the component. Scss and BEM work really well together.

```scss
.counter {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 1rem;

  &__count {
    grid-column: 1 / 3;
    text-align: center;

    &--wow {
      color: red;
    }
  }

  &__increment,
  &__reset {
    padding: 0.5rem 1rem;
    border: 1px solid black;
    border-radius: 5px;
    text-align: center;

    &--frozen {
      background-color: gray;
      color: white;
      cursor: not-allowed;
    }
  }
}
```

## Binary, Output And Laminar Compatibility

### Binary Compatibility
We follow semantic versioning, i.e. semver 2.0. Keeping binary (and to a somewhat lesser degree source) compatibility as long as possible is a high priority. The next major version of the library will probably come with a new major version of Laminar which breaks binary compatibility with us.

When we need to break binary compatibility for a reason other than to keep up with Laminar, we will consider changing the namespace of the library in the process.

### Laminar Compatibility
Beminar doesn't list Laminar as normal dependency, but as "provided", so that you can use it with any version of Laminar you want. For example, Beminar 1.0.0 works with Laminar 15, 16 and 17. Chances are that when Laminar updates to 18, this library will just work without any changes and will not need a new release.

We generate a compatibility matrix for all releases, which lists the Laminar versions as well as the Scala versions the library is binary compatible with. Here it is:

||Beminar 0.16.0|Beminar 1.0.0|
|-|-|-|
| Laminar 15.0.1 | scala 2.13, 3.3|scala 2.13, 3.3 |
| Laminar 16.0.0 | scala 2.13, 3.3|scala 2.13, 3.3 |
| Laminar 17.0.0 | scala 2.13, 3.3|scala 2.13, 3.3 |
| Laminar 17.1.0 | scala 2.13, 3.3|scala 2.13, 3.3 |

### Output Compatibility
We also take output compatibility very seriously. Unless something is clearly a bug, new versions of this library should not change what classes are generated for your components, otherwise your CSS will break.

## Contributions

We welcome contributions. Create an issue if you need something or go straight to creating a PR!

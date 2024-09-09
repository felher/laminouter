package org.felher.laminouter

/**
  * A codec tells Laminouter how to turn parts of an URL into your data structure and the other way round.
  */
trait Codec[A]:
  /**
    * Encode your data structure into a string.
    *
    * Laminouter takes care of URL/URI component encoding for you.
    */
  def encode(a: A): String

  /**
    * Decode a string into your data structure.
    *
    * If the string is not a valid representation of your data structure, return `None`.
    *
    * Laminouter takes care of URL/URI component decoding for you.
    */
  def decode(s: String): Option[A]

  /**
    * Create a new codec by reusing an old one and mapping your new type from and to it.
    *
    * You can throw in the `toBase` method, in which case the codec will return `None`.
    * This makes it easy to create codecs by using java style parser functions like
    * `java.util.UUID.fromString`
    */
  def bimap[B](fromBase: A => B)(toBase: B => A): Codec[B] =
    val that = this
    new Codec[B]:
      def encode(b: B): String         = that.encode(toBase(b))
      def decode(s: String): Option[B] =
        try that.decode(s).map(fromBase)
        catch case _: Exception => None

object Codec:
  given intCodec: Codec[Int] with
    def encode(a: Int): String         = a.toString
    def decode(s: String): Option[Int] = s.toIntOption

  given longCodec: Codec[Long] with
    def encode(a: Long): String         = a.toString
    def decode(s: String): Option[Long] = s.toLongOption

  given stringCodec: Codec[String] with
    def encode(a: String): String         = a
    def decode(s: String): Option[String] = Some(s)

  given doubleCodec: Codec[Double] with
    def encode(a: Double): String         = a.toString
    def decode(s: String): Option[Double] = s.toDoubleOption

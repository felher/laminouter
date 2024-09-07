package org.felher.laminiroute

trait Codec[A]:
  def encode(a: A): String
  def decode(s: String): Option[A]
  def isOptional: Boolean

object Codec:
  given Codec[Int] with
    def encode(a: Int): String         = a.toString
    def decode(s: String): Option[Int] = s.toIntOption
    def isOptional                     = false

  given Codec[String] with
    def encode(a: String): String         = a
    def decode(s: String): Option[String] = Some(s)
    def isOptional                        = false

  given Codec[Double] with
    def encode(a: Double): String         = a.toString
    def decode(s: String): Option[Double] = s.toDoubleOption
    def isOptional                        = false

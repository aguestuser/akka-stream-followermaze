package com.example.codec

import scala.util.parsing.combinator.Parsers

trait StringDecoder extends Parsers {
  type Elem = Char
  val DIGITS: Set[Char] = Set('0', '1', '2', '3', '4', '5', '6', '7', '8', '9')

  def pipe: Parser[Char] = '|'
  def anychar: Parser[Char] = acceptIf(_ => true)(_ => "rudy can't fail!")
  def int: Parser[Int] = rep1(digit) ^^ (_.mkString.toInt)
  def digit: Parser[Char] = acceptIf(DIGITS.contains)(x => s"$x is not a digit")
}

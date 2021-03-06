package org.kframework.kale.util

import io.circe._
import io.circe.syntax._
import io.circe.parser._
import org.kframework.kale._
import org.kframework.kale.standard.{SimpleFreeLabel2, StandardEnvironment}
import org.scalatest.FreeSpec

import scala.language.implicitConversions

class CodecTest extends FreeSpec {

  implicit val env = StandardEnvironment()

  val foo = SimpleFreeLabel2("foo")

  import env._

  val pattern = foo(INT(3), STRING("bar"))

  object TestAtt extends Att[Int] {
    override def toString = "test"

    override def update(term: Term, oldTerm: Option[Term]): Int = 0
  }

  val codec = new Codec(Set(
    AttCodec(TestAtt, Encoder.encodeInt, Decoder.decodeInt)
  ))

  import codec._

  "encode" in {
    val actual = pattern.asJson.noSpaces
    val expected = "{\"label\":\"foo\",\"att\":{},\"children\":[{\"label\":\"Int\",\"att\":{},\"data\":\"3\"},{\"label\":\"String\",\"att\":{},\"data\":\"bar\"}]}"
    assert(actual == expected)
  }

  "decode int" in {
    val actual = decode[Term]("{\"label\":\"Int\",\"att\":{},\"data\":\"3\"}")
    val expected = Right(INT(3))
    assert(actual === expected)
  }

  "decode string" in {
    val actual = decode[Term]("{\"label\":\"String\",\"att\":{},\"data\":\"bar\"}")
    val expected = Right(STRING("bar"))
    assert(actual === expected)
  }

  "round trip" in {
    val json = pattern.asJson.noSpaces
    val afterRoundTrip = decode[Term](json).right.get

    assert(pattern === afterRoundTrip)
  }

  private def assertEncodings(expectedTerm: Term, expectedJson: String) = {
    assert(expectedTerm.asJson.noSpaces === expectedJson)

    assert(decode[Term](expectedJson) === Right(expectedTerm))

    assert(decode[Term](expectedJson).right.get.attributes === expectedTerm.attributes)
  }

  "att encoding" in {
    val expectedTerm: Term = INT(3)
    expectedTerm.att(TestAtt)

    val expectedJson = "{\"label\":\"Int\",\"att\":{\"test\":0},\"data\":\"3\"}"

    assertEncodings(expectedTerm, expectedJson)
  }

  "mutable obj encoding" in {
    val x = new MutableObj(4)
    assert(x.asJson.noSpaces === "4")
    assert(decode[MutableObj[Int]]("4") === Right(new MutableObj(4)))
  }
}
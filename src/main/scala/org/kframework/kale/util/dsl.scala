package org.kframework.kale.util

import org.kframework.kale._
import org.kframework.kale.standard.StandardEnvironment

import scala.language.implicitConversions

class dsl(implicit val env: StandardEnvironment) {

  import env._

  implicit class RichStandardTerm(t: Term) {
    def :=(tt: Term): Term = env.And.filterOutNext(env.unify(t, tt))

    def :==(tt: Term): Term = env.unify(t, tt)

    def ==>(tt: Term): Term = Rewrite(t, tt)

    def =:=(tt: Term): Term = env.And.filterOutNext(env.unify(t, tt))
  }

  implicit def symbolWithApp(s: Symbol)(env: Environment) = new {
    val label = env.label(s.name)

    def apply[T](value: T): Term = label.asInstanceOf[LeafLabel[T]](value)

    def apply(_1: Term): Term = label.asInstanceOf[Label1](_1)

    def apply(_1: Term, _2: Term): Term = label.asInstanceOf[Label2](_1, _2)
  }

  val rw = Rewrite

  def __ = Variable.freshVariable()

  def A(implicit env: StandardEnvironment) = env.Variable("A")

  def B(implicit env: StandardEnvironment) = env.Variable("B")
}



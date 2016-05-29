package org.kframework.kale

import org.kframework.kale.transformer.Binary

import scala.collection.{Iterable, Set}

object Matcher {

  def apply(labels: Set[Label]): Binary.Application = {
    val variableXlabel = labels.map(Binary.Piece(Variable, _, Matcher.VarLeft))
    val freeLikeLabelXfreeLikeLabel = labels.collect({
      case l: FreeLabel0 => Binary.Piece(l, l, Matcher.FreeNode0FreeNode0)
      case l: FreeLabel1 => Binary.Piece(l, l, Matcher.FreeNode1FreeNode1)
      case l: FreeLabel2 => Binary.Piece(l, l, Matcher.FreeNode2FreeNode2)
      case l: FreeLabel3 => Binary.Piece(l, l, Matcher.FreeNode3FreeNode3)
      case l: FreeLabel4 => Binary.Piece(l, l, Matcher.FreeNode4FreeNode4)
      case l: ConstantLabel[_] => Binary.Piece(l, l, Matcher.Constants)
    })

    val assoc = labels.flatMap({
      case l: AssocLabel =>
        labels.collect({ case ll if !ll.isInstanceOf[Variable] => Binary.Piece(l, ll, Matcher.AssocTerm) })
      case _ => Set[Binary.Piece]()
    }).toSet

    val anywhereContextMatchers = labels.map(Binary.Piece(AnywhereContext, _, AnywhereContextMatcher))

    new Binary.Application(variableXlabel | freeLikeLabelXfreeLikeLabel | assoc | anywhereContextMatchers, labels.map(_.id).max + 1)
  }

  object FreeNode0FreeNode0 extends Binary.Function[Node0, Node0, Top.type] {
    def f(solver: Binary.State)(a: Node0, b: Node0) = Top
  }

  object FreeNode1FreeNode1 extends Binary.Function[Node1, Node1, Term] {
    def f(solver: Binary.State)(a: Node1, b: Node1) = solver(a._1, b._1)
  }

  object FreeNode2FreeNode2 extends Binary.Function[Node2, Node2, Term] {
    def f(solver: Binary.State)(a: Node2, b: Node2) = And(solver(a._1, b._1), solver(a._2, b._2))
  }

  object FreeNode3FreeNode3 extends Binary.Function[Node3, Node3, Term] {
    def f(solver: Binary.State)(a: Node3, b: Node3) = And(List(solver(a._1, b._1), solver(a._2, b._2), solver(a._3, b._3)))
  }

  object FreeNode4FreeNode4 extends Binary.Function[Node4, Node4, Term] {
    def f(solver: Binary.State)(a: Node4, b: Node4) = And(List(solver(a._1, b._1), solver(a._2, b._2), solver(a._3, b._3), solver(a._4, b._4)))
  }

  def matchContents(l: AssocLabel, ksLeft: Iterable[Term], ksRight: Iterable[Term])(implicit solver: Binary.State): Term = {
    val res = (ksLeft.toSeq, ksRight.toSeq) match {
      case (Seq(), Seq()) => Top
      case ((v: Variable) +: tailL, ksR) =>
        (0 to ksR.size)
          .map { index => (ksR.take(index), ksR.drop(index)) }
          .map { case (prefix, suffix) => And(Equality(v, l(prefix)), matchContents(l, tailL, suffix)) }
          .fold(Bottom)({ (a, b) => Or(a, b) })
      case (left, right) if left.nonEmpty && right.nonEmpty => And(solver(left.head, right.head), matchContents(l, left.tail, right.tail): Term)
      case other => Bottom
    }
    res
  }


  object AssocTerm extends Binary.Function[Assoc, Term, Term] {
    def f(solver: Binary.State)(a: Assoc, b: Term) = {
      val asList = a.label.asList _
      val l1 = asList(a)
      val l2 = asList(b)
      matchContents(a.label, l1, l2)(solver)
    }
  }

  object TermAssoc extends Binary.Function[Term, Assoc, Term] {
    def f(solver: Binary.State)(a: Term, b: Assoc) = {
      val asList = b.label.asList _
      val l1 = asList(a)
      val l2 = asList(b)
      matchContents(b.label, l1, l2)(solver)
    }
  }

  object VarLeft extends Binary.Function[Variable, Term, Term] {
    def f(solver: Binary.State)(a: Variable, b: Term) = Equality(a.asInstanceOf[Variable], b)
  }

  object Constants extends Binary.Function[Constant[_], Constant[_], Term] {
    override def f(solver: Binary.State)(a: Constant[_], b: Constant[_]) =
      Truth(a.value == b.value)
  }

}

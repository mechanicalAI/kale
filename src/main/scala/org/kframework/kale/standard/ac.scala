package org.kframework.kale.standard

import org.kframework.kale._
import org.kframework.kale.util.Named

import scala.collection.Iterable

class AssocWithIdListLabel(val name: String, val identity: Term)(implicit val env: Environment) extends AssocWithIdLabel {
  override def construct(l: Iterable[Term]): Term = AssocWithIdList(this, l)
}

case class AssocWithIdList(label: AssocWithIdLabel, assocIterable: Iterable[Term]) extends Assoc {
  assert(assocIterable.forall(_ != label.identity))
  assert(assocIterable.forall(_.label != label))

  override lazy val isPredicate: Boolean = false

  override def _1: Term = assocIterable.head

  override def _2: Term = label(assocIterable.tail)
}

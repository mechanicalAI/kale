package org.kframework

import org.kframework.kale.standard.SimpleRewrite
import org.kframework.kale.transformer.GenUnary.Apply
import org.kframework.kale.transformer.Unary.ProcessingFunction
import org.kframework.kale.transformer.{GenUnary, Unary}

package object kale {

  /**
    * For marking traits that are meant to be mixed into an Environment
    * By convention, postfix all extending traits with "Mixin"
    */
  trait Mixin

  def definePartialFunction[T, Solver <: Apply[T]](f: GenUnary.ProcessingFunctions[T, Solver]): GenUnary.ProcessingFunctions[T, Solver] = f

  implicit def PFtoTotal(f: PartialFunction[Term, Boolean]): (Term => Boolean) = x => f.lift(x).getOrElse(false)

  class ExplicitOr(private val t: Term) extends AnyVal {
    def map(f: Term => Term): Term = (t.label.env.Or map f) (t)
  }

  class ExplicitAnd(private val t: Term) extends AnyVal {
    def map(f: Term => Term): Term = (t.label.env.And map f) (t)
  }

  implicit class RichTerm(private val t: Term) extends AnyVal {
    def mapBU(f: Term => Term): Term = kale.mapBU(f)(t)

    def mapTD(f: Term => Term): Term = kale.mapTD(f)(t)

    def contains(subterm: Term): Boolean = kale.contains(subterm)(t)

    def findTD(f: Term => Boolean): Option[Term] = kale.findTD(f)(t)

    def findBU(f: Term => Boolean): Option[Term] = kale.findBU(f)(t)

    def find(f: Term => Boolean): Option[Term] = this.findTD(f)

    def exists(f: Term => Boolean): Boolean = find(f).isDefined

    // if (t == subterm) true else t.children.exists(_.contains(subterm))
    def containsInConstructor(subterm: Term): Boolean = kale.containsInConstructor(t, subterm)

    def rewrite(obj: Term): Term = t.label.env.rewrite(t, obj)

    def unify(obj: Term): Term = t.label.env.unify(t, obj)


    def asOr = new ExplicitOr(t)
    def asAnd = new ExplicitAnd(t)
  }

  implicit class StaticRichAssocLabel(label: AssocLabel) {
    def apply(args: Term*): Term = label.apply(args.toSeq)
  }

  object BUMapper {
    def apply(processingFunction: Label => ProcessingFunction[BUMapper], env: Environment)(func: PartialFunction[Term, Term]): BUMapper = new BUMapper(func)(env)

    def apply(env: Environment): PartialFunction[Term, Term] => BUMapper = {

      BUMapper(env)
    }
  }

  class BUMapper(val func: PartialFunction[Term, Term])(implicit env: Environment) extends Unary.Apply() {
    val processingFunctions = env.unaryProcessingFunctions

    val liftedF = func.lift

    override def apply(t: Term) =
      arr(t.label.id) match {
        case f =>
          val processedT = f(t)
          liftedF(processedT).getOrElse(processedT)
      }
  }

  case class mapBU(f: Term => Term) extends (Term => Term) {
    override def apply(t: Term): Term = f(t map0 this)
  }

  case class mapTD(f: Term => Term) extends (Term => Term) {
    override def apply(t: Term): Term = f(t) map0 this
  }

  case class map0(f: Term => Term) extends (Term => Term) {
    override def apply(t: Term): Term = t map0 f
  }


  /**
    * Collects all terms generated by f when it applies, in BU order
    * TODO: needs better testing
    */
  case class collectBU(f: PartialFunction[Term, Term]) extends (Term => Set[Term]) {
    override def apply(t: Term): Set[Term] = {
      val resForChildren = t.children.toSet flatMap this
      resForChildren ++ f.lift(t)
    }
  }

  /**
    * Collects all terms generated by f when it applies, in TD order
    * TODO: needs better testing
    */
  case class collectTD(f: PartialFunction[Term, Term]) extends (Term => Set[Term]) {
    override def apply(t: Term): Set[Term] = {
      val resForChildren = t.children.toSet flatMap this
      f.lift(t) ++: resForChildren
    }
  }

  val collect = collectTD

  case class findBU(f: Term => Boolean) extends (Term => Option[Term]) {
    override def apply(t: Term): Option[Term] = {
      val resForChildren = t.children.view map this find (_.isDefined)
      resForChildren.getOrElse(if (f(t)) Some(t) else None)
    }
  }

  case class findTD(f: Term => Boolean) extends (Term => Option[Term]) {
    override def apply(t: Term): Option[Term] = {
      if (f(t))
        Some(t)
      else {
        t.children.view map this collect {
          case Some(c) => c
        } headOption
      }
    }
  }

  def findAllTD(f: Term => Boolean): collectTD = collectTD({
    case t if f(t) => t
  })

  def findAllBU(f: Term => Boolean): collectBU = collectBU({
    case t if f(t) => t
  })

  val findAll = findAllTD _

  case class existsTD(f: Term => Boolean) extends (Term => Boolean) {
    override def apply(t: Term): Boolean = f(t) || (t.children.view exists this)
  }

  case class existsBU(f: Term => Boolean) extends (Term => Boolean) {
    override def apply(t: Term): Boolean = (t.children.view exists this) || f(t)
  }

  val exists = existsTD

  def contains(contained: Term) = exists(contained == _)

  val variables = findAll({ case v: Variable => true })

  def fixpoint[T](f: T => T): (T => T) = {
    { t: T =>
      val after = f(t)
      if (after != t)
        fixpoint(f)(after)
      else
        after
    }
  }

  def toRewriteLHS(t: Term): Term = t match {
    case SimpleRewrite(l, _) => l
    case n: Node => n.copy(n.children map toRewriteLHS toSeq)
    case _ => t
  }

  def toRewriteRHS(t: Term): Term = t match {
    case SimpleRewrite(_, r) => r
    case n: Node => n.copy(n.children map toRewriteRHS toSeq)
    case _ => t
  }

  def moveRewriteSymbolToTop(t: Term)(implicit env: Environment): SimpleRewrite = env.Rewrite(toRewriteLHS(t), toRewriteRHS((t))).asInstanceOf[SimpleRewrite]

  def containsInConstructor(t: Term, subterm: Term): Boolean = {
    if (t == subterm) true
    else if (!t.label.isInstanceOf[Constructor]) false
    else t.children.exists(containsInConstructor(_, subterm))
  }

  trait BinaryInfix {
    self: Node2 =>
    override def toString: String =  _1 + " " + label.name + " " + _2
  }

  trait MemoizedHashCode {
    lazy val cachedHashCode = computeHashCode

    override def hashCode = cachedHashCode

    def computeHashCode: Int
  }
}
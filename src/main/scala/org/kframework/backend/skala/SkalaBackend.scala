package org.kframework.backend.skala

import org.kframework.kale.standard._
import org.kframework.kale.{Environment, Term}
import org.kframework.kore
import org.kframework.kore.extended
import org.kframework.kore.extended.Backend
import org.kframework.kore.extended.implicits._
import org.kframework.kore.implementation.DefaultBuilders

import scala.collection.Seq

class SkalaBackend(implicit val env: Environment, val originalDefintion: kore.Definition) extends KoreBuilders with extended.Backend {
  override def att: kore.Attributes = ???

  override def modules: Seq[kore.Module] = ???
}


//Todo: Move somewhere else
object Encodings {
  val iMainModule = DefaultBuilders.Symbol("#MainModule")
  val iNone = DefaultBuilders.Symbol("#None")
  val assoc = DefaultBuilders.Symbol("assoc")
  val bag = DefaultBuilders.Symbol("bag")
  val relativeHook = DefaultBuilders.Symbol("relativeHook")
  val hook = DefaultBuilders.Symbol("hook")
  val function = DefaultBuilders.Symbol("function")
  val unit = DefaultBuilders.Symbol("unit")
  val index = DefaultBuilders.Symbol("index")
  val comm = DefaultBuilders.Symbol("comm")
  val macroEnc = DefaultBuilders.Symbol("macro")
  val rewrite = DefaultBuilders.Symbol("#KRewrite")
}


object DefinitionToEnvironment extends (kore.Definition => Environment) {

  import Encodings._

  import org.kframework.kore.implementation.{DefaultBuilders => db}

  def apply(d: kore.Definition): Environment = {
    val mainModuleName: kore.ModuleName = {
      d.att.findSymbol(iMainModule) match {
        case Some(kore.Application(_, Seq(kore.DomainValue(kore.Symbol("S"), kore.Value(name)))))
        => DefaultBuilders.ModuleName(name)
        case None => ??? // throw exception
      }
    }

    val mainModule: kore.Module = d.modulesMap(mainModuleName)
    apply(d, mainModule)
  }

  private def isAssoc(s: kore.SymbolDeclaration): Boolean = {
    s.att.findSymbol(Encodings.assoc) match {
      case Some(_) => true
      case None => s.att.findSymbol(Encodings.bag) match {
        case Some(_) => true
        case None => false
      }
    }
  }


  def apply(d: kore.Definition, m: kore.Module): Environment = {

    implicit val iDef = d

    val uniqueSymbolDecs: Seq[kore.SymbolDeclaration] = m.allSentences.collect({
      case sd@kore.SymbolDeclaration(_, s, _, _) if s != iNone => sd
    }).groupBy(_.symbol).mapValues(_.head).values.toSeq


    val sortDeclarations: Seq[kore.SortDeclaration] = m.sentences.collect({
      case s@kore.SortDeclaration(_, _) => s
    })


    val assocSymbols: Seq[kore.SymbolDeclaration] = uniqueSymbolDecs.filter(isAssoc)

    val nonAssocSymbols: Seq[kore.SymbolDeclaration] = uniqueSymbolDecs.diff(assocSymbols)

    implicit val env = StandardEnvironment()

    //dealing with non-assoc labels

    nonAssocSymbols.foreach(x => {
      x.att.getSymbolValue(Encodings.relativeHook) match {
        // Todo: Has Relative Hook
        case Some(_) => None
        // No Relative Hook
        case None => x.att.getSymbolValue(Encodings.hook) match {
          // Has Some Non Relative Hook
          case Some(v) => {
            None
            //Todo: No Hooking Mechanism present?
          }
          case None => {
            x.att.findSymbol(Encodings.function) match {
              case Some(_) => {
                if (x.symbol.str.startsWith("is")) {
                  //Todo: Issue with Sorting?
                  None
                }

                //Functional Symbol Declaration
                x.args match {
                  case Seq() => FunctionDefinedByRewritingLabel0(x.symbol.str)(env)
                  case Seq(_) => FunctionDefinedByRewritingLabel1(x.symbol.str)(env)
                  case Seq(_, _) => FunctionDefinedByRewritingLabel2(x.symbol.str)(env)
                  case Seq(_, _, _) => FunctionDefinedByRewritingLabel3(x.symbol.str)(env)
                  case Seq(_, _, _, _) => FunctionDefinedByRewritingLabel4(x.symbol.str)(env)
                }
              }
              //
              case None => {
                // Non Functional Symbol Declaration
                x.args match {
                  case Seq() => SimpleFreeLabel0(x.symbol.str)
                  case Seq(_) => SimpleFreeLabel1(x.symbol.str)
                  case Seq(_, _) => SimpleFreeLabel2(x.symbol.str)
                  case Seq(_, _, _) => SimpleFreeLabel3(x.symbol.str)
                  case Seq(_, _, _, _) => SimpleFreeLabel4(x.symbol.str)
                }
              }
            }
          }
        }
      }
    })

    //Todo: Dealing with Assoc Labels
    //dealing with assoc labels
    assocSymbols.foreach(x => {
      val unitLabel: Option[kore.Pattern] = x.att.findSymbol(Encodings.unit)
      unitLabel match {
        case Some(kore.Application(kore.Symbol(label), _)) => {
          env.uniqueLabels.getOrElse(x.symbol.str, {
            val index = x.att.findSymbol(Encodings.index)
            if (index.isDefined && x.att.findSymbol(Encodings.comm).isEmpty) {
              //              MapLabel(label, indexFunction, unitLabel())(env)
              ???
            } else {
              //              new AssocWithIdListLabel(label, unitLabel())(env)
              ???
            }

          })
        }
        //No unit Label for Assoc Symbol
        case None => ???
      }

    })
    //TODO: rules with function attributes

    val rules: Set[Rewrite] = m.rules.map({
      case kore.Rule(kore.Implies(requires, kore.And(kore.Rewrite(left, right), kore.Next(ensures))), att)
        if att.findSymbol(Encodings.macroEnc).isEmpty => {
        StandardConverter(db.Rewrite(db.And(left, requires), right)).asInstanceOf[Rewrite]
      }
      case _ => throw ConversionException("Encountered Non Uniform Rule")
    }).toSet

    env.seal()
    
    env
  }
}

object SkalaBackend extends extended.BackendCreator {
  override def apply(d: kore.Definition): Backend = new SkalaBackend()(DefinitionToEnvironment(d), d)

  // Todo: Use for Development, Replace with apply above
  def apply(d: kore.Definition, m: kore.Module): Backend = new SkalaBackend()(DefinitionToEnvironment(d, m), d)

}


//class ScalaConverters(m: kore.Module)(implicit env: Environment) {
//  //Some Converters need to be ported here
//
//
//

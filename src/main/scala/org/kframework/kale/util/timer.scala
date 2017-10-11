package org.kframework.kale.util

import cats.Monoid
import org.kframework.kale.util.timer.formatTime

import scala.concurrent.duration._

//class functionTimer[A, B, M: Monoid](val name: String, measure: A => M)(val f: A => B)
//  extends (A => B) with Timer {
//
//  timer.register(this)
//
//  val monoid = implicitly[Monoid[M]]
//
//  var _measured: M = monoid.empty
//
//  override def reset(): Unit = {
//    _measured = monoid.empty
//    super.reset()
//  }
//
//  final def apply(a: A): B = {
//    _measured = monoid.combine(_measured, measure(a))
//    enter()
//    val res = try {
//      f(a)
//    } catch {
//      case e: Throwable => _errorHits += 1; throw e;
//    } finally {
//      exit()
//    }
//    res
//  }
//
//  override def hashCode(): Int = f.hashCode()
//
//  override def equals(obj: scala.Any): Boolean = obj match {
//    case that: functionTimer[_, _, _] => this.name == that.name
//    case _ => false
//  }
//}

object timer {

  private val rs = collection.mutable.Map[String, Timer]()

  def register(t: Timer): Unit = {
    assert(!rs.keySet.contains(t.name))
    rs.put(t.name, t)
  }

  def apply(name: String): Timer = rs.getOrElseUpdate(name, Timer(name))

  def timers: Map[String, Timer] = rs.toMap

  def get(name: String): Option[Timer] = rs.get(name)

  def formatTime(l: Long): String = {
    l.nanos.toMillis + "ms"
  }

  def fullReport(): String = {
    rs.values map (_.report) mkString "\n\n"
  }

  def fullReset(): Unit = {
    rs.values.foreach(_.reset())
  }

  case class Timer(name: String) {

    timer.register(this)

    protected[this] var _entries = 0
    protected[this] var _totalTime = 0L
    protected[this] var _lastEntry = 0L
    protected[this] var _hits = 0L
    protected[this] var _errorHits = 0L
    protected[this] var _invocations = 0L

    def reset() = {
      assert(_entries == 0, "Do not reset the timer during measuring.")
      _entries = 0
      _totalTime = 0L
      _lastEntry = 0L
      _hits = 0L
      _errorHits = 0L
      _invocations = 0L
    }

    @inline final def time[T](f: => T): T = {
      enter()
      val res = try {
        f
      } catch {
        case e: Throwable => _errorHits += 1; throw e;
      } finally {
        exit()
      }
      res
    }

    @inline final def timeWithM[T](f: => T): T = {
      enter()
      val res = try {
        f
      } catch {
        case e: Throwable => _errorHits += 1; throw e;
      } finally {
        exit()
      }
      res
    }

    @inline protected[this] final def enter(): Unit = {
      if (_entries == 0) {
        _lastEntry = System.nanoTime()
        _invocations += 1
      }
      _entries += 1
      _hits += 1
    }

    @inline protected[this] final def exit(): Unit = {
      _entries -= 1
      if (_entries == 0) {
        _totalTime += (System.nanoTime() - _lastEntry)
      }
    }

    def totalTime: Long = _totalTime

    def hits: Long = _hits

    def errorHits: Long = _errorHits

    /**
      * hits per second
      */
    def speed: Double = {
      if (totalTime > 0)
        (hits.toDouble / totalTime.toDouble) * Math.pow(10, 9)
      else
        Double.NaN
    }

    def report: String = {
      if (_entries != 0) {
        System.err.println("Trying to print a report while inside a measured region")
      }
      name + ": time = " + formatTime(totalTime) + ";  hits: " + _hits +
        (if (hits > 0) f"; speed: $speed%.2f hits/s" else "") +
        (if (_errorHits > 0) "; errorHits: " + _errorHits else "")
    }
  }

}

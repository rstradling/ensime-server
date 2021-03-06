// Copyright: 2010 - 2016 https://github.com/ensime/ensime-server/graphs
// License: http://www.gnu.org/licenses/lgpl-3.0.en.html
package org.ensime.sexp

import annotation.implicitNotFound

/** Provides the S-Exp deserialization for type T. */
@implicitNotFound(msg = "Cannot find SexpReader or SexpFormat for ${T}")
trait SexpReader[T] {
  def read(value: Sexp): T
}

object SexpReader {
  implicit def func2Reader[T](f: Sexp => T): SexpReader[T] = new SexpReader[T] {
    def read(sexp: Sexp) = f(sexp)
  }
}

/** Provides the S-Exp serialization for type T. */
@implicitNotFound(msg = "Cannot find SexpWriter or SexpFormat for ${T}")
trait SexpWriter[T] {
  def write(obj: T): Sexp
}

object SexpWriter {
  implicit def func2Writer[T](f: T => Sexp): SexpWriter[T] = new SexpWriter[T] {
    def write(obj: T) = f(obj)
  }
}

/** Provides the S-Exp deserialization and serialization for type T. */
@implicitNotFound(msg = "Cannot find SexpFormat for ${T}")
trait SexpFormat[T] extends SexpReader[T] with SexpWriter[T]

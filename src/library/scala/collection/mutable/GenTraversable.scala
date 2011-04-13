/*                     __                                               *\
**     ________ ___   / /  ___     Scala API                            **
**    / __/ __// _ | / /  / _ |    (c) 2003-2011, LAMP/EPFL             **
**  __\ \/ /__/ __ |/ /__/ __ |    http://scala-lang.org/               **
** /____/\___/_/ |_/____/_/ | |                                         **
**                          |/                                          **
\*                                                                      */



package scala.collection
package mutable


import generic._


/** A trait for traversable collections that can be mutated.
 *
 *  $possiblyparinfo
 * 
 *  $traversableInfo
 *  @define mutability mutable
 */
trait GenTraversable[A] extends scala.collection.GenTraversable[A] 
                        with scala.collection.GenTraversableLike[A, GenTraversable[A]]
                        with GenericTraversableTemplate[A, GenTraversable]
                        with Mutable
{
  def seq: Traversable[A]
  override def companion: GenericCompanion[GenTraversable] = GenTraversable
}

object GenTraversable extends TraversableFactory[GenTraversable] {
  implicit def canBuildFrom[A] = new GenericCanBuildFrom[A]
  def newBuilder[A] = Traversable.newBuilder
}


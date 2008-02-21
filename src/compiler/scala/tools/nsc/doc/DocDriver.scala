/* NSC -- new Scala compiler
 * Copyright 2007-2008 LAMP/EPFL
 * @author  Sean McDirmid
 */
// $Id$

package scala.tools.nsc.doc

/**
 *  This is an abstract class for documentation plugins.
 *
 *  @author Geoffrey Washburn
 */
abstract class DocDriver {
 val global: Global
 import global._

 def process(settings: Settings, units: Iterator[CompilationUnit]): Unit
}

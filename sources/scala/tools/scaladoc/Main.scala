/*     ____ ____  ____ ____  ______                                     *\
**    / __// __ \/ __// __ \/ ____/    SOcos COmpiles Scala             **
**  __\_ \/ /_/ / /__/ /_/ /\_ \       (c) 2002, LAMP/EPFL              **
** /_____/\____/\___/\____/____/                                        **
\*                                                                      */

// $Id$

import scala.tools.scalac.Global$class;
import scalac.util.Reporter;

package scala.tools.scaladoc {

/**
 * The main class for scaladoc, an HTML documentation generator
 * for the programming language Scala.
 *
 * @author     Vincent Cremet, Stephane Micheloud
 * @version    1.0
 */
object Main {

  val PRODUCT: String =
    System.getProperty("scala.product", "scaladoc");
  val VERSION: String =
    System.getProperty("scala.version", "1.0");

  def main(args: Array[String]): Unit = {
    val reporter = new Reporter();
    val command = new HTMLGeneratorCommand(
      PRODUCT, VERSION, reporter, new HTMLGeneratorPhases());
    if (command.parse(args) && command.files.list.size() > 0) {
      val global = new Global$class(command);
      global.compile(command.files.toArray(), false);
      global.stop("total");
      global.reporter.printSummary();
    }
    // System.exit(if (reporter.errors() > 0) 1 else 0);
  }

}
}

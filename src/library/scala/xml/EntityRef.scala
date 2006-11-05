/*                     __                                               *\
**     ________ ___   / /  ___     Scala API                            **
**    / __/ __// _ | / /  / _ |    (c) 2003-2006, LAMP/EPFL             **
**  __\ \/ /__/ __ |/ /__/ __ |                                         **
** /____/\___/_/ |_/____/_/ | |                                         **
**                          |/                                          **
\*                                                                      */

// $Id$


package scala.xml

import compat.StringBuilder

/** an XML node for entity references
 *
 * @author  Burak Emir
 * @version 1.0
 * @param   text the text contained in this node
 */
case class EntityRef(entityName: String) extends SpecialNode {

  final override def typeTag$: Int = -5

  /** structural equality */
  override def equals(x: Any): Boolean = x match {
    case EntityRef(x) => x.equals(entityName)
    case _ => false
  }

  /** the constant "#ENTITY"
  */
  def label = "#ENTITY"

  override def hashCode() = entityName.hashCode()

  override def text = entityName match {
   case "lt"   => "<"
   case "gt"   => ">"
   case "amp"  => "&"
   case "apos" => "'"
   case "quot" => "\""
   case _ => val sb = new StringBuilder(); toString(sb).toString() 
  }

  /** appends "&amp; entityName;" to this stringbuffer */
  override def toString(sb: StringBuilder) = 
    sb.append("&").append(entityName).append(";")

}

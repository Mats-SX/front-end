/*
 * Copyright © 2002-2018 Neo4j Sweden AB (http://neo4j.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.opencypher.v9_0.frontend.phases

import org.opencypher.v9_0.ast.Statement
import org.opencypher.v9_0.expressions.{FunctionInvocation, FunctionName, RelationshipPattern}
import org.opencypher.v9_0.frontend.phases.CompilationPhaseTracer.CompilationPhase.DEPRECATION_WARNINGS
import org.opencypher.v9_0.rewriting.rewriters.replaceAliasedFunctionInvocations.deprecatedFunctionReplacements
import org.opencypher.v9_0.util.{DeprecatedFunctionNotification, DeprecatedRelTypeSeparatorNotification, DeprecatedVarLengthBindingNotification, InternalNotification}

object SyntaxDeprecationWarnings extends VisitorPhase[BaseContext, BaseState] {
  override def visit(state: BaseState, context: BaseContext): Unit = {
    val warnings = findDeprecations(state.statement())

    warnings.foreach(context.notificationLogger.log)
  }

  private def findDeprecations(statement: Statement): Set[InternalNotification] =
    statement.treeFold(Set.empty[InternalNotification]) {
      case f@FunctionInvocation(_, FunctionName(name), _, _) if deprecatedFunctionReplacements.contains(name) =>
        seq => (seq + DeprecatedFunctionNotification(f.position, name, deprecatedFunctionReplacements(name)), None)
      case p@RelationshipPattern(Some(variable), _, Some(_), _, _, _, _) =>
        seq => (seq + DeprecatedVarLengthBindingNotification(p.position, variable.name), None)
      case p@RelationshipPattern(variable, _, length, properties, _, true, _) if variable.isDefined || length.isDefined || properties.isDefined =>
        seq => (seq + DeprecatedRelTypeSeparatorNotification(p.position), None)
    }

  override def phase = DEPRECATION_WARNINGS

  override def description = "find deprecated Cypher constructs and generate warnings for them"
}

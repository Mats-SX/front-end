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
package org.opencypher.v9_1.frontend.phases

import org.opencypher.v9_1.ast.UnaliasedReturnItem
import org.opencypher.v9_1.ast.Statement
import org.opencypher.v9_1.ast.semantics.SemanticState
import org.opencypher.v9_1.expressions.NotEquals
import org.opencypher.v9_1.rewriting.RewriterStep._
import org.opencypher.v9_1.rewriting.rewriters._
import org.opencypher.v9_1.rewriting.{RewriterCondition, RewriterStepSequencer}
import org.opencypher.v9_1.rewriting.conditions._
import org.opencypher.v9_1.rewriting.rewriters.replaceLiteralDynamicPropertyLookups

class ASTRewriter(rewriterSequencer: (String) => RewriterStepSequencer,
                  literalExtraction: LiteralExtraction,
                  getDegreeRewriting: Boolean) {

  def rewrite(queryText: String, statement: Statement, semanticState: SemanticState): (Statement, Map[String, Any], Set[RewriterCondition]) = {

    val contract = rewriterSequencer("ASTRewriter")(
      recordScopes(semanticState),
      desugarMapProjection(semanticState),
      normalizeComparisons,
      enableCondition(noReferenceEqualityAmongVariables),
      enableCondition(containsNoNodesOfType[UnaliasedReturnItem]),
      enableCondition(orderByOnlyOnVariables),
      enableCondition(noDuplicatesInReturnItems),
      expandStar(semanticState),
      enableCondition(containsNoReturnAll),
      foldConstants,
      nameMatchPatternElements,
      nameUpdatingClauses,
      enableCondition(noUnnamedPatternElementsInMatch),
      normalizeMatchPredicates(getDegreeRewriting),
      normalizeNotEquals,
      enableCondition(containsNoNodesOfType[NotEquals]),
      normalizeArgumentOrder,
      normalizeSargablePredicates,
      enableCondition(normalizedEqualsArguments),
      addUniquenessPredicates,
      isolateAggregation,
      enableCondition(aggregationsAreIsolated),
      replaceLiteralDynamicPropertyLookups,
      namePatternComprehensionPatternElements,
      enableCondition(noUnnamedPatternElementsInPatternComprehension),
      inlineNamedPathsInPatternComprehensions
    )

    val rewrittenStatement = statement.endoRewrite(contract.rewriter)
    val (extractParameters, extractedParameters) = literalReplacement(rewrittenStatement, literalExtraction)

    (rewrittenStatement.endoRewrite(extractParameters), extractedParameters, contract.postConditions)
  }
}
package org.linqs.psl.application.learning.structure.rulegen;

import org.linqs.psl.model.predicate.StandardPredicate;
import org.linqs.psl.model.rule.Rule;

import java.util.List;

public interface DRLRuleGenerator {
        Rule generateRule(int maxRuleLen);
        Rule generateRule(StandardPredicate headPredicate, List<StandardPredicate> bodyPredicates,
                          List<Boolean> isNegated);
        boolean isValid(StandardPredicate targetPredicate, List<StandardPredicate> rulePredicates,
                        StandardPredicate action);
}

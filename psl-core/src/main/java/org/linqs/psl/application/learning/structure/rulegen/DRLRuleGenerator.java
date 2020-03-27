package org.linqs.psl.application.learning.structure.rulegen;

import org.linqs.psl.model.predicate.StandardPredicate;
import org.linqs.psl.model.rule.Rule;

import java.util.ArrayList;

public interface DRLRuleGenerator {
        Rule generateRule(int maxRuleLen);
        boolean isValid(StandardPredicate targetPredicate, ArrayList<StandardPredicate> rulePredicates, StandardPredicate action);
}

package org.linqs.psl.application.learning.structure.rulegen;

import org.linqs.psl.model.rule.Rule;

/**
 * Created by sriramsrinivasan on 4/10/20.
 */
public interface RandomRuleGenerator {
    Rule generateRule(int maxRuleLen);
}

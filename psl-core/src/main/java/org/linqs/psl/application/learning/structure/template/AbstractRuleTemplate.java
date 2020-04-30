package org.linqs.psl.application.learning.structure.template;

import org.linqs.psl.config.Config;
import org.linqs.psl.model.predicate.StandardPredicate;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Created by sriramsrinivasan on 4/17/20.
 */
public abstract class AbstractRuleTemplate implements RuleTemplate {
    private static final String CONFIG_PREFIX = "ruletemplate";
    private static final String DONOT_NEGATE_KEY = CONFIG_PREFIX + ".nonegate";
    private static final boolean DONOT_NEGATE_DEFAULT = false;

    protected Set<StandardPredicate> predicates;
    protected Set<StandardPredicate> openPredicates;
    protected Set<StandardPredicate> closedPredicates;
    protected Map<StandardPredicate, StandardPredicate> open2BlockPred;
    protected final boolean donotNegate;

    public AbstractRuleTemplate(Map<StandardPredicate, StandardPredicate> open2BlockPred,
                                Set<StandardPredicate> closedPredicates,
                                Set<StandardPredicate> openPredicates) {
        this.open2BlockPred = open2BlockPred;
        this.closedPredicates = new HashSet<>(closedPredicates);
        this.openPredicates = new HashSet<>(openPredicates);

        this.predicates = new HashSet<>();
        this.predicates.addAll(closedPredicates);
        this.predicates.addAll(openPredicates);
        this.donotNegate = Config.getBoolean(DONOT_NEGATE_KEY, DONOT_NEGATE_DEFAULT);
    }

    protected void resetIsNegated(List<Boolean> isNegated){
        int size = isNegated.size();
        isNegated.clear();
        for (int i = 0; i < size; i++) {
            isNegated.add(false);
        }
    }
}

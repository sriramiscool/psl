package org.linqs.psl.application.learning.structure.template;

import org.linqs.psl.model.predicate.StandardPredicate;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Created by sriramsrinivasan on 4/17/20.
 */
public abstract class AbstractRuleTemplate implements RuleTemplate {
    protected Set<StandardPredicate> predicates;
    protected Set<StandardPredicate> openPredicates;
    protected Set<StandardPredicate> closedPredicates;
    protected Map<StandardPredicate, StandardPredicate> open2BlockPred;

    public AbstractRuleTemplate(Map<StandardPredicate, StandardPredicate> open2BlockPred,
                                Set<StandardPredicate> closedPredicates,
                                Set<StandardPredicate> openPredicates) {
        this.open2BlockPred = open2BlockPred;
        this.closedPredicates = new HashSet<>(closedPredicates);
        this.openPredicates = new HashSet<>(openPredicates);

        this.predicates = new HashSet<>();
        this.predicates.addAll(closedPredicates);
        this.predicates.addAll(openPredicates);
    }
}

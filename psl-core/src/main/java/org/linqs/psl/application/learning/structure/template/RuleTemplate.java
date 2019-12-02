package org.linqs.psl.application.learning.structure.template;

import org.linqs.psl.model.predicate.Predicate;
import org.linqs.psl.model.rule.Rule;

import java.util.List;
import java.util.Set;

/**
 * Created by sriramsrinivasan on 12/1/19.
 */
public interface RuleTemplate {
    public Set<Predicate> getValidPredicates();
    public boolean isValid(List<Predicate> predicates, List<Boolean> isNegated);
    public Rule getRule(List<Predicate> predicates, List<Boolean> isNegated, boolean isSquared, double weight);
}

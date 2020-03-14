package org.linqs.psl.application.learning.structure.template;

import org.linqs.psl.model.atom.QueryAtom;
import org.linqs.psl.model.formula.Conjunction;
import org.linqs.psl.model.formula.Formula;
import org.linqs.psl.model.formula.Implication;
import org.linqs.psl.model.formula.Negation;
import org.linqs.psl.model.predicate.Predicate;
import org.linqs.psl.model.predicate.StandardPredicate;
import org.linqs.psl.model.rule.Rule;
import org.linqs.psl.model.rule.logical.WeightedLogicalRule;
import org.linqs.psl.model.term.Variable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Created by sriramsrinivasan on 12/1/19.
 */
public class SimRuleTemplate implements RuleTemplate {
    private static final Logger log = LoggerFactory.getLogger(SimRuleTemplate.class);

    protected final Set<StandardPredicate> predicates;
    protected final Set<StandardPredicate> openPredicates;
    protected final Set<StandardPredicate> closedPredicates;
    protected final Variable v1;
    protected final Variable v2;
    protected final Variable v3;


    private static boolean checkRequirement(List<StandardPredicate> predicates) {
        for (int i = 0; i < predicates.size() ; i++) {
            if (invalidPredicate(predicates.get(i))){
                return false;
            }
        }
        return true;
    }

    private static boolean invalidPredicate(Predicate p) {
        return p.getArity() != 2;
    }

    public SimRuleTemplate(Set<StandardPredicate> closedPredicates, Set<StandardPredicate> openPredicates) {
        Set<StandardPredicate> predicates = new HashSet<>();
        Set<StandardPredicate> openPreds = new HashSet<>();
        Set<StandardPredicate> closedPreds = new HashSet<>();
        for (StandardPredicate p: closedPredicates){
            if (!invalidPredicate(p)) {
                predicates.add(p);
                closedPreds.add(p);
            }
        }

        for (StandardPredicate p: openPredicates){
            if (!invalidPredicate(p)) {
                predicates.add(p);
                openPreds.add(p);
            }
        }
        this.predicates = Collections.unmodifiableSet(predicates);
        this.openPredicates = Collections.unmodifiableSet(openPreds);
        this.closedPredicates = Collections.unmodifiableSet(closedPreds);

        v1 = new Variable("A");
        v2 = new Variable("B");
        v3 = new Variable("C");
    }

    public Set<StandardPredicate> getValidPredicates() {
        return predicates;
    }

    @Override
    public boolean isValid(List<StandardPredicate> predicates, List<Boolean> isNegated) {
        if (predicates.size() != 3 ||
                predicates.get(2).getName() != predicates.get(0).getName() ||
                !checkRequirement(predicates)){
            return false;
        }
        return true;
    }

    public Rule getRule(List<StandardPredicate> predicates, List<Boolean> isNegated, boolean isSquared, double weight){
        if (!isValid(predicates, isNegated)){
            throw new IllegalArgumentException("Sim template needs three predicates with first and " +
                    "last the same and each with arity = 2.");
        }
        Formula q1 = new QueryAtom(predicates.get(0), v1, v2);
        //q1 = isNegated.get(0) ? new Negation(q1):q1;
        Formula q2 = new QueryAtom(predicates.get(1), v1, v3);
        //q2 = isNegated.get(1) ? new Negation(q2):q2;
        Formula q3 = new QueryAtom(predicates.get(2), v3, v2);
        q3 = isNegated.get(2) ? new Negation(q3):q3;
        Formula and = new Conjunction(q1, q2);
        Formula implies = new Implication(and, q3);
        log.trace("Rule generated: " + implies.toString());
        return new WeightedLogicalRule(implies, weight, isSquared);
    }
}

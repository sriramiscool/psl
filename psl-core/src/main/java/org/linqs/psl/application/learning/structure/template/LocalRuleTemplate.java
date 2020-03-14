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
public class LocalRuleTemplate implements RuleTemplate {
    private static final Logger log = LoggerFactory.getLogger(LocalRuleTemplate.class);

    protected final Set<StandardPredicate> predicates;
    protected final Set<StandardPredicate> openPredicates;
    protected final Set<StandardPredicate> closedPredicates;

    public LocalRuleTemplate(Set<StandardPredicate> closedPredicates, Set<StandardPredicate> openPredicates) {
        Set<StandardPredicate> predicates = new HashSet<>();
        for (StandardPredicate p: closedPredicates){
            predicates.add(p);
        }

        for (StandardPredicate p: openPredicates){
            predicates.add(p);
        }
        this.predicates = Collections.unmodifiableSet(predicates);
        this.openPredicates = Collections.unmodifiableSet(openPredicates);
        this.closedPredicates = Collections.unmodifiableSet(closedPredicates);
    }

    public Set<StandardPredicate> getValidPredicates() {
        return predicates;
    }

    @Override
    public boolean isValid(List<StandardPredicate> predicates, List<Boolean> isNegated) {
        int arity = predicates.get(0).getArity();
        int count = 0;
        for (Predicate p : predicates) {
            if (p.getArity() != arity || (count < predicates.size()-1 && !closedPredicates.contains(p))){
                return false;
            }
            count++;
        }
        if (!openPredicates.contains(predicates.get(predicates.size()-1))){
            return false;
        }
        return true;
    }

    public Rule getRule(List<StandardPredicate> predicates, List<Boolean> isNegated, boolean isSquared, double weight){
        if (!isValid(predicates, isNegated)){
            throw new IllegalArgumentException("all predicates must have same arity and " +
                    "head must be open and body closed.");
        }
        Formula[] qatoms = new Formula[predicates.size()-1];
        Variable[] vars = new Variable[predicates.get(0).getArity()];
        for (int i = 0; i < vars.length; i++) {
            vars[i] = new Variable("A" + Integer.toString(i));
        }
        for (int i = 0; i < qatoms.length; i++) {
            qatoms[i] = new QueryAtom(predicates.get(i), vars);
            //qatoms[i] = isNegated.get(i) ? new Negation(qatoms[i]):qatoms[i];
        }
        Formula head = new QueryAtom(predicates.get(predicates.size()-1), vars);
        head = isNegated.get(predicates.size()-1) ? new Negation(head):head;
        Formula and = (qatoms.length > 1) ? new Conjunction(qatoms) : qatoms[0];
        Formula implies = new Implication(and, head);
        log.trace("Rule generated: " + implies.toString());
        return new WeightedLogicalRule(implies, weight, isSquared);
    }
}

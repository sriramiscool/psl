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
public class PathRuleTemplate implements RuleTemplate {
    private static final Logger log = LoggerFactory.getLogger(PathRuleTemplate.class);

    protected final Set<Predicate> predicates;
    protected final Set<StandardPredicate> openPredicates;
    protected final Set<StandardPredicate> closedPredicates;


    private static boolean checkRequirement(List<Predicate> predicates) {
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

    public PathRuleTemplate(Set<StandardPredicate> closedPredicates, Set<StandardPredicate> openPredicates) {
        Set<Predicate> predicates = new HashSet<>();
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
    }

    public Set<Predicate> getValidPredicates() {
        return predicates;
    }

    @Override
    public boolean isValid(List<Predicate> predicates, List<Boolean> isNegated) {
        if (!checkRequirement(predicates) || !openPredicates.contains(predicates.get(predicates.size()-1))){
            return false;
        }
        return true;
    }

    public Rule getRule(List<Predicate> predicates, List<Boolean> isNegated, boolean isSquared, double weight){
        if (!isValid(predicates, isNegated)){
            throw new IllegalArgumentException("All predicates must have arity = 2.");
        }
        Variable v1 = new Variable("A0");
        Variable firstVar = v1;
        Variable v2 = new Variable("A1");
        Formula[] qatoms = new Formula[predicates.size()-1];
        for (int i = 0; i < predicates.size()-1; i++) {
            qatoms[i] = new QueryAtom(predicates.get(i), v1, v2);
            //qatoms[i] = isNegated.get(i) ? new Negation(qatoms[i]) : qatoms[i];
            v1 = v2;
            v2 = new Variable("A" + Integer.toString(i+2));
        }
        Formula head = new QueryAtom(predicates.get(predicates.size()-1), firstVar, v1);
        head = isNegated.get(predicates.size() - 1) ? new Negation(head) : head;
        Formula and = (qatoms.length > 1) ? new Conjunction(qatoms) : qatoms[0];
        Formula implies = new Implication(and, head);
        log.trace("Rule generated: " + implies.toString());
        return new WeightedLogicalRule(implies, weight, isSquared);
    }
}

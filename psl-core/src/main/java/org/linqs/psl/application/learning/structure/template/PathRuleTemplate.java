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

import java.util.*;

/**
 * Created by sriramsrinivasan on 12/1/19.
 */
public class PathRuleTemplate extends AbstractRuleTemplate {
    private static final Logger log = LoggerFactory.getLogger(PathRuleTemplate.class);


    private boolean checkRequirement(List<StandardPredicate> predicates) {
        for (int i = 0; i < predicates.size() ; i++) {
            if (invalidPredicate(predicates.get(i))){
                return false;
            }
        }
        return true;
    }

    private boolean invalidPredicate(Predicate p) {
        return p.getArity() != 2;
    }

    public PathRuleTemplate(Set<StandardPredicate> closedPredicates, Set<StandardPredicate> openPredicates){
        this(closedPredicates, openPredicates, new HashMap<StandardPredicate, StandardPredicate>());
    }

    public PathRuleTemplate(Set<StandardPredicate> closedPredicates, Set<StandardPredicate> openPredicates,
                            Map<StandardPredicate, StandardPredicate> open2BlockPred) {
        super(open2BlockPred, closedPredicates, openPredicates);
        for (StandardPredicate p: closedPredicates){
            if (invalidPredicate(p)) {
                predicates.remove(p);
                this.closedPredicates.remove(p);
            }
        }

        for (StandardPredicate p: openPredicates){
            if (invalidPredicate(p)) {
                predicates.remove(p);
                this.openPredicates.remove(p);
            }
        }
    }

    public Set<StandardPredicate> getValidPredicates() {
        return predicates;
    }

    @Override
    public boolean isValid(List<StandardPredicate> predicates, List<Boolean> isNegated) {
        if (!checkRequirement(predicates) || !openPredicates.contains(predicates.get(predicates.size()-1))){
            return false;
        }
        if (!predicates.get(0).getDomains()[0].equals(predicates.get(predicates.size()-1).getDomains()[0]) ||
                !predicates.get(predicates.size()-2).getDomains()[1].equals(predicates.get(predicates.size()-1).getDomains()[1]) ) {
            return false;
        }
        for (int i = 1 ; i < predicates.size()-1 ; i++){
            if(!predicates.get(i-1).getDomains()[1].equals(predicates.get(i).getDomains()[0])){
                return false;
            }
        }
        return true;
    }

    public Rule getRule(List<StandardPredicate> predicates, List<Boolean> isNegated, boolean isSquared, double weight){
        if (!isValid(predicates, isNegated)){
            throw new IllegalArgumentException("All predicates must have arity = 2.");
        }
        if (donotNegate){
            resetIsNegated(isNegated);
        }
        Variable v1 = new Variable("A0");
        Variable firstVar = v1;
        Variable v2 = new Variable("A1");
        List<Formula> qatoms = new ArrayList<>();
        for (int i = 0; i < predicates.size()-1; i++) {
//            qatoms.add(new QueryAtom(predicates.get(i), v1, v2));
            qatoms.add(isNegated.get(i) ? new Negation(new QueryAtom(predicates.get(i), v1, v2)) :
                    new QueryAtom(predicates.get(i), v1, v2));
//            qatoms[i] = isNegated.get(i) ? new Negation(qatoms[i]) : qatoms[i];
            if (open2BlockPred.containsKey(predicates.get(i))) {
                qatoms.add(new QueryAtom(open2BlockPred.get(predicates.get(i)), v1, v2));
            }
            v1 = v2;
            v2 = new Variable("A" + Integer.toString(i+2));
        }
        Formula head = new QueryAtom(predicates.get(predicates.size()-1), firstVar, v1);
        head = isNegated.get(predicates.size() - 1) ? new Negation(head) : head;
        if (open2BlockPred.containsKey(predicates.get(predicates.size()-1))) {
            qatoms.add(new QueryAtom(open2BlockPred.get(predicates.get(predicates.size()-1)), firstVar, v1));
        }
        Formula[] temp = new Formula[qatoms.size()];
        Formula[] qar = qatoms.toArray(temp);
        Formula and = (qatoms.size() > 1) ? new Conjunction(qar) : qatoms.get(0);
        Formula implies = new Implication(and, head);
        log.trace("Rule generated: " + implies.toString());
        return new WeightedLogicalRule(implies, weight, isSquared);
    }
}

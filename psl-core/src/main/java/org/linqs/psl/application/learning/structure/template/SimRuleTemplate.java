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
public class SimRuleTemplate extends AbstractRuleTemplate {
    private static final Logger log = LoggerFactory.getLogger(SimRuleTemplate.class);
    protected final Variable v1;
    protected final Variable v2;
    protected final Variable v3;


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

    public SimRuleTemplate(Set<StandardPredicate> closedPredicates, Set<StandardPredicate> openPredicates){
        this(closedPredicates, openPredicates, new HashMap<StandardPredicate, StandardPredicate>());
    }

    public SimRuleTemplate(Set<StandardPredicate> closedPredicates, Set<StandardPredicate> openPredicates,
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

        v1 = new Variable("A");
        v2 = new Variable("B");
        v3 = new Variable("C");
    }

    public Set<StandardPredicate> getValidPredicates() {
        return predicates;
    }

    @Override
    public boolean isValid(List<StandardPredicate> predicates, List<Boolean> isNegated) {
        if ((predicates.size() != 3) ||
                !checkRequirement(predicates)){
            return false;
        }
        if (!Arrays.equals(predicates.get(0).getDomains(),predicates.get(2).getDomains())) {
            return false;
        }
        if (!(predicates.get(0).getDomains()[0].equals(predicates.get(1).getDomains()[0]) ||
                predicates.get(0).getDomains()[1].equals(predicates.get(1).getDomains()[0]))) {
            return false;
        }
        return true;
    }

    public Rule getRule(List<StandardPredicate> predicates, List<Boolean> isNegated, boolean isSquared, double weight){
        if (!isValid(predicates, isNegated)){
            throw new IllegalArgumentException("Sim template needs three predicates with first and " +
                    "last the same and each with arity = 2.");
        }
        if (donotNegate){
            resetIsNegated(isNegated);
        }
        Formula q1 = new QueryAtom(predicates.get(0), v1, v2);
        q1 = isNegated.get(0) ? new Negation(q1):q1;
        Formula q2, q3;
        int headInd = 2;

        Formula q4 = null, q5 = null;
        if (open2BlockPred.containsKey(predicates.get(0))){
            q4 = new QueryAtom(open2BlockPred.get(predicates.get(0)), v1, v2);
        }
        if (predicates.get(1).getDomains()[0].equals(predicates.get(0).getDomains()[0])) {
            q2 = new QueryAtom(predicates.get(1), v1, v3);
            //q2 = isNegated.get(1) ? new Negation(q2):q2;
            q3 = new QueryAtom(predicates.get(headInd), v3, v2);
            q3 = isNegated.get(headInd) ? new Negation(q3):q3;
            if (open2BlockPred.containsKey(predicates.get(headInd))) {
                //blocking predicate
                q5 = new QueryAtom(open2BlockPred.get(predicates.get(headInd)), v3, v2);
            }
        } else {
            q2 = new QueryAtom(predicates.get(1), v2, v3);
            //q2 = isNegated.get(1) ? new Negation(q2):q2;
            q3 = new QueryAtom(predicates.get(headInd), v1, v3);
            q3 = isNegated.get(headInd) ? new Negation(q3) : q3;
            if (open2BlockPred.containsKey(predicates.get(headInd))) {
                //blocking predicate
                q5 = new QueryAtom(open2BlockPred.get(predicates.get(headInd)), v1, v3);
            }
        }
        Formula and;
        if (q4 == null && q5 == null) {
            and = new Conjunction(q1, q2);
        } else if(q4 == null) {
            and = new Conjunction(q1, q2, q5);
        } else if(q5 == null){
            and = new Conjunction(q1, q2, q4);
        } else {
            and = new Conjunction(q1, q2, q4, q5);
        }
        Formula implies = new Implication(and, q3);
        log.trace("Rule generated: " + implies.toString());
        return new WeightedLogicalRule(implies, weight, isSquared);
    }
}

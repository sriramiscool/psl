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
public class LocalRuleTemplate implements RuleTemplate {
    private static final Logger log = LoggerFactory.getLogger(LocalRuleTemplate.class);

    protected final Set<StandardPredicate> predicates;
    protected final Set<StandardPredicate> openPredicates;
    protected final Set<StandardPredicate> closedPredicates;
    protected Map<StandardPredicate, StandardPredicate> open2BlockPred;

    public LocalRuleTemplate(Set<StandardPredicate> closedPredicates, Set<StandardPredicate> openPredicates){
        this(closedPredicates, openPredicates, new HashMap<StandardPredicate, StandardPredicate>());
    }

    public LocalRuleTemplate(Set<StandardPredicate> closedPredicates, Set<StandardPredicate> openPredicates,
                             Map<StandardPredicate, StandardPredicate> open2BlockPred) {
        Set<StandardPredicate> predicates = new HashSet<>();
        this.open2BlockPred = open2BlockPred;
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
            if (p.getArity() != arity){// || (count < predicates.size()-1 && !closedPredicates.contains(p))){
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
        Set<StandardPredicate> set = new HashSet<>();
        for(int i = 0 ; i < predicates.size()-1; i++){
            set.add(predicates.get(i));
        }
        List<Formula> qatoms = new ArrayList<>();
        Variable[] vars = new Variable[predicates.get(0).getArity()];
        for (int i = 0; i < vars.length; i++) {
            vars[i] = new Variable("A" + Integer.toString(i));
        }
        int i = 0;
        for (StandardPredicate s:set) {
            Formula q = (new QueryAtom(s, vars));
            q = isNegated.get(i) ? new Negation(q):q;
            qatoms.add(q);
            if(open2BlockPred.containsKey(s)){
                qatoms.add(new QueryAtom(open2BlockPred.get(s), vars));
            }
            i++;
        }
        Formula head = new QueryAtom(predicates.get(predicates.size()-1), vars);
        head = isNegated.get(predicates.size()-1) ? new Negation(head):head;
        if(open2BlockPred.containsKey(predicates.get(predicates.size()-1))){
            qatoms.add(new QueryAtom(open2BlockPred.get(predicates.get(predicates.size()-1)), vars));
        }
        Formula[] temp = new Formula[qatoms.size()];
        Formula[] qar = qatoms.toArray(temp);
        Formula and = (qatoms.size() > 1) ? new Conjunction(qar) : qatoms.get(0);
        Formula implies = new Implication(and, head);
        log.trace("Rule generated: " + implies.toString());
        return new WeightedLogicalRule(implies, weight, isSquared);
    }
}

package org.linqs.psl.application.learning.structure.template;

import org.linqs.psl.model.atom.QueryAtom;
import org.linqs.psl.model.formula.Conjunction;
import org.linqs.psl.model.formula.Formula;
import org.linqs.psl.model.formula.Implication;
import org.linqs.psl.model.formula.Negation;
import org.linqs.psl.model.predicate.StandardPredicate;
import org.linqs.psl.model.rule.Rule;
import org.linqs.psl.model.rule.logical.WeightedLogicalRule;
import org.linqs.psl.model.term.Term;
import org.linqs.psl.model.term.Variable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * Created by sriramsrinivasan on 12/1/19.
 */
public class LocalRuleTemplate extends AbstractRuleTemplate {
    private static final Logger log = LoggerFactory.getLogger(LocalRuleTemplate.class);

    public LocalRuleTemplate(Set<StandardPredicate> closedPredicates, Set<StandardPredicate> openPredicates){
        this(closedPredicates, openPredicates, new HashMap<StandardPredicate, StandardPredicate>());
    }

    public LocalRuleTemplate(Set<StandardPredicate> closedPredicates, Set<StandardPredicate> openPredicates,
                             Map<StandardPredicate, StandardPredicate> open2BlockPred) {
        super(open2BlockPred, closedPredicates, openPredicates);
    }

    public Set<StandardPredicate> getValidPredicates() {
        return predicates;
    }

    @Override
    public boolean isValid(List<StandardPredicate> predicates, List<Boolean> isNegated) {
        if (predicates.size() < 2) {
            return false;
        }
        if (!openPredicates.contains(predicates.get(predicates.size()-1))){
            return false;
        }
        Set<String> headDomian = new HashSet<String>(Arrays.asList(predicates.get(predicates.size()-1).getDomains()));
        int countClosed = 0;
        for (StandardPredicate p : predicates) {
            Set<String> curDomains = new HashSet<String>(Arrays.asList(p.getDomains()));
            if (!((headDomian.containsAll(curDomains) || curDomains.containsAll(headDomian)))){
                return false;
            }
            if (this.closedPredicates.contains(p)) {
                countClosed++;
            }
        }
        if (countClosed == 0) {
            return false;
        }
        return true;
    }

    public Rule getRule(List<StandardPredicate> predicates, List<Boolean> isNegated, boolean isSquared, double weight){
        if (!isValid(predicates, isNegated)){
            throw new IllegalArgumentException("all predicates must have same arity and " +
                    "head must be open and body closed.");
        }
        Set<StandardPredicate> setBodyPred = new HashSet<>();
        Map<String, Variable> domainToVar = new HashMap<>();
        int varId = 0;
        for(int i = 0 ; i < predicates.size()-1; i++){
            setBodyPred.add(predicates.get(i));
            for (int j = 0 ; j < predicates.get(i).getDomains().length; j++) {
                String[] domains = predicates.get(i).getDomains();
                if (!domainToVar.containsKey(domains[j])) {
                    domainToVar.put(domains[j], new Variable("A" + Integer.toString(varId++)));
                }
            }
        }

        for (int j = 0 ; j < predicates.get(predicates.size()-1).getDomains().length; j++) {
            String[] domains = predicates.get(predicates.size() - 1).getDomains();
            if (!domainToVar.containsKey(domains[j])) {
                domainToVar.put(domains[j], new Variable("A" + Integer.toString(varId++)));
            }
        }

        List<Formula> qatoms = new ArrayList<>();
        int i = 0;
        for (StandardPredicate s:setBodyPred) {
            String[] domains = s.getDomains();
            Term[] vars = new Term[domains.length];
            for (int j = 0; j < domains.length; j++) {
                vars[j] = domainToVar.get(domains[j]);
            }
            Formula q = (new QueryAtom(s, vars));
            q = isNegated.get(i) ? new Negation(q):q;
            qatoms.add(q);
            if(open2BlockPred.containsKey(s)){
                qatoms.add(new QueryAtom(open2BlockPred.get(s), vars));
            }
            i++;
        }
        String[] headDomains = predicates.get(predicates.size()-1).getDomains();
        Term[] headVars = new Term[headDomains.length];
        for (int j = 0; j < headDomains.length; j++) {
            headVars[j] = domainToVar.get(headDomains[j]);
        }
        Formula head = new QueryAtom(predicates.get(predicates.size()-1), headVars);
        head = isNegated.get(predicates.size()-1) ? new Negation(head):head;
        if(open2BlockPred.containsKey(predicates.get(predicates.size()-1))){
            qatoms.add(new QueryAtom(open2BlockPred.get(predicates.get(predicates.size()-1)), headVars));
        }
        Formula[] temp = new Formula[qatoms.size()];
        Formula[] qar = qatoms.toArray(temp);
        Formula and = (qatoms.size() > 1) ? new Conjunction(qar) : qatoms.get(0);
        Formula implies = new Implication(and, head);
        log.trace("Rule generated: " + implies.toString());
        return new WeightedLogicalRule(implies, weight, isSquared);
    }
}

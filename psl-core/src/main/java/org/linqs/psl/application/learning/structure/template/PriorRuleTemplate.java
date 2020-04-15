package org.linqs.psl.application.learning.structure.template;

import org.linqs.psl.model.atom.QueryAtom;
import org.linqs.psl.model.predicate.StandardPredicate;
import org.linqs.psl.model.rule.Rule;
import org.linqs.psl.model.rule.arithmetic.WeightedArithmeticRule;
import org.linqs.psl.model.rule.arithmetic.expression.ArithmeticRuleExpression;
import org.linqs.psl.model.rule.arithmetic.expression.SummationAtomOrAtom;
import org.linqs.psl.model.rule.arithmetic.expression.coefficient.Coefficient;
import org.linqs.psl.model.rule.arithmetic.expression.coefficient.ConstantNumber;
import org.linqs.psl.model.term.Variable;
import org.linqs.psl.reasoner.function.FunctionComparator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * Created by sriramsrinivasan on 4/11/20.
 */
public class PriorRuleTemplate implements RuleTemplate {
    private static final Logger log = LoggerFactory.getLogger(PriorRuleTemplate.class);

    protected final Set<StandardPredicate> predicates;
    protected final Set<StandardPredicate> openPredicates;
    protected final Set<StandardPredicate> closedPredicates;
    protected Map<StandardPredicate, StandardPredicate> open2BlockPred;

    public PriorRuleTemplate(Set<StandardPredicate> closedPredicates, Set<StandardPredicate> openPredicates){
        this(closedPredicates, openPredicates, new HashMap<StandardPredicate, StandardPredicate>());
    }

    public PriorRuleTemplate(Set<StandardPredicate> closedPredicates, Set<StandardPredicate> openPredicates,
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

    @Override
    public Set<StandardPredicate> getValidPredicates() {
        return this.openPredicates;
    }

    @Override
    public boolean isValid(List<StandardPredicate> predicates, List<Boolean> isNegated) {
        if(predicates.size()!=1 ||
                !this.openPredicates.contains(predicates.get(0)) ||
                isNegated.size() != predicates.size()){
            return false;
        }
        return true;
    }

    @Override
    public Rule getRule(List<StandardPredicate> predicates, List<Boolean> isNegated, boolean isSquared, double weight) {
        if (!isValid(predicates, isNegated)){
            throw new IllegalArgumentException("Prior template needs one open predicate.");
        }

        Variable[] vars = new Variable[predicates.get(0).getArity()];
        for (int i = 0; i < vars.length; i++) {
            vars[i] = new Variable("A"+Integer.toString(i));
        }
        SummationAtomOrAtom prior = new QueryAtom(predicates.get(0), vars);
        ConstantNumber constant = isNegated.get(0) ? new ConstantNumber(0.0f) : new ConstantNumber(1.0f);
        ArithmeticRuleExpression expression = new ArithmeticRuleExpression(
                Arrays.asList((Coefficient) new ConstantNumber(1.0f)),
                Arrays.asList(prior),
                FunctionComparator.EQ,
                constant);
        log.trace("Rule generated: " + expression.toString());
        return new WeightedArithmeticRule(expression, weight, isSquared);
    }
}

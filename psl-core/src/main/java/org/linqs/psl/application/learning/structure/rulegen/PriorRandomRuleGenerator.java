package org.linqs.psl.application.learning.structure.rulegen;

import org.linqs.psl.application.learning.structure.template.PriorRuleTemplate;
import org.linqs.psl.model.predicate.StandardPredicate;
import org.linqs.psl.model.rule.Rule;
import org.linqs.psl.util.RandUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * Created by sriramsrinivasan on 4/11/20.
 */
public class PriorRandomRuleGenerator extends PriorRuleTemplate implements RandomRuleGenerator {
    private static final Logger log = LoggerFactory.getLogger(PriorRandomRuleGenerator.class);

    private List<StandardPredicate> localCopyPredicates;
    private List<StandardPredicate> localCopyOpenPredicates;
    private List<StandardPredicate> localCopyClosedPredicates;
    private Map<Integer, List<StandardPredicate>> arityToPredicates;


    public PriorRandomRuleGenerator(Set<StandardPredicate> closedPredicates, Set<StandardPredicate> openPredicates){
        this(closedPredicates, openPredicates, new HashMap<StandardPredicate, StandardPredicate>());
    }

    public PriorRandomRuleGenerator(Set<StandardPredicate> closedPredicates, Set<StandardPredicate> openPredicates,
                                    Map<StandardPredicate, StandardPredicate> open2BlockPred) {
        super(closedPredicates, openPredicates, open2BlockPred);
        this.localCopyPredicates = new ArrayList<>(this.predicates);
        this.localCopyOpenPredicates = new ArrayList<>();
        this.localCopyClosedPredicates = new ArrayList<>();
        for (StandardPredicate p : this.closedPredicates) {
            this.localCopyClosedPredicates.add(p);
        }
        for (StandardPredicate p : this.openPredicates) {
            this.localCopyOpenPredicates.add(p);
        }
        for (StandardPredicate p : open2BlockPred.values()){
            this.localCopyClosedPredicates.remove(p);
            this.localCopyPredicates.remove(p);
        }
        this.arityToPredicates = new HashMap<>();
        for (StandardPredicate p: this.localCopyClosedPredicates){
            List<StandardPredicate> preds = this.arityToPredicates.get(p.getArity());
            if (preds == null){
                preds = new ArrayList<>();
            }
            preds.add(p);
            this.arityToPredicates.put(p.getArity(), preds);
        }
    }




    @Override
    public Rule generateRule(int maxRuleLen) {
        List<StandardPredicate> predicates = new ArrayList<>();
        List<Boolean> isNegated = new ArrayList<>();
        predicates.add(this.localCopyOpenPredicates.get(RandUtils.nextInt(this.localCopyOpenPredicates.size())));
        isNegated.add(RandUtils.nextBoolean());
        return getRule(predicates, isNegated, true, 0.0);
    }
}

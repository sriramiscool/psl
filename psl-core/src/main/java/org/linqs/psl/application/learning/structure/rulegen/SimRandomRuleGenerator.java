package org.linqs.psl.application.learning.structure.rulegen;

import org.linqs.psl.application.learning.structure.template.SimRuleTemplate;
import org.linqs.psl.model.predicate.StandardPredicate;
import org.linqs.psl.model.rule.Rule;
import org.linqs.psl.util.RandUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

public class SimRandomRuleGenerator extends SimRuleTemplate implements DRLRuleGenerator,RandomRuleGenerator{
    private static final Logger log = LoggerFactory.getLogger(SimRandomRuleGenerator.class);

    private List<StandardPredicate> localCopyPredicates;
    private List<StandardPredicate> localCopyOpenPredicates;
    private List<StandardPredicate> localCopyClosedPredicates;

    public SimRandomRuleGenerator(Set<StandardPredicate> closedPredicates, Set<StandardPredicate> openPredicates){
        this(closedPredicates, openPredicates, new HashMap<StandardPredicate, StandardPredicate>());
    }

    public SimRandomRuleGenerator(Set<StandardPredicate> closedPredicates, Set<StandardPredicate> openPredicates,
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
    }

    @Override
    public Rule generateRule(StandardPredicate headPredicate, List<StandardPredicate> bodyPredicates,
                             List<Boolean> isNegated) {
        if (bodyPredicates == null || headPredicate == null || bodyPredicates.size() != 2) {
            throw new RuntimeException("Rule length must be greater than 2.");
        }
        List<StandardPredicate> predicates = new ArrayList<>();

        predicates.addAll(bodyPredicates);
        predicates.add(headPredicate);
        isNegated.add(isNegated.get(0));
        return getRule(predicates, isNegated, true, 0);
    }

    @Override
    public Rule generateRule(int maxRuleLen) {
        List<StandardPredicate> predicates = new ArrayList<>();
        List<Boolean> isNegated = new ArrayList<>();
        StandardPredicate headPredicate = this.localCopyOpenPredicates.get(RandUtils.nextInt(this.localCopyOpenPredicates.size()));
        String simDomain1 = headPredicate.getDomains()[0];
        String simDomain2 = headPredicate.getDomains()[1];
        predicates.add(headPredicate);
        isNegated.add(RandUtils.nextBoolean());
        List<StandardPredicate> possiblePredicates = new ArrayList<>();
        for (StandardPredicate p : this.localCopyClosedPredicates) {
            if(p.getDomains()[0].equals(p.getDomains()[1]) &&
                    (p.getDomains()[0].equals(simDomain1) || p.getDomains()[0].equals(simDomain2) )) {
                possiblePredicates.add(p);
            }
        }
        if (possiblePredicates == null) {
            log.debug("Failed to generate a rule");
            return null;
        }
        predicates.add(possiblePredicates.get(RandUtils.nextInt(possiblePredicates.size())));
        isNegated.add(RandUtils.nextBoolean());
        predicates.add(headPredicate);
//        isNegated.add(RandUtils.nextBoolean());
        isNegated.add(false);
        return getRule(predicates, isNegated, true, 0);
    }

    @Override
    public boolean isValid(StandardPredicate targetPredicate, List<StandardPredicate> rulePredicates, StandardPredicate action, int maxRuleLength) {
        int currentRuleLength = rulePredicates.size();
        boolean isValidFlag = false;

        if (action.getDomains().length == 2) {
            //If it is the first predicate after target predicate
            if (currentRuleLength == 1) {
                if (Arrays.equals(action.getDomains(), targetPredicate.getDomains())) {
                    isValidFlag = true;
                }
            } else {
                if ((targetPredicate.getDomains()[0].equals(action.getDomains()[0]) && targetPredicate.getDomains()[0].equals(action.getDomains()[1])) || (targetPredicate.getDomains()[1].equals(action.getDomains()[0]) && targetPredicate.getDomains()[1].equals(action.getDomains()[1]))) {
                    isValidFlag = true;
                }
            }
        }
        return isValidFlag;
    }
}

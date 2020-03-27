package org.linqs.psl.application.learning.structure.rulegen;

import org.linqs.psl.application.learning.structure.template.SimRuleTemplate;
import org.linqs.psl.model.predicate.StandardPredicate;
import org.linqs.psl.model.rule.Rule;
import org.linqs.psl.util.RandUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

public class SimRandomRuleGenerator extends SimRuleTemplate implements DRLRuleGenerator{
    private static final Logger log = LoggerFactory.getLogger(SimRandomRuleGenerator.class);

    private List<StandardPredicate> localCopyPredicates;
    private List<StandardPredicate> localCopyOpenPredicates;
    private List<StandardPredicate> localCopyClosedPredicates;

    public SimRandomRuleGenerator(Set<StandardPredicate> closedPredicates, Set<StandardPredicate> openPredicates) {
        super(closedPredicates, openPredicates);
        this.localCopyPredicates = new ArrayList<>(this.predicates);
        this.localCopyOpenPredicates = new ArrayList<>();
        this.localCopyClosedPredicates = new ArrayList<>();
        for (StandardPredicate p : this.closedPredicates) {
            this.localCopyClosedPredicates.add(p);
        }
        for (StandardPredicate p : this.openPredicates) {
            this.localCopyOpenPredicates.add(p);
        }
    }

    @Override
    public Rule generateRule(int maxRuleLen) {
        List<StandardPredicate> predicates = new ArrayList<>();
        List<Boolean> isNegated = new ArrayList<>();
        StandardPredicate headPredicate = this.localCopyOpenPredicates.get(RandUtils.nextInt(this.localCopyOpenPredicates.size()));
        String simDomain = headPredicate.getDomains()[0];
        predicates.add(headPredicate);
        isNegated.add(RandUtils.nextBoolean());
        List<StandardPredicate> possiblePredicates = new ArrayList<>();
        for (StandardPredicate p : this.localCopyClosedPredicates) {
            if(p.getDomains()[0].equals(simDomain) && p.getDomains()[1].equals(simDomain)) {
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
        isNegated.add(RandUtils.nextBoolean());
        return getRule(predicates, isNegated, true, 0);
    }

    @Override
    public boolean isValid(StandardPredicate targetPredicate, ArrayList<StandardPredicate> rulePredicates, StandardPredicate action) {
        int currentRuleLength = rulePredicates.size();
        boolean isValidFlag = false;

        if (action.getDomains().length == 2) {
            //If it is the first predicate after target predicate
            if (currentRuleLength == 1) {
                if ((targetPredicate.getDomains()[0].equals(action.getDomains()[0]) && targetPredicate.getDomains()[0].equals(action.getDomains()[1])) || (targetPredicate.getDomains()[1].equals(action.getDomains()[0]) && targetPredicate.getDomains()[1].equals(action.getDomains()[1]))) {
                    isValidFlag = true;
                }
            } else {
                if (Arrays.equals(action.getDomains(), targetPredicate.getDomains())) {
                    isValidFlag = true;
                }
            }
        }
        return isValidFlag;
    }
}

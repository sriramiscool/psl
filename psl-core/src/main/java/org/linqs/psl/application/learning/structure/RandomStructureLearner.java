package org.linqs.psl.application.learning.structure;

import org.linqs.psl.application.learning.structure.template.LocalRuleTemplate;
import org.linqs.psl.application.learning.structure.template.PathRuleTemplate;
import org.linqs.psl.application.learning.structure.template.SimRuleTemplate;
import org.linqs.psl.config.Config;
import org.linqs.psl.database.Database;
import org.linqs.psl.model.predicate.StandardPredicate;
import org.linqs.psl.model.rule.Rule;
import org.linqs.psl.model.rule.WeightedRule;
import org.linqs.psl.util.RandUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * Created by sriramsrinivasan on 11/29/19.
 */
public class RandomStructureLearner extends AbstractStructureLearningApplication {
    private static final Logger log = LoggerFactory.getLogger(RandomStructureLearner.class);

    private static final String CONFIG_PREFIX = "rsl";
    private static final String ITERATIONS_KEY = CONFIG_PREFIX + ".iter";
    private static final int ITERATIONS_DEFAULT = 2;
    private static final String MAX_RULE_LEN_KEY = CONFIG_PREFIX + ".rulelen";
    private static final int MAX_RULE_LEN_DEFAULT = 3;
    private static final String NUM_RULES_KEY = CONFIG_PREFIX + ".numrules";
    private static final int NUM_RULES_DEFAULT = 5;
    private static final int NUM_TRIES_PER_RULE_DEFAULT = 5;

    private int numIte;
    protected Map<StandardPredicate, Integer> predicateToId;
    private int numRules;
    protected Map<Integer, RandomRuleGenerator> idToTemplate;
    private int maxRuleLen;

    public RandomStructureLearner(List<Rule> rules, Database rvDB, Database observedDB,
                                  Set<StandardPredicate> closedPredicates,
                                  Set<StandardPredicate> openPredicates) {
        super(rules, rvDB, observedDB, closedPredicates, openPredicates);
        this.numIte = Config.getInt(ITERATIONS_KEY, ITERATIONS_DEFAULT);
        this.numRules = Config.getInt(NUM_RULES_KEY, NUM_RULES_DEFAULT);
        this.maxRuleLen = Config.getInt(MAX_RULE_LEN_KEY, MAX_RULE_LEN_DEFAULT);

        this.predicateToId = new HashMap<>();
        this.idToTemplate = new HashMap<>();

        this.idToTemplate.put(0, new PathRandomRuleGenerator(closedPredicates, openPredicates));
        this.idToTemplate.put(1, new SimRandomRuleGenerator(closedPredicates, openPredicates));
        this.idToTemplate.put(2, new LocalRandomRuleGenerator(closedPredicates, openPredicates));

        for (int i = 0; i < this.predicates.size() ; i++) {
            this.predicateToId.put(this.predicates.get(i), i);
        }
    }

    @Override
    protected void doLearn() {

        for (int i = 0; i < this.numIte; i++) {
            this.populateNextRulesOfModel();
            double metric = this.evaluator.getRepresentativeMetric();
            if (!this.evaluator.isHigherRepresentativeBetter()){
                metric *= -1;
            }
            if (metric > this.bestValueForRulesSoFar){
                this.bestValueForRulesSoFar = metric;
                this.bestRulesSoFar.clear();
                this.bestRulesSoFar.addAll(this.allRules);
                log.debug("Got a better model with metric: " + metric);

            }
        }

    }

    private void populateNextRulesOfModel() {
        this.resetModel();
        for (int i = 0; i < this.numRules; i++){
            RandomRuleGenerator ruleGen = idToTemplate.get(RandUtils.nextInt(idToTemplate.size()));
            int tries = 0;
            Rule r;
            do {
                r = ruleGen.generateRule(this.maxRuleLen);
                tries++;
            }while (tries < NUM_TRIES_PER_RULE_DEFAULT && r == null);
            if(r == null) {
                throw new RuntimeException("Exceeded maximum number of tries for a rule");
            }
            this.addRuleToModel(r);
            log.info("Added rule: " + r.toString() );
        }

        //this.getNewWeightLearner().learn();
        for(WeightedRule r: this.mutableRules) {
            r.setWeight(1);
        }
        this.evaluator.compute(trainingMap);
    }

    interface RandomRuleGenerator {
        public Rule generateRule(int maxRuleLen);
    }

    static class PathRandomRuleGenerator extends PathRuleTemplate implements RandomRuleGenerator{
        private List<StandardPredicate> localCopyPredicates;
        private List<StandardPredicate> localCopyOpenPredicates;
        private List<StandardPredicate> localCopyClosedPredicates;

        public PathRandomRuleGenerator(Set<StandardPredicate> closedPredicates, Set<StandardPredicate> openPredicates) {
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
            if (maxRuleLen < 3) {
                throw new RuntimeException("Rule length must be greater than 2.");
            }
            int newRuleLen = RandUtils.nextInt(maxRuleLen-2) + 3;
            List<StandardPredicate> predicates = new ArrayList<>();
            List<Boolean> isNegated = new ArrayList<>();

            StandardPredicate headPredicate = this.localCopyOpenPredicates.get(RandUtils.nextInt(this.localCopyOpenPredicates.size()));
            List<StandardPredicate> validPredicates = new ArrayList<>();
            for (StandardPredicate p: this.localCopyPredicates) {
                if (p.getDomains()[0].equals(headPredicate.getDomains()[0])) {
                    validPredicates.add(p);
                }
            }

            StandardPredicate nextPredicate = validPredicates.get(RandUtils.nextInt(validPredicates.size()));
            predicates.add(nextPredicate);
            isNegated.add(RandUtils.nextBoolean());
            String prevDomain = nextPredicate.getDomains()[1];

            for (int i = 1; i < newRuleLen - 1; i++) {
                validPredicates = new ArrayList<>();
                for (StandardPredicate p: this.localCopyPredicates) {
                    if (p.getDomains()[0].equals(prevDomain)) {
                        validPredicates.add(p);
                    }
                }
                if (validPredicates.size() == 0) {
                    log.debug("Failed to generate a rule");
                    return null;
                }
                nextPredicate = validPredicates.get(RandUtils.nextInt(validPredicates.size()));
                predicates.add(nextPredicate);
                isNegated.add(RandUtils.nextBoolean());
                prevDomain = nextPredicate.getDomains()[1];
            }
            predicates.add(headPredicate);
            isNegated.add(RandUtils.nextBoolean());
            return getRule(predicates, isNegated, true, 0);
        }
    }

    static class SimRandomRuleGenerator extends SimRuleTemplate implements RandomRuleGenerator{
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
    }

    static class LocalRandomRuleGenerator extends LocalRuleTemplate implements RandomRuleGenerator{
        private List<StandardPredicate> localCopyPredicates;
        private List<StandardPredicate> localCopyOpenPredicates;
        private List<StandardPredicate> localCopyClosedPredicates;
        private Map<Integer, List<StandardPredicate>> arityToPredicates;

        public LocalRandomRuleGenerator(Set<StandardPredicate> closedPredicates, Set<StandardPredicate> openPredicates) {
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
            if (maxRuleLen < 2) {
                throw new RuntimeException("Rule lenght must be greater than 2.");
            }
            int newRuleLen = RandUtils.nextInt(maxRuleLen-1) + 2;
            List<StandardPredicate> predicates = new ArrayList<>();
            List<Boolean> isNegated = new ArrayList<>();
            StandardPredicate headPredicate = this.localCopyOpenPredicates.get(RandUtils.nextInt(this.localCopyOpenPredicates.size()));
            int chosenArity = headPredicate.getArity();
            String[] chosenDomains = headPredicate.getDomains();
            List<StandardPredicate> possiblePredicates = new ArrayList<>();
            for (StandardPredicate p : this.arityToPredicates.get(chosenArity)) {
                if(Arrays.equals(p.getDomains(), chosenDomains)) {
                   possiblePredicates.add(p);
                }
            }
            if (possiblePredicates == null) {
                log.debug("Failed to generate a rule");
                return null;
            }
            Set<StandardPredicate> usedPredicate = new HashSet<>();
            for (int i = 0; i < newRuleLen - 1; i++) {
                StandardPredicate p = possiblePredicates.get(RandUtils.nextInt(possiblePredicates.size()));
                if (usedPredicate.contains(p)){
                    break;
                }
                predicates.add(p);
                isNegated.add(RandUtils.nextBoolean());
                usedPredicate.add(p);
            }
            predicates.add(headPredicate);
            isNegated.add(RandUtils.nextBoolean());
            return getRule(predicates, isNegated, true, 0);
        }
    }

}

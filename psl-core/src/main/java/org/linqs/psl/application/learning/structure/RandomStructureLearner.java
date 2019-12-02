package org.linqs.psl.application.learning.structure;

import org.linqs.psl.application.learning.structure.template.*;
import org.linqs.psl.config.Config;
import org.linqs.psl.database.Database;
import org.linqs.psl.model.predicate.Predicate;
import org.linqs.psl.model.predicate.StandardPredicate;
import org.linqs.psl.model.rule.Rule;
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
    private static final int ITERATIONS_DEFAULT = 10;
    private static final String MAX_RULE_LEN_KEY = CONFIG_PREFIX + ".rulelen";
    private static final int MAX_RULE_LEN_DEFAULT = 3;
    private static final String NUM_RULES_KEY = CONFIG_PREFIX + ".numrules";
    private static final int NUM_RULES_DEFAULT = 5;

    private int numIte;
    protected Map<Predicate, Integer> predicateToId;
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
        for (int i = 0; i < this.predicates.size() ; i++) {
            this.predicateToId.put(this.predicates.get(i), i);
        }
        this.idToTemplate = new HashMap<>();
        this.idToTemplate.put(0, new PathRandomRuleGenerator(closedPredicates, openPredicates));
        this.idToTemplate.put(1, new SimRandomRuleGenerator(closedPredicates, openPredicates));
        this.idToTemplate.put(2, new LocalRandomRuleGenerator(closedPredicates, openPredicates));
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
            Rule r = ruleGen.generateRule(this.maxRuleLen);
            this.addRuleToModel(r);
        }
        this.getNewWeightLearner().learn();
        this.evaluator.compute(trainingMap);
    }

    interface RandomRuleGenerator {
        public Rule generateRule(int maxRuleLen);
    }

    static class PathRandomRuleGenerator extends PathRuleTemplate implements RandomRuleGenerator{
        private List<Predicate> localCopyPredicates;
        private List<Predicate> localCopyOpenPredicates;
        private List<Predicate> localCopyClosedPredicates;

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
                throw new RuntimeException("Rule lenght must be greater than 2.");
            }
            int newRuleLen = RandUtils.nextInt(maxRuleLen-2) + 3;
            List<Predicate> predicates = new ArrayList<>();
            List<Boolean> isNegated = new ArrayList<>();
            for (int i = 0; i < newRuleLen - 1; i++) {
                predicates.add(this.localCopyPredicates.get(RandUtils.nextInt(this.localCopyPredicates.size())));
                isNegated.add(RandUtils.nextBoolean());
            }
            predicates.add(this.localCopyOpenPredicates.get(RandUtils.nextInt(this.localCopyOpenPredicates.size())));
            isNegated.add(RandUtils.nextBoolean());
            return getRule(predicates, isNegated, true, 0);
        }
    }

    static class SimRandomRuleGenerator extends SimRuleTemplate implements RandomRuleGenerator{
        private List<Predicate> localCopyPredicates;
        private List<Predicate> localCopyOpenPredicates;
        private List<Predicate> localCopyClosedPredicates;

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
            List<Predicate> predicates = new ArrayList<>();
            List<Boolean> isNegated = new ArrayList<>();
            predicates.add(this.localCopyOpenPredicates.get(RandUtils.nextInt(this.localCopyOpenPredicates.size())));
            isNegated.add(RandUtils.nextBoolean());
            predicates.add(this.localCopyClosedPredicates.get(RandUtils.nextInt(this.localCopyClosedPredicates.size())));
            isNegated.add(RandUtils.nextBoolean());
            predicates.add(predicates.get(0));
            isNegated.add(RandUtils.nextBoolean());
            return getRule(predicates, isNegated, true, 0);
        }
    }

    static class LocalRandomRuleGenerator extends LocalRuleTemplate implements RandomRuleGenerator{
        private List<Predicate> localCopyPredicates;
        private List<Predicate> localCopyOpenPredicates;
        private List<Predicate> localCopyClosedPredicates;
        private Map<Integer, List<Predicate>> arityToPredicates;

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
            for (Predicate p: this.localCopyClosedPredicates){
                List<Predicate> preds = this.arityToPredicates.get(p.getArity());
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
            List<Predicate> predicates = new ArrayList<>();
            List<Boolean> isNegated = new ArrayList<>();
            Predicate headPredicate = this.localCopyOpenPredicates.get(RandUtils.nextInt(this.localCopyOpenPredicates.size()));
            int chosenArity = headPredicate.getArity();
            List<Predicate> possiblePredicates = this.arityToPredicates.get(chosenArity);
            if (possiblePredicates == null) {
                throw new RuntimeException("There is no closed predicate with same arity as open predicates.");
            }
            for (int i = 0; i < newRuleLen - 1; i++) {
                predicates.add(possiblePredicates.get(RandUtils.nextInt(possiblePredicates.size())));
                isNegated.add(RandUtils.nextBoolean());
            }
            predicates.add(headPredicate);
            isNegated.add(RandUtils.nextBoolean());
            return getRule(predicates, isNegated, true, 0);
        }
    }

}

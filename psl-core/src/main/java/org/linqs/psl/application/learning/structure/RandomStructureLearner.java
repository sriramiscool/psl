package org.linqs.psl.application.learning.structure;

import org.linqs.psl.application.learning.structure.rulegen.*;
import org.linqs.psl.application.learning.weight.WeightLearningApplication;
import org.linqs.psl.config.Config;
import org.linqs.psl.database.Database;
import org.linqs.psl.model.predicate.StandardPredicate;
import org.linqs.psl.model.rule.Rule;
import org.linqs.psl.model.rule.WeightedRule;
import org.linqs.psl.model.rule.arithmetic.WeightedArithmeticRule;
import org.linqs.psl.util.RandUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileNotFoundException;
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
    private static final String EXPLAINABLE_RULES_RATIO_KEY = CONFIG_PREFIX + ".expruleratio";
    private static final float EXPLAINABLE_RULES_RATIO_DEFAULT = -1.0f;
    private static final int NUM_TRIES_PER_RULE_DEFAULT = 1000;
    private static final String BLOCK_PRED_MAP_KEY = CONFIG_PREFIX + ".block";
    private static final String PRED_EXPLAIN_MAP_KEY = CONFIG_PREFIX + ".predexp";

    private int numIte;
    protected Map<StandardPredicate, Integer> predicateToId;
    private int numRules;
    protected Map<Integer, RandomRuleGenerator> idToTemplate;
    private int maxRuleLen;
    private  Map<Boolean, Set<String>> explainablePredicates;
    private float explainableRatio;

    public RandomStructureLearner(List<Rule> rules, Database rvDB, Database observedDB,
                                  Set<StandardPredicate> closedPredicates,
                                  Set<StandardPredicate> openPredicates) {
        super(rules, rvDB, observedDB, closedPredicates, openPredicates);
        this.numIte = Config.getInt(ITERATIONS_KEY, ITERATIONS_DEFAULT);
        this.numRules = Config.getInt(NUM_RULES_KEY, NUM_RULES_DEFAULT);
        this.maxRuleLen = Config.getInt(MAX_RULE_LEN_KEY, MAX_RULE_LEN_DEFAULT);
        this.explainableRatio = Config.getFloat(EXPLAINABLE_RULES_RATIO_KEY, EXPLAINABLE_RULES_RATIO_DEFAULT);
        String predExplainFileName = Config.getString(PRED_EXPLAIN_MAP_KEY, null);
        if (this.explainableRatio > 0 && predExplainFileName == null) {
            throw new RuntimeException("Explainable ratio mentioed but " +
                    "no file given with predicate explainability information. " +
                    "Must pass a value for: -D " + PRED_EXPLAIN_MAP_KEY);
        }
        if (this.explainableRatio > 1) {
            log.warn("Explainable ratio must be [0,1]. >1 is considered 1 and <0 implies ignore ratio. " +
                    "Note: this produces at least these many explainable rules.");
        }

        this.predicateToId = new HashMap<>();

        if (predExplainFileName != null) {
            this.explainablePredicates = new HashMap<>();
            this.explainablePredicates.put(true, new HashSet<String>());
            this.explainablePredicates.put(false, new HashSet<String>());

            try {
                File myObj = new File(predExplainFileName);
                if (!myObj.exists()) {
                    throw new RuntimeException("Explainable predicates file does not exist : " + predExplainFileName);
                }
                Scanner myReader = new Scanner(myObj);
                while (myReader.hasNextLine()) {
                    String data = myReader.nextLine();
                    data = data.toUpperCase();
                    String[] split = data.split(":");
                    this.explainablePredicates.get(split[1].contains("YES")).add(split[0]);
                }
                myReader.close();
            } catch (FileNotFoundException e) {
                throw new RuntimeException(e);
            }
        }

        for (int i = 0; i < this.predicates.size() ; i++) {
            this.predicateToId.put(this.predicates.get(i), i);
        }

        String blockStr = Config.getString(BLOCK_PRED_MAP_KEY, null);
        Map<String, StandardPredicate> predicateStrMap = new HashMap<>();
        Map<String, StandardPredicate> closedPredicateStrMap = new HashMap<>();
        Map<StandardPredicate, StandardPredicate> open2BlockPred = new HashMap<>();
        for (StandardPredicate p: openPredicates){
            predicateStrMap.put(p.getName().toUpperCase(), p);
        }
        for (StandardPredicate p: closedPredicates){
            closedPredicateStrMap.put(p.getName().toUpperCase(), p);
            predicateStrMap.put(p.getName().toUpperCase(), p);
        }

        if(blockStr != null){
            String[] split = blockStr.split(",");
            if (split.length < 1) {
                throw new RuntimeException("Block predicate specified incorrect format: expected bp1:op2,bp3:op4, " +
                        "found: " + blockStr);
            }
            for (int i = 0 ; i < split.length ; i++){
                String s = split[i];
                String[] b2o = s.split(":");
                if (b2o.length != 2){
                    throw new RuntimeException("Block predicate specified incorrect format: expected p1:p2,p3:p4, " +
                            "found: " + blockStr);
                }
                String blk = b2o[0].toUpperCase();
                String opn = b2o[1].toUpperCase();
                open2BlockPred.put(predicateStrMap.get(opn), closedPredicateStrMap.get(blk));
            }
        }
        this.idToTemplate = new HashMap<>();

        this.idToTemplate.put(0, new PathRandomRuleGenerator(closedPredicates, openPredicates, open2BlockPred));
        this.idToTemplate.put(1, new SimRandomRuleGenerator(closedPredicates, openPredicates, open2BlockPred));
        this.idToTemplate.put(2, new LocalRandomRuleGenerator(closedPredicates, openPredicates, open2BlockPred));
        this.idToTemplate.put(3, new PriorRandomRuleGenerator(closedPredicates, openPredicates, open2BlockPred));
    }

    @Override
    protected void doLearn() {

        for (int i = 0; i < this.numIte; i++) {
            this.populateNextRulesOfModel();
            double metric = this.evaluator.getRepresentativeMetric();
            if (!this.evaluator.isHigherRepresentativeBetter()){
                metric *= -1;
            }
            if (metric > this.bestValueForRulesSoFar && this.mutableRules.size() > 0){
                this.bestValueForRulesSoFar = metric;
                this.bestRulesSoFar.clear();
                this.bestRulesSoFar.addAll(this.allRules);
                log.debug("Got a better model with metric: " + metric);
                checkpointModel(i);
            }
        }

    }

    private void populateNextRulesOfModel() {
        this.resetModel();
        int numRules = RandUtils.nextInt(this.numRules-1)+5;
        for (int i = 0; i < numRules; i++){
            int tries = 0;
            Rule r;
            do {
                RandomRuleGenerator ruleGen = idToTemplate.get(RandUtils.nextInt(idToTemplate.size()));
                r = ruleGen.generateRule(this.maxRuleLen);
                //Ensure at least explainableRatio of eplainable rules.
                if (RandUtils.nextFloat() < explainableRatio && r!=null) {
                    String rStr = r.toString();
                    boolean isExplainable = false;
                    for (String s: this.explainablePredicates.get(true)){
                        if (rStr.contains(s)) {
                            isExplainable = true;
                            break;
                        }
                    }
                    if (!isExplainable) {
                        r = null;
                    }
                }
                tries++;
            }while (tries < NUM_TRIES_PER_RULE_DEFAULT && r == null);
            if(r == null) {
                throw new RuntimeException("Exceeded maximum number of tries for a rule");
            }
            if (this.addRuleToModel(r)) {
                log.info("Added rule: " + r.toString());
            } else {
                log.info("Rule: " + r.toString() + " already exists. Skipping it...");
            }
        }

        WeightLearningApplication newWeightLearner = this.getNewWeightLearner();
        newWeightLearner.learn();
        for(WeightedRule r: this.mutableRules) {
            if(r instanceof WeightedArithmeticRule && r.toString().contains("= 0.0")){
                r.setWeight(0.01);
            }
//            r.setWeight(1);
        }
        this.computeMPEState();
        this.evaluator.compute(trainingMap);
    }


}

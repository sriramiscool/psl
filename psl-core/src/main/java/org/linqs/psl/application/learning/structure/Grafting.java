package org.linqs.psl.application.learning.structure;

import org.linqs.psl.application.learning.weight.maxlikelihood.MaxPiecewisePseudoLikelihood;
import org.linqs.psl.database.Database;
import org.linqs.psl.model.Model;
import org.linqs.psl.model.atom.GroundAtom;
import org.linqs.psl.model.atom.RandomVariableAtom;
import org.linqs.psl.model.rule.GroundRule;
import org.linqs.psl.model.rule.Rule;
import org.linqs.psl.model.rule.WeightedGroundRule;
import org.linqs.psl.model.rule.WeightedRule;
import org.linqs.psl.reasoner.admm.term.ADMMTermStore;
import org.linqs.psl.util.RandUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Created by sriramsrinivasan on 12/6/18.
 */
public class Grafting extends MaxPiecewisePseudoLikelihood{
    private static final Logger log = LoggerFactory.getLogger(Grafting.class);


    public Grafting(List<Rule> rules, Database rvDB, Database observedDB, boolean supportsLatentVariables) {
        super(rules, rvDB, observedDB);
    }

    public Grafting(Model model, Database rvDB, Database observedDB) {
        this(model.getRules(), rvDB, observedDB, false);
    }

    public void doLearn() {
//        for (int i = 0; i < mutableRules.size(); i++) {
//            mutableRules.get(i).setWeight(0);
//        }
//
//        // Computes the observed incompatibilities.
//        computeObservedIncompatibility();
//
//        // Reset the RVAs to default values.
//        setDefaultRandomVariables();
//
//        double[] scalingFactor = computeScalingFactor();
//        double prevRepMetric = 0.0;
//
//        // Computes the gradient steps.
//        for (int step = 0; step < numSteps; step++) {
//            log.debug("Starting iteration {}", step);
//
//            // Computes the expected incompatibility.
//            computeExpectedIncompatibility();
//
//            double norm = 0.0;
//
//            WeightedRule ruleToAdd = null;
//            Set<WeightedRule> blacklist = new HashSet<>();
//            double maxGrad = 1;
//            // Updates weights.
//            for (int i = 0; i < mutableRules.size(); i++) {
//                double currentStep = (expectedIncompatibility[i] - observedIncompatibility[i]
//                        - l1Regularization) / scalingFactor[i];
//                if (mutableRules.get(i).getWeight() != 0) {
//                    double newWeight = mutableRules.get(i).getWeight();
//
//                    currentStep *= baseStepSize;
//
//                    if (clipNegativeWeights) {
//                        newWeight = Math.max(0.0, newWeight + currentStep);
//                    } else {
//                        newWeight = newWeight + currentStep;
//                    }
//
//                    log.trace("Gradient: {} , Expected Incomp.: {}, Observed Incomp.: {} -- ({}) {}",
//                            currentStep,
//                            expectedIncompatibility[i], observedIncompatibility[i],
//                            i, mutableRules.get(i));
//
//                    mutableRules.get(i).setWeight(newWeight);
//                    norm += Math.pow(expectedIncompatibility[i] - observedIncompatibility[i], 2);
//                } else {
//                    if (Math.random() > 0.5){//maxGrad < currentStep && !blacklist.contains(mutableRules.get(i))){
//                        maxGrad = currentStep;
//                        ruleToAdd = mutableRules.get(i);
//                    }
//                }
//            }
//            if (maxGrad <= 0) {
//                log.info("Ending structure learning after {} iterations", step);
//                //break;
//            }
//            ruleToAdd.setWeight(Math.abs(maxGrad));
//            log.debug("Rule added : {} at iteration: {}", ruleToAdd.toString(), step);
//
////            inMPEState = false;
////            inLatentMPEState = false;
////            computeMPEState();
////            this.evaluator.compute(trainingMap);
////            final double representativeMetric = this.evaluator.getRepresentativeMetric();
////            log.info("Metric after adding new rule is : {}", representativeMetric);
////
////            if (this.evaluator.isHigherRepresentativeBetter() && representativeMetric <= prevRepMetric ||
////                    !this.evaluator.isHigherRepresentativeBetter() && representativeMetric>=prevRepMetric){
////                log.info("removing the added rule as it hurts the metric.");
////                ruleToAdd.setWeight(0.0);
////                blacklist.add(ruleToAdd);
////            } else {
////                prevRepMetric = representativeMetric;
////            }
//
//            norm = Math.sqrt(norm);
//
//            if (log.isDebugEnabled()) {
//                getLoss();
//            }
super.doLearn();
            log.debug("Iteration {} complete. Loss: {}. L2-norm: {}", 0, computeLoss(), 0);
            //log.debug("Log-Likelihood: {}. Max grad: {}", getLogLikelihood(), 0);
            if (log.isDebugEnabled()) {
                for (int i = 0; i < this.mutableRules.size(); i++) {
                    if (this.mutableRules.get(i).getWeight() > 0) {
                        log.debug("Model {} ", mutableRules.get(i));
                    }
                }
            }
//        }

        log.debug("Log-Likelihood: {}.", getLogLikelihood());
        List<WeightedRule> removeTheseRules = new ArrayList<>();
        for (int i = 0; i < mutableRules.size(); i++) {
            if (mutableRules.get(i).getWeight() == 0){
                removeTheseRules.add(mutableRules.get(i));
            }
        }
        mutableRules.removeAll(removeTheseRules);
    }
    public double getLogLikelihood(){
        double logLikelihood = 0;
        computeObservedIncompatibility();
        double numerator = 0.0;
        for (int i= 0; i<mutableRules.size();i++){
            numerator += mutableRules.get(i).getWeight() * observedIncompatibility[i];
        }
        numerator *= -1;

        ADMMTermStore admmTermStore = (ADMMTermStore) termStore;
        int numSamples = 50;
        Map<GroundAtom, double[]> samples = new HashMap<>();
        for (Rule mR : mutableRules) {
            for(GroundRule gr : groundRuleStore.getGroundRules(mR)) {
                for(GroundAtom ga : gr.getAtoms()){
                    if (!samples.keySet().contains(ga)) {
                        double [] raSamples = new double[numSamples];
                        samples.put(ga, raSamples);
                        for (int i = 0; i < numSamples; i++) {
                            raSamples[i] = RandUtils.nextDouble();
                        }
                    }
                }
            }
        }

        double partition = 0.0;
        double max = -Double.MAX_VALUE;

        for (int i = 0; i < numSamples; i++) {
            double sum = 0;
            for (Rule mr : mutableRules) {
                for (GroundRule gr : groundRuleStore.getGroundRules(mr)) {
                    for(GroundAtom ga : gr.getAtoms()){
                        if(ga instanceof RandomVariableAtom){
                            ((RandomVariableAtom)ga).setValue((float)samples.get(ga)[i]);
                        }
                    }
                    double inc = ((WeightedRule)mr).getWeight() * ((WeightedGroundRule)gr).getIncompatibility();
                    sum -= inc;
                }
            }
            max = sum > max? sum:max;
        }
        for (int i = 0; i < numSamples; i++) {
            double sum = 0;
            for (Rule mr : mutableRules) {
                for (GroundRule gr : groundRuleStore.getGroundRules(mr)) {
                    for(GroundAtom ga : gr.getAtoms()){
                        if(ga instanceof RandomVariableAtom){
                            ((RandomVariableAtom)ga).setValue((float)samples.get(ga)[i]);
                        }
                    }
                    double inc = ((WeightedRule)mr).getWeight() * ((WeightedGroundRule)gr).getIncompatibility();
                    sum -= inc;
                }
            }
            partition += Math.exp(sum-max);
        }
        partition = max + Math.log(partition) - Math.log(numSamples);
        logLikelihood = numerator - partition;
        return logLikelihood;
    }
}

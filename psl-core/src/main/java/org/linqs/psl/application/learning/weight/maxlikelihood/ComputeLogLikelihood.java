/*
 * This file is part of the PSL software.
 * Copyright 2011-2015 University of Maryland
 * Copyright 2013-2018 The Regents of the University of California
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.linqs.psl.application.learning.weight.maxlikelihood;

import org.linqs.psl.application.learning.weight.VotedPerceptron;
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

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Learns weights by optimizing the log likelihood of the data using
 * the voted perceptron algorithm.
 *
 * The expected total incompatibility is estimated with the total incompatibility
 * in the MPE state.
 *
 * The default implementations in VotedPerceptron are sufficient.
 */
public class ComputeLogLikelihood extends VotedPerceptron {
    public ComputeLogLikelihood(Model model, Database rvDB, Database observedDB) {
        this(model.getRules(), rvDB, observedDB);
    }

    public ComputeLogLikelihood(List<Rule> rules, Database rvDB, Database observedDB) {
        super(rules, rvDB, observedDB, false);
    }

    public double getLogLikelihood(){
        initGroundModel();
        double logLikelihood = 0;
        computeObservedIncompatibility();
        double numerator = 0.0;
        for (int i= 0; i<mutableRules.size();i++){
            numerator += mutableRules.get(i).getWeight() * Math.max(observedIncompatibility[i], 0);
        }
        numerator *= -1;

        ADMMTermStore admmTermStore = (ADMMTermStore) termStore;
        int numSamples = 1000;
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
                    double inc = ((WeightedRule)mr).getWeight() * Math.max(((WeightedGroundRule)gr).getIncompatibility(), 0);
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
                    double inc = ((WeightedRule)mr).getWeight() * Math.max(((WeightedGroundRule)gr).getIncompatibility(), 0);
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

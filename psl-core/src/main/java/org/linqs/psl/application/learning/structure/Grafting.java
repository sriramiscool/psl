package org.linqs.psl.application.learning.structure;

import org.linqs.psl.application.learning.weight.VotedPerceptron;
import org.linqs.psl.database.Database;
import org.linqs.psl.model.Model;
import org.linqs.psl.model.rule.Rule;
import org.linqs.psl.model.rule.WeightedRule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by sriramsrinivasan on 12/6/18.
 */
public class Grafting extends VotedPerceptron{
    private static final Logger log = LoggerFactory.getLogger(Grafting.class);


    public Grafting(List<Rule> rules, Database rvDB, Database observedDB, boolean supportsLatentVariables) {
        super(rules, rvDB, observedDB, supportsLatentVariables);
    }

    public Grafting(Model model, Database rvDB, Database observedDB) {
        this(model.getRules(), rvDB, observedDB, false);
    }

    public void learn() {
        for (int i = 0; i < mutableRules.size(); i++) {
            mutableRules.get(i).setWeight(0);
        }

        // Computes the observed incompatibilities.
        computeObservedIncompatibility();

        // Reset the RVAs to default values.
        setDefaultRandomVariables();

        double[] scalingFactor = computeScalingFactor();

        // Computes the gradient steps.
        for (int step = 0; step < numSteps; step++) {
            log.debug("Starting iteration {}", step);

            // Computes the expected incompatibility.
            computeExpectedIncompatibility();

            double norm = 0.0;

            WeightedRule ruleToAdd = null;
            double maxGrad = Double.MIN_VALUE;
            // Updates weights.
            for (int i = 0; i < mutableRules.size(); i++) {
                double currentStep = (expectedIncompatibility[i] - observedIncompatibility[i]
                        - l1Regularization) / scalingFactor[i];
                if (mutableRules.get(i).getWeight() != 0) {
                    double newWeight = mutableRules.get(i).getWeight();

                    currentStep *= baseStepSize;

                    if (clipNegativeWeights) {
                        newWeight = Math.max(0.0, newWeight + currentStep);
                    } else {
                        newWeight = newWeight + currentStep;
                    }

                    log.trace("Gradient: {} , Expected Incomp.: {}, Observed Incomp.: {} -- ({}) {}",
                            currentStep,
                            expectedIncompatibility[i], observedIncompatibility[i],
                            i, mutableRules.get(i));

                    mutableRules.get(i).setWeight(newWeight);
                    norm += Math.pow(expectedIncompatibility[i] - observedIncompatibility[i], 2);
                } else {
                    if (maxGrad < currentStep){
                        maxGrad = currentStep;
                        ruleToAdd = mutableRules.get(i);
                    }
                }
            }
            if (maxGrad <= 0){
                break;
            }
            ruleToAdd.setWeight(maxGrad);
            log.trace("Rule added : {} at iteration: {}",ruleToAdd.toString(), step);

            inMPEState = false;
            inLatentMPEState = false;

            norm = Math.sqrt(norm);

            if (log.isDebugEnabled()) {
                getLoss();
            }

            log.debug("Iteration {} complete. Likelihood: {}. Icomp. L2-norm: {}", step, computeLoss(), norm);
            log.trace("Model {} ", mutableRules);
        }
        List<WeightedRule> removeTheseRules = new ArrayList<>();
        for (int i = 0; i < mutableRules.size(); i++) {
            if (mutableRules.get(i).getWeight() == 0){
                removeTheseRules.add(mutableRules.get(i));
            }
        }
        mutableRules.removeAll(removeTheseRules);
    }
}

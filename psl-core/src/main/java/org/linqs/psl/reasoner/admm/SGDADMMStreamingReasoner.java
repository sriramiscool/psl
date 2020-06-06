/*
 * This file is part of the PSL software.
 * Copyright 2011-2015 University of Maryland
 * Copyright 2013-2020 The Regents of the University of California
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
package org.linqs.psl.reasoner.admm;

import org.linqs.psl.config.Options;
import org.linqs.psl.reasoner.Reasoner;
import org.linqs.psl.reasoner.admm.term.*;
import org.linqs.psl.reasoner.sgd.term.SGDObjectiveTerm;
import org.linqs.psl.reasoner.term.TermStore;
import org.linqs.psl.reasoner.term.streaming.StreamingCacheIterator;
import org.linqs.psl.util.MathUtils;
import org.linqs.psl.util.Parallel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Iterator;

/**
 * Uses an ADMM optimization method to optimize its GroundRules.
 */
public class SGDADMMStreamingReasoner extends Reasoner {
    private static final Logger log = LoggerFactory.getLogger(SGDADMMStreamingReasoner.class);

    private static final float LOWER_BOUND = 0.0f;
    private static final float UPPER_BOUND = 1.0f;
    public static final int NUM_THREADS = Parallel.getNumThreads();

    private int computePeriod;
    private Iterator<ADMMObjectiveTerm> termIterator;
    private float[][] computeConsensus;
    private int[][] computeConsensusCount;

    /**
     * Sometimes called eta or rho,
     */
    private final float stepSize;

    private int maxIterations;

    private int termBlockSize;
    private int variableBlockSize;
    private int[] varCount;

    public SGDADMMStreamingReasoner() {
        maxIterations = Options.ADMM_MAX_ITER.getInt();
        stepSize = Options.ADMM_STEP_SIZE.getFloat();
        computePeriod = Options.ADMM_COMPUTE_PERIOD.getInt();
    }

    @Override
    public void optimize(TermStore baseTermStore) {
        if (!(baseTermStore instanceof ADMMStreamingTermStore)) {
            throw new IllegalArgumentException("ADMMReasoner requires an ADMMStreamingTermStore (found " + baseTermStore.getClass().getName() + ").");
        }
        ADMMStreamingTermStore termStore = (ADMMStreamingTermStore)baseTermStore;

        termStore.initForOptimization();

        ObjectiveResult objective = null;
        ObjectiveResult oldObjective = null;

        if (log.isTraceEnabled()) {
            objective = computeObjective(termStore, false);
            log.trace(
                    "Iteration {} -- Objective: {}, Feasible: {}.",
                    0, objective.objective, (objective.violatedConstraints == 0));
        }

        int iteration = 1;
        long timeSoFar = 0;
        while (true) {
            if (iteration <= 0){
                int[] variableIndexes = new int[5];
                printVars(termStore.getVariableValues(), termStore.getNumVariables());
                for (ADMMObjectiveTerm term: termStore){
                    if (term instanceof LinearConstraintTerm) {
                        continue;
                    }
                    if (term.size() > variableIndexes.length) {
                        variableIndexes = new int[term.size()];
                    }
                    for (int i = 0 ; i < term.size() ; i++){
                        variableIndexes[i] = term.getVariables()[i].getGlobalId();
                    }
                    boolean squared = (term instanceof SquaredHingeLossTerm || term instanceof SquaredLinearLossTerm);
                    boolean hinge = (term instanceof HingeLossTerm || term instanceof SquaredHingeLossTerm);
                    SGDObjectiveTerm sgdTerm = new SGDObjectiveTerm(term.size(), term.getCoefficients(), term.getConstant(),
                            variableIndexes, squared, hinge, termStore.getWeight(term.getRuleIndex()), 0.1f);
                    sgdTerm.minimize(1, termStore.getVariableValues());
                    //System.out.println(term);
                }
                printVars(termStore.getVariableValues(), termStore.getNumVariables());
            } else {
                //Create once, Assuming termstore has finished first round so numbers are proper.
                if (varCount == null) {
                    varCount = new int[termStore.getNumVariables()];
                    computeConsensus = new float[NUM_THREADS][termStore.getNumVariables()];
                    computeConsensusCount = new int[NUM_THREADS][termStore.getNumVariables()];
                }
                printVars(termStore.getVariableValues(), termStore.getNumVariables());
                long start = System.currentTimeMillis();
                termIterator = termStore.iterator();

                // Minimize all the terms.
                Parallel.count(NUM_THREADS,
                        new TermWorker(termStore, termIterator));
                long end = System.currentTimeMillis();
                updateConsensusVariables(termStore);
                printVars(termStore.getVariableValues(), termStore.getNumVariables());
                timeSoFar += end - start;
            }

                oldObjective = objective;
                objective = computeObjective(termStore, false);
                if (iteration % computePeriod == 0) {
                        log.trace(
                                "Iteration {} -- Objective: {}, Old objective: {}, Feasible: {}, Time: {}, Vars: {}.",
                                iteration, objective.objective, (oldObjective==null? "NAN":oldObjective.objective),
                                (objective.violatedConstraints == 0), timeSoFar, objective.numTerms);

                }
            termStore.iterationComplete();

            iteration++;

            if (breakOptimization(iteration, objective, oldObjective)) {
                    break;
            }
        }
        log.info("Optimization completed in {} iterations. Objective: {}, Feasible: {}",
                iteration - 1, objective.objective, (objective.violatedConstraints == 0));

        if (objective.violatedConstraints > 0) {
            log.warn("No feasible solution found. {} constraints violated.", objective.violatedConstraints);
            computeObjective(termStore, true);
        }

        // Sync the consensus values back to the atoms.
        termStore.syncAtoms();
    }

    private void printVars(float[] variables, int size) {
        String p = "Variables: ";
        for (int i = 0; i < size; i++) {
            p += ", " + variables[i];
        }
        System.out.println(p);
    }

    private void updateConsensusVariables(ADMMStreamingTermStore termStore) {
        float[] variableValues = termStore.getVariableValues();
        for (int i = 0; i < termStore.getNumVariables(); i++) {
            variableValues[i] = 0;
            varCount[i] = 0;
            for (int j = 0; j < NUM_THREADS; j++) {
                variableValues[i] += computeConsensus[j][i];
                varCount[i] += computeConsensusCount[j][i];
                computeConsensus[j][i] = 0;
                computeConsensusCount[j][i] = 0;
            }
            variableValues[i] = variableValues[i]/ varCount[i];
            variableValues[i] = Math.max(Math.min(variableValues[i], 1f), 0f);
        }
        termStore.syncAtoms();
    }

    private boolean breakOptimization(int iteration, ObjectiveResult objective, ObjectiveResult oldObjective) {
        // Always break when the allocated iterations is up.
        if (iteration > (int)(maxIterations * budget)) {
            return true;
        }

        // Don't break if there are violated constraints.
        if (objective != null && objective.violatedConstraints > 0) {
            return false;
        }

        // Break if we have converged.
//        if (iteration > 1 && primalRes < epsilonPrimal && dualRes < epsilonDual) {
//            return true;
//        }

        // Break if the objective has not changed.
        if (objectiveBreak && oldObjective != null && MathUtils.equals(objective.objective, oldObjective.objective, tolerance)) {
            return true;
        }

        return false;
    }

    @Override
    public void close() {
    }

    private ObjectiveResult computeObjective(ADMMStreamingTermStore termStore, boolean logViolatedConstraints) {
        float objective = 0.0f;
        int violatedConstraints = 0;
        int numTerms = 0;

        for (ADMMObjectiveTerm term : termStore) {
            numTerms++;
            if (term instanceof LinearConstraintTerm) {
                if (term.evaluate(termStore.getVariableValues(), termStore) > 0.0f) {
                    violatedConstraints++;

//                    if (logViolatedConstraints) {
//                        log.trace("    {}", term.getGroundRule());
//                    }
                }
            } else {
                objective += term.evaluate(termStore.getVariableValues(), termStore);
            }
        }

        return new ObjectiveResult(objective, violatedConstraints, numTerms);
    }

    private class TermWorker extends Parallel.Worker<Integer> {
        private final ADMMStreamingTermStore termStore;
        private StreamingCacheIterator<ADMMObjectiveTerm, LocalVariable> termIterator;

        public TermWorker(ADMMStreamingTermStore termStore,
                          Iterator<ADMMObjectiveTerm> termIterator) {
            super();

            this.termStore = termStore;
            this.termIterator = (StreamingCacheIterator<ADMMObjectiveTerm, LocalVariable>) termIterator;
        }

        public Object clone() {
            return new TermWorker(termStore, termIterator);
        }

        @Override
        public void work(int blockIndex, Integer ignore) {
            //This is synchronized hasNext and Next combined.
            ADMMObjectiveTerm term = termIterator.getNext();
            while (term != null){
                //System.out.println(term);
                term.updateLagrange(stepSize, termStore.getVariableValues());
                term.minimize(stepSize, termStore.getVariableValues(), termStore);
                //System.out.println(term);
                for (int i = 0; i < term.size(); i++) {
                    computeConsensusCount[blockIndex][term.getVariables()[i].getGlobalId()]++;
                    computeConsensus[blockIndex][term.getVariables()[i].getGlobalId()] += term.getVariables()[i].getValue();
                }
                term = termIterator.getNext();
            }
        }
    }


    private static class ObjectiveResult {
        public final float objective;
        public final int violatedConstraints;
        public final int numTerms;

        public ObjectiveResult(float objective, int violatedConstraints, int numTerms) {
            this.objective = objective;
            this.violatedConstraints = violatedConstraints;
            this.numTerms = numTerms;
        }
    }
}

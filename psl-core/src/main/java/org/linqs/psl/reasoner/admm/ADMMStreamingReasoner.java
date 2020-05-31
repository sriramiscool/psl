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
import org.linqs.psl.reasoner.admm.term.ADMMObjectiveTerm;
import org.linqs.psl.reasoner.admm.term.LinearConstraintTerm;
import org.linqs.psl.reasoner.admm.term.ADMMStreamingTermStore;
import org.linqs.psl.reasoner.term.TermStore;
import org.linqs.psl.util.MathUtils;
import org.linqs.psl.util.Parallel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Iterator;

/**
 * Uses an ADMM optimization method to optimize its GroundRules.
 */
public class ADMMStreamingReasoner extends Reasoner {
    private static final Logger log = LoggerFactory.getLogger(ADMMStreamingReasoner.class);

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

//    private float epsilonRel;
//    private float epsilonAbs;
//
//    private float primalRes;
//    private float epsilonPrimal;
//    private float dualRes;
//    private float epsilonDual;
//
//    private float AxNorm;
//    private float AyNorm;
//    private float BzNorm;
//    private float lagrangePenalty;
//    private float augmentedLagrangePenalty;

    private int maxIterations;

    private int termBlockSize;
    private int variableBlockSize;
    private int[] varCount;

    public ADMMStreamingReasoner() {
        maxIterations = Options.ADMM_MAX_ITER.getInt();
        stepSize = Options.ADMM_STEP_SIZE.getFloat();
        computePeriod = Options.ADMM_COMPUTE_PERIOD.getInt();
//        epsilonAbs = Options.ADMM_EPSILON_ABS.getFloat();
//        epsilonRel = Options.ADMM_EPSILON_REL.getFloat();
    }

//    public float getEpsilonRel() {
//        return epsilonRel;
//    }
//
//    public void setEpsilonRel(float epsilonRel) {
//        this.epsilonRel = epsilonRel;
//    }
//
//    public float getEpsilonAbs() {
//        return epsilonAbs;
//    }
//
//    public void setEpsilonAbs(float epsilonAbs) {
//        this.epsilonAbs = epsilonAbs;
//    }
//
//    public float getLagrangianPenalty() {
//        return this.lagrangePenalty;
//    }
//
//    public float getAugmentedLagrangianPenalty() {
//        return this.augmentedLagrangePenalty;
//    }

    @Override
    public void optimize(TermStore baseTermStore) {
        if (!(baseTermStore instanceof ADMMStreamingTermStore)) {
            throw new IllegalArgumentException("ADMMReasoner requires an ADMMStreamingTermStore (found " + baseTermStore.getClass().getName() + ").");
        }
        ADMMStreamingTermStore termStore = (ADMMStreamingTermStore)baseTermStore;

        termStore.initForOptimization();
        //Create once
        varCount = new int[termStore.getNumVariables()];

//        int numTerms = termStore.size();
//        int numVariables = termStore.getNumVariables();

//        log.debug("Performing optimization with {} variables and {} terms.", numVariables, numTerms);

//        termBlockSize = numTerms / (Parallel.getNumThreads() * 4) + 1;
//        variableBlockSize = numVariables / (Parallel.getNumThreads() * 4) + 1;

//        int numTermBlocks = (int)Math.ceil(numTerms / (float)termBlockSize);
//        int numVariableBlocks = (int)Math.ceil(numVariables / (float)variableBlockSize);

        // Performs inference.
//        float epsilonAbsTerm = (float)(Math.sqrt(termStore.getNumLocalVariables()) * epsilonAbs);

        ObjectiveResult objective = null;
        ObjectiveResult oldObjective = null;

        if (log.isTraceEnabled()) {
            objective = computeObjective(termStore, false);
            log.trace(
                    "Iteration {} -- Objective: {}, Feasible: {}.",
                    0, objective.objective, (objective.violatedConstraints == 0));
        }
        computeConsensus = new float[NUM_THREADS][termStore.getNumVariables()];
        computeConsensusCount = new int[NUM_THREADS][termStore.getNumVariables()];

        int iteration = 1;
        while (true) {
            if (iteration == 0){
//                long start = System.currentTimeMillis();
//
//                // Keep track of the mean movement of the random variables.
//                float movement = 0.0f;
//
//                float[] variableValues = termStore.getVariableValues();
//                for (SGDObjectiveTerm term : termStore) {
//                    movement += term.minimize(iteration, variableValues);
//                }
//
//                if (variableValues.length != 0) {
//                    movement /= variableValues.length;
//                }
//
//                long end = System.currentTimeMillis();
            } else {

                // Zero out the iteration variables.
//                primalRes = 0.0f;
//                dualRes = 0.0f;
//                AxNorm = 0.0f;
//                AyNorm = 0.0f;
//                BzNorm = 0.0f;
//                lagrangePenalty = 0.0f;
//                augmentedLagrangePenalty = 0.0f;
                long start = System.currentTimeMillis();
                termIterator = termStore.iterator();

                // Minimize all the terms.
                Parallel.count(NUM_THREADS,
                        new TermWorker(termStore, termIterator, computeConsensus, computeConsensusCount));
                long end = System.currentTimeMillis();
                updateConsensusVariables(termStore);

                // Compute new consensus values and residuals.
//                Parallel.count(numVariableBlocks, new VariableWorker(termStore, variableBlockSize));

//                primalRes = (float) Math.sqrt(primalRes);
//                dualRes = (float) (stepSize * Math.sqrt(dualRes));
//
//                epsilonPrimal = (float) (epsilonAbsTerm + epsilonRel * Math.max(Math.sqrt(AxNorm), Math.sqrt(BzNorm)));
//                epsilonDual = (float) (epsilonAbsTerm + epsilonRel * Math.sqrt(AyNorm));

                oldObjective = objective;
                objective = computeObjective(termStore, false);
                if (iteration % computePeriod == 0) {
                    if (!objectiveBreak) {
//                        log.trace(
//                                "Iteration {} -- Primal: {}, Dual: {}, Epsilon Primal: {}, Epsilon Dual: {}.",
//                                iteration, primalRes, dualRes, epsilonPrimal, epsilonDual);
                    } else {
//
//                        log.trace(
//                                "Iteration {} -- Objective: {}, Feasible: {}, Primal: {}, Dual: {}, Epsilon Primal: {}, Epsilon Dual: {}.",
//                                iteration, objective.objective, (objective.violatedConstraints == 0),
//                                primalRes, dualRes, epsilonPrimal, epsilonDual);

                        log.trace(
                                "Iteration {} -- Objective: {}, Feasible: {}, Time: {}.",
                                iteration, objective.objective, (objective.violatedConstraints == 0), (end-start));
                    }

                }
            }
            termStore.iterationComplete();

            iteration++;

            if (breakOptimization(iteration, objective, oldObjective)) {
//                // Before we break, compute the objective so we can look for violated constraints.
//                if (objective == null) {
//                    objective = computeObjective(termStore, false);
//                }

                // Check one more time if we should actually break.
//                if (breakOptimization(iteration, objective, oldObjective)) {
                    break;
//                }
            }
        }

//        log.info("Optimization completed in {} iterations. Objective: {}, Feasible: {}, Primal res.: {}, Dual res.: {}",
//                iteration - 1, objective.objective, (objective.violatedConstraints == 0), primalRes, dualRes);
        log.info("Optimization completed in {} iterations. Objective: {}, Feasible: {}",
                iteration - 1, objective.objective, (objective.violatedConstraints == 0));

        if (objective.violatedConstraints > 0) {
            log.warn("No feasible solution found. {} constraints violated.", objective.violatedConstraints);
            computeObjective(termStore, true);
        }

        // Sync the consensus values back to the atoms.
        termStore.syncAtoms();
    }

    private void updateConsensusVariables(ADMMStreamingTermStore termStore) {
        float[] variableValues = termStore.getVariableValues();
        for (int i = 0; i < variableValues.length; i++) {
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
        float[] consensusValues = termStore.getVariableValues();

        for (ADMMObjectiveTerm term : termStore) {
            if (term instanceof LinearConstraintTerm) {
                if (term.evaluate(consensusValues, termStore) > 0.0f) {
                    violatedConstraints++;

//                    if (logViolatedConstraints) {
//                        log.trace("    {}", term.getGroundRule());
//                    }
                }
            } else {
                objective += term.evaluate(consensusValues, termStore);
            }
        }

        return new ObjectiveResult(objective, violatedConstraints);
    }

//    private synchronized void updateIterationVariables(
//            float primalRes, float dualRes,
//            float AxNorm, float BzNorm, float AyNorm,
//            float lagrangePenalty, float augmentedLagrangePenalty) {
//        this.primalRes += primalRes;
//        this.dualRes += dualRes;
//        this.AxNorm += AxNorm;
//        this.AyNorm += AyNorm;
//        this.BzNorm += BzNorm;
//        this.lagrangePenalty += lagrangePenalty;
//        this.augmentedLagrangePenalty += augmentedLagrangePenalty;
//    }

    private class TermWorker extends Parallel.Worker<Integer> {
        private final ADMMStreamingTermStore termStore;
//        private final int blockSize;
        private Iterator<ADMMObjectiveTerm> termIterator;
        private final float[] consensusValues;
        private float[][] newConsensusVal;
        private int[][] newConsensusValCount;

        public TermWorker(ADMMStreamingTermStore termStore,
                          Iterator<ADMMObjectiveTerm> termIterator,
                          float[][] ncv, int[][] ncc) {
            super();

            this.termStore = termStore;
            this.termIterator = termIterator;
            this.consensusValues = termStore.getVariableValues();
            this.newConsensusVal = ncv;
            this.newConsensusValCount = ncc;
        }

        public Object clone() {
            return new TermWorker(termStore, termIterator, newConsensusVal, newConsensusValCount);
        }

        @Override
        public void work(int blockIndex, Integer ignore) {
            int numTerms = termStore.size();

            // Minimize each local function (wrt the local variable copies).
            ADMMObjectiveTerm term = termIterator.next();
            while (term != null){
                term.updateLagrange(stepSize, consensusValues);
                term.minimize(stepSize, consensusValues, termStore);
                for (int i = 0; i < term.size(); i++) {
                    newConsensusValCount[blockIndex][term.getVariables()[i].getGlobalId()]++;
                    newConsensusVal[blockIndex][term.getVariables()[i].getGlobalId()] += term.getVariables()[i].getValue();
                }
            }
        }
    }

//    private class VariableWorker extends Parallel.Worker<Integer> {
//        private final ADMMStreamingTermStore termStore;
//        private final int blockSize;
//        private final float[] consensusValues;
//
//        public VariableWorker(ADMMStreamingTermStore termStore, int blockSize) {
//            super();
//
//            this.termStore = termStore;
//            this.blockSize = blockSize;
//            this.consensusValues = termStore.getConsensusValues();
//        }
//
//        public Object clone() {
//            return new VariableWorker(termStore, blockSize);
//        }
//
//        @Override
//        public void work(int blockIndex, Integer ignore) {
//            int numVariables = termStore.getNumConsensusVariables();
//
//            float primalResInc = 0.0f;
//            float dualResInc = 0.0f;
//            float AxNormInc = 0.0f;
//            float BzNormInc = 0.0f;
//            float AyNormInc = 0.0f;
//            float lagrangePenaltyInc = 0.0f;
//            float augmentedLagrangePenaltyInc = 0.0f;
//
//            // Instead of dividing up the work ahead of time,
//            // get one job at a time so the threads will have more even workloads.
//            for (int innerBlockIndex = 0; innerBlockIndex < blockSize; innerBlockIndex++) {
//                int variableIndex = blockIndex * blockSize + innerBlockIndex;
//
//                if (variableIndex >= numVariables) {
//                    break;
//                }
//
//                float total = 0.0f;
//                int numLocalVariables = termStore.getLocalVariables(variableIndex).size();
//
//                // First pass computes newConsensusValue and dual residual fom all local copies.
//                for (int localVarIndex = 0; localVarIndex < numLocalVariables; localVarIndex++) {
//                    LocalVariable localVariable = termStore.getLocalVariables(variableIndex).get(localVarIndex);
//                    total += localVariable.getValue() + localVariable.getLagrange() / stepSize;
//
//                    AxNormInc += localVariable.getValue() * localVariable.getValue();
//                    AyNormInc += localVariable.getLagrange() * localVariable.getLagrange();
//                }
//
//                float newConsensusValue = total / numLocalVariables;
//                newConsensusValue = Math.max(Math.min(newConsensusValue, UPPER_BOUND), LOWER_BOUND);
//
//                float diff = consensusValues[variableIndex] - newConsensusValue;
//                // Residual is diff^2 * number of local variables mapped to consensusValues element.
//                dualResInc += diff * diff * numLocalVariables;
//                BzNormInc += newConsensusValue * newConsensusValue * numLocalVariables;
//
//                consensusValues[variableIndex] = newConsensusValue;
//
//                // Second pass computes primal residuals.
//
//                for (int localVarIndex = 0; localVarIndex < numLocalVariables; localVarIndex++) {
//                    LocalVariable localVariable = termStore.getLocalVariables(variableIndex).get(localVarIndex);
//
//                    diff = localVariable.getValue() - newConsensusValue;
//                    primalResInc += diff * diff;
//
//                    // compute Lagrangian penalties
//                    lagrangePenaltyInc += localVariable.getLagrange() * (localVariable.getValue() - consensusValues[variableIndex]);
//                    augmentedLagrangePenaltyInc += 0.5 * stepSize * Math.pow(localVariable.getValue() - consensusValues[variableIndex], 2);
//                }
//            }
//
//            updateIterationVariables(primalResInc, dualResInc, AxNormInc, BzNormInc, AyNormInc, lagrangePenaltyInc, augmentedLagrangePenaltyInc);
//        }
//    }

    private static class ObjectiveResult {
        public final float objective;
        public final int violatedConstraints;

        public ObjectiveResult(float objective, int violatedConstraints) {
            this.objective = objective;
            this.violatedConstraints = violatedConstraints;
        }
    }
}

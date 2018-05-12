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
package org.linqs.psl.application.learning.weight;

import org.linqs.psl.config.ConfigBundle;
import org.linqs.psl.database.Database;
import org.linqs.psl.evaluation.statistics.ContinuousEvaluator;
import org.linqs.psl.evaluation.statistics.Evaluator;
import org.linqs.psl.model.rule.Rule;
import org.linqs.psl.model.rule.WeightedRule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * TODO(steve): rewrite class documentation to describe general gradient-based learning algorithms
 *
 * Learns new weights for the weighted rules in a model using the voted perceptron algorithm.
 *
 * The weight-learning objective is to maximize the likelihood according to the distribution:
 *
 * p(X) = 1 / Z(w) * exp{-sum[w * f(X)]}
 *
 * Where:
 *  - X is the set of RandomVariableAtoms,
 *  - f(X) the incompatibility of each GroundRule,
 *  - w is the weight of that GroundRule,
 *  - and Z(w) is a normalization factor.
 *
 * The voted perceptron algorithm starts at the current weights and at each step
 * computes the gradient of the objective, takes that step multiplied by a step size
 * (possibly truncated to stay in the region of feasible weights), and
 * saves the new weights. The components of the gradient are each divided by the
 * number of weighted ground rules from that Rule. The learned weights
 * are the averages of the saved weights.
 *
 * For the gradient of the objective, the expected total incompatibility is
 * computed by subclasses in {@link #computeExpectedIncompatibility()}.
 *
 * Reasonable initial implementations are provided for all methods.
 * Child classes should be able to pick and chose which to override.
 */
public abstract class VotedPerceptron extends WeightLearningApplication {
	private static final Logger log = LoggerFactory.getLogger(VotedPerceptron.class);

	/**
	 * Prefix of property keys used by this class.
	 */
	public static final String CONFIG_PREFIX = "votedperceptron";

	/**
	 * Key for positive double property scaling the L2 regularization
	 * (\lambda / 2) * ||w||^2
	 */
	public static final String L2_REGULARIZATION_KEY = CONFIG_PREFIX + ".l2regularization";
	public static final double L2_REGULARIZATION_DEFAULT = 0.0;

	/**
	 * Key for positive double property scaling the L1 regularization
	 * \gamma * |w|
	 */
	public static final String L1_REGULARIZATION_KEY = CONFIG_PREFIX + ".l1regularization";
	public static final double L1_REGULARIZATION_DEFAULT = 0.0;

	/**
	 * Key for positive double property which will be multiplied with the
	 * objective gradient to compute a step.
	 */
	public static final String STEP_SIZE_KEY = CONFIG_PREFIX + ".stepsize";
	public static final double STEP_SIZE_DEFAULT = 1;

	/**
	 * The inertia that is used for adaptive step sizes.
	 * Should be in (0, 1).
	 */
	public static final String INERTIA_KEY = CONFIG_PREFIX + ".inertia";
	public static final double INERTIA_DEFAULT = 0.0;//0.50;

	/**
	 * Key for Boolean property that indicates whether to scale gradient by
	 * number of groundings
	 */
	public static final String SCALE_GRADIENT_KEY = CONFIG_PREFIX + ".scalegradient";
	public static final boolean SCALE_GRADIENT_DEFAULT = true;

	/**
	 * Key for Boolean property that indicates whether to average all visited
	 * weights together for final output.
	 */
	public static final String AVERAGE_STEPS_KEY = CONFIG_PREFIX + ".averagesteps";
	public static final boolean AVERAGE_STEPS_DEFAULT = true;//true;

	/**
	 * Key for positive integer property. VotedPerceptron will take this many
	 * steps to learn weights.
	 */
	public static final String NUM_STEPS_KEY = CONFIG_PREFIX + ".numsteps";
	public static final int NUM_STEPS_DEFAULT = 25;

	/**
	 * The evaluation method to get stats for each iteration.
	 * This is only used for logging/information, and not for gradients.
	 */
	public static final String EVALUATOR_KEY = CONFIG_PREFIX + ".evaluator";
	public static final String EVALUATOR_DEFAULT = ContinuousEvaluator.class.getName();

	protected final double baseStepSize;
	protected final double l2Regularization;
	protected final double l1Regularization;
	protected final boolean scaleGradient;

	protected boolean averageSteps;
	protected double inertia;
	protected final int maxNumSteps;
	protected int numSteps;

	private Evaluator evaluator;

	/**
	 * Learning loss at the current point
	 */
	private double currentLoss;

	public VotedPerceptron(List<Rule> rules, Database rvDB, Database observedDB,
			boolean supportsLatentVariables, ConfigBundle config) {
		super(rules, rvDB, observedDB, supportsLatentVariables, config);

		baseStepSize = config.getDouble(STEP_SIZE_KEY, STEP_SIZE_DEFAULT);
		if (baseStepSize <= 0) {
			throw new IllegalArgumentException("Step size must be positive.");
		}

		inertia = config.getDouble(INERTIA_KEY, INERTIA_DEFAULT);
		if (inertia < 0 || inertia >= 1) {
			throw new IllegalArgumentException("Inertia must be in [0, 1), found: " + inertia);
		}

		numSteps = config.getInt(NUM_STEPS_KEY, NUM_STEPS_DEFAULT);
		maxNumSteps = numSteps;
		if (numSteps <= 0) {
			throw new IllegalArgumentException("Number of steps must be positive.");
		}

		l2Regularization = config.getDouble(L2_REGULARIZATION_KEY, L2_REGULARIZATION_DEFAULT);
		if (l2Regularization < 0) {
			throw new IllegalArgumentException("L2 regularization parameter must be non-negative.");
		}

		l1Regularization = config.getDouble(L1_REGULARIZATION_KEY, L1_REGULARIZATION_DEFAULT);
		if (l1Regularization < 0) {
			throw new IllegalArgumentException("L1 regularization parameter must be non-negative.");
		}

	evaluator = (Evaluator)config.getNewObject(EVALUATOR_KEY, EVALUATOR_DEFAULT);

	scaleGradient = config.getBoolean(SCALE_GRADIENT_KEY, SCALE_GRADIENT_DEFAULT);
	averageSteps = config.getBoolean(AVERAGE_STEPS_KEY, AVERAGE_STEPS_DEFAULT);

	currentLoss = Double.NaN;
}


	@Override
	protected void doLearn() {
		double[] avgWeights = new double[mutableRules.size()];

		// Reset the RVAs to default values.
		setDefaultRandomVariables();
		computeObservedIncompatibility();

		double[] scalingFactor = computeScalingFactor();
		double sumOfWeights = 0;

		// Keep track of the last steps for each weight so we can apply momentum.
		double[] lastSteps = new double[mutableRules.size()];
		double previousLoss = 0;

		// Computes the gradient steps.
		for (int step = 0; step < numSteps; step++) {
			log.info("Starting iteration {}", step);
			boolean stopUpdate = true;
			double diffExp = 0;
			// Computes the expected incompatibility.
			computeExpectedIncompatibility();
			sumOfWeights = 0;
			for (int i = 0; i < mutableRules.size(); i++) {
				currentLoss = Double.NaN;


				double newWeight = mutableRules.get(i).getWeight();//
				double currentStep = (expectedIncompatibility[i] - observedIncompatibility[i]
						- l2Regularization * newWeight
						- l1Regularization) / scalingFactor[i];

				currentStep *= baseStepSize/((step+1));

				// Apply momentum.
				currentStep += inertia * lastSteps[i];

				// TEST
				// newWeight = newWeight + currentStep;
				newWeight = Math.max(0, newWeight + currentStep);
				sumOfWeights += newWeight;
				diffExp += (expectedIncompatibility[i] - observedIncompatibility[i]) * (expectedIncompatibility[i] - observedIncompatibility[i])/((scalingFactor[i])*(scalingFactor[i]));///scalingFactor[i];

				log.trace("Gradient: {} (without momentun: {}), Expected Incomp.: {}, Observed Incomp.: {} -- ({}) {}",
						currentStep, currentStep - (inertia * lastSteps[i]),
						expectedIncompatibility[i], observedIncompatibility[i],
						i, mutableRules.get(i));

				mutableRules.get(i).setWeight(newWeight);
				//if (Math.abs(expectedIncompatibility[i] - observedIncompatibility[i]) > 0.01) {
					inMPEState = false;
					inLatentMPEState = false;
				//}

				lastSteps[i] = currentStep;
				avgWeights[i] += newWeight;
			}



			if (log.isDebugEnabled()) {
				getLoss();
			}

			double objective = -1.0;
			if (log.isDebugEnabled() && evaluator != null) {
				// Compute the MPE state before evaluating so variables have assigned values.
				computeMPEState();

				evaluator.compute(trainingMap);
				objective = evaluator.getRepresentativeMetric();
				objective = evaluator.isHigherRepresentativeBetter() ? -1.0 * objective : objective;
			}
			currentLoss = getLoss();

			log.info("Iteration {} complete. Neg Gradient: {}. Metric: {}. Diff incompat: {}", step, currentLoss, objective, Math.sqrt(diffExp));
			log.trace("Model {} ", mutableRules);
			previousLoss = currentLoss;

		}
		// Sets the weights to their averages.
		if (averageSteps) {
			sumOfWeights = 0;
			for (int i = 0; i < mutableRules.size(); i++) {
				log.info("{}", mutableRules.get(i));
				mutableRules.get(i).setWeight(avgWeights[i] / numSteps);
				sumOfWeights += avgWeights[i] / numSteps;
			}
		}
		//for (int i = 0; i < mutableRules.size(); i++) {
		//	mutableRules.get(i).setWeight(mutableRules.get(i).getWeight()/sumOfWeights);
		//}
	}


//	@Override
//	protected void doLearn() {
//		double[] avgWeights = new double[mutableRules.size()];
//		int[] countAvg = new int[mutableRules.size()];
//
//		int[] countIncompatPrev = new int[mutableRules.size()];
//
//		// Computes the observed incompatibilities.
//		computeObservedIncompatibility();
//		Map<WeightedGroundRule, Double> ruleToObsIncompat = new HashMap<>();
//		for (int i = 0; i < mutableRules.size(); i++) {
//			for (GroundRule groundRule : groundRuleStore.getGroundRules(mutableRules.get(i))) {
//				ruleToObsIncompat.put(((WeightedGroundRule)groundRule),
//						((WeightedGroundRule)groundRule).getIncompatibility());
//			}
//		}
//
//		// Reset the RVAs to default values.
//		setDefaultRandomVariables();
//
//		double[] scalingFactor = computeScalingFactor();
//
//		// Keep track of the last steps for each weight so we can apply momentum.
//		double[] lastSteps = new double[mutableRules.size()];
//		double previousLoss = 0;
//
//		// Computes the gradient steps.
//		for (int step = 0; step < numSteps; step++) {
//			boolean stopUpdate = true;
//			for (int i = 0; i < mutableRules.size(); i++) {
//				log.info("Starting iteration {}", step);
//				int countIncompat = 0;
//				for (GroundRule groundRule : groundRuleStore.getGroundRules(mutableRules.get(i))) {
//					currentLoss = Double.NaN;
//
//					// Computes the expected incompatibility.
//					computeExpectedIncompatibility();
//
//					double newWeight, currentStep;
//					// Updates weights.
//					if (((WeightedGroundRule)groundRule).getIncompatibility() != ruleToObsIncompat.get(groundRule)) {
//						countIncompat++;
//						newWeight = mutableRules.get(i).getWeight();//
//						currentStep = (((WeightedGroundRule)groundRule).getIncompatibility() - ruleToObsIncompat.get(groundRule)//(expectedIncompatibility[i] - observedIncompatibility[i]
//								- l2Regularization * newWeight
//								- l1Regularization) / scalingFactor[i];
//
//						currentStep *= baseStepSize/(step+1);
//
//						// Apply momentum.
//						currentStep += inertia * lastSteps[i];
//
//						// TEST
//						// newWeight = newWeight + currentStep;
//						newWeight = Math.max(0.0, newWeight + currentStep);
//
//						log.info("Gradient: {} (without momentun: {}), Expected Incomp.: {}, Observed Incomp.: {} -- ({}) {}",
//								currentStep, currentStep - (inertia * lastSteps[i]),
//								expectedIncompatibility[i], observedIncompatibility[i],
//								i, mutableRules.get(i));
//
//						mutableRules.get(i).setWeight(newWeight);
//						if (Math.abs(expectedIncompatibility[i] - observedIncompatibility[i]) > 0.01) {
//							inMPEState = false;
//							inLatentMPEState = false;
//						}
//					} else {
//						newWeight =  mutableRules.get(i).getWeight();
//						currentStep = 0;
//					}
//					lastSteps[i] = currentStep;
//					avgWeights[i] += newWeight;
//					countAvg[i]++;
//				}
//
//				log.info("Expected Incomp.: {}, Observed Incomp.: {} -- ({}) {}, Prev count incompat: {}, current incompat = {} ",
//						expectedIncompatibility[i], observedIncompatibility[i],
//						i, mutableRules.get(i), countIncompatPrev[i], countIncompat);
//				stopUpdate  = stopUpdate && (countIncompatPrev[i] == countIncompat);
//				countIncompatPrev[i] = countIncompat;
//			}
//
//
//			if (log.isDebugEnabled()) {
//				getLoss();
//			}
//
//			double objective = -1.0;
//			if (log.isDebugEnabled() && evaluator != null) {
//				// Compute the MPE state before evaluating so variables have assigned values.
//				computeMPEState();
//
//				evaluator.compute(trainingMap);
//				objective = evaluator.getRepresentativeMetric();
//				objective = evaluator.isHigherRepresentativeBetter() ? -1.0 * objective : objective;
//			}
//			currentLoss = getLoss();
//
//			log.info("Iteration {} complete. Neg Gradient: {}. Metric: {}", step, currentLoss, objective);
//			log.trace("Model {} ", mutableRules);
//			previousLoss = currentLoss;
//
//			//if (stopUpdate) {
//			//	log.info("Stopping as the number of ground rules not compatible is constant for all rules.");
//			//	break;
//			//}
//		}
//
//		// Sets the weights to their averages.
//		if (averageSteps) {
//			for (int i = 0; i < mutableRules.size(); i++) {
//				mutableRules.get(i).setWeight(avgWeights[i] / countAvg[i]);
//			}
//		}
//	}

	protected double computeRegularizer() {
		if (l1Regularization == 0.0 && l2Regularization == 0.0) {
			return 0.0;
		}

		double l2 = 0.0;
		double l1 = 0.0;

		for (WeightedRule rule : mutableRules) {
			l2 += Math.pow(rule.getWeight(), 2);
			l1 += Math.abs(rule.getWeight());
		}

		return 0.5 * l2Regularization * l2 + l1Regularization * l1;
	}

	public double getLoss() {
		if (Double.isNaN(currentLoss)) {
			currentLoss = computeLoss();
		}

		return currentLoss;
	}

	/**
	 * Computes the amount to scale gradient for each rule.
	 * Scales by the number of groundings of each rule
	 * unless the rule is not grounded in the training set, in which case
	 * scales by 1.0.
	 */
	protected double[] computeScalingFactor() {
		double [] factor = new double[mutableRules.size()];
		for (int i = 0; i < factor.length; i++) {
			factor[i] = Math.max(1.0, groundRuleStore.count(mutableRules.get(i)));
		}

		return factor;
	}

	@Override
	public void setBudget(double budget) {
		super.setBudget(budget);

		numSteps = (int)Math.ceil(budget * maxNumSteps);
	}
}

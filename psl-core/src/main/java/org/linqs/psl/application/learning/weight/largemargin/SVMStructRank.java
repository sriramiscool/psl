package org.linqs.psl.application.learning.weight.largemargin;

import com.google.common.collect.Sets;
import org.linqs.psl.application.learning.weight.VotedPerceptron;
import org.linqs.psl.application.util.GroundRules;
import org.linqs.psl.config.ConfigBundle;
import org.linqs.psl.database.Database;
import org.linqs.psl.model.atom.GroundAtom;
import org.linqs.psl.model.atom.ObservedAtom;
import org.linqs.psl.model.atom.RandomVariableAtom;
import org.linqs.psl.model.rule.GroundRule;
import org.linqs.psl.model.rule.Rule;
import org.linqs.psl.model.rule.WeightedGroundRule;
import org.linqs.psl.model.rule.WeightedRule;
import org.linqs.psl.reasoner.admm.term.ADMMTermStore;
import org.linqs.psl.reasoner.admm.term.AugmentedLinearLossTerm;
import org.linqs.psl.reasoner.admm.term.LinearLossTerm;
import org.linqs.psl.reasoner.admm.term.LocalVariable;
import org.linqs.psl.reasoner.function.FunctionSum;
import org.linqs.psl.reasoner.function.FunctionSummand;
import org.linqs.psl.reasoner.function.FunctionTerm;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * Weight learning using ranking loss with large margin.
 * Created by sriramsrinivasan on 4/4/18.
 */
public class SVMStructRank extends VotedPerceptron {
    private static final Logger log = LoggerFactory.getLogger(SVMStructRank.class);

    private Map<WeightedGroundRule, LinearLossTerm> lossMap;
    private DummyGroundRule[] dummyRules;
    private int totalPos, totalNeg;
    private Set<WeightedGroundRule> allRules;
    //private TermStore localTermStore;
    public SVMStructRank(List<Rule> rules, Database rvDB, Database observedDB, ConfigBundle config) {
        super(rules, rvDB, observedDB, false, config);
        //localTermStore = new ADMMTermStore();
        allRules = new HashSet<WeightedGroundRule>();
    }

    @Override
    protected void doLearn() {
		/* Modifies objective, collects dummyRules as array, and counts positive links */
        totalPos = 0; totalNeg = 0;
        lossMap = new HashMap<WeightedGroundRule, LinearLossTerm>(trainingMap.getTrainingMap().size());
        List<WeightedGroundRule> atomList = new ArrayList<WeightedGroundRule>(trainingMap.getTrainingMap().size());
        //termGenerator.generateTerms(groundRuleStore, localTermStore);
        for (Map.Entry<RandomVariableAtom, ObservedAtom> e : trainingMap.getTrainingMap().entrySet()) {
            RandomVariableAtom atom = e.getKey();
            List<LocalVariable> lv = new ArrayList<LocalVariable>();
            lv.add(((ADMMTermStore)termStore).createLocalVariable(atom.getVariable()));
            List<Float> coeff = new ArrayList<Float>();
            coeff.add(1.0f);
            AugmentedLinearLossTerm lossTerm = new AugmentedLinearLossTerm(lv, coeff, 0.0f);
            DummyGroundRule groundRule = new DummyGroundRule(lv.get(0).getGlobalId(), atom);
            termStore.add(groundRule, lossTerm);
            groundRuleStore.addGroundRule(groundRule);

            lossMap.put(groundRule, lossTerm);
            atomList.add(groundRule);
            if (e.getValue().getValue() == 1.0)
                totalPos++;
            else if (e.getValue().getValue() == 0.0)
                totalNeg++;
            else
                throw new IllegalStateException("Only Boolean training data are supported.");
        }
        dummyRules = atomList.toArray(new DummyGroundRule[atomList.size()]);
        allRules.addAll(atomList);
        for (int i = 0 ; i < mutableRules.size() ; i++) {
            for (GroundRule gr : groundRuleStore.getGroundRules(mutableRules.get(i))){
                allRules.add((WeightedGroundRule) gr);
            }
        }

        log.info("Total positive links: {}", totalPos);

        super.doLearn();

		/* Unmodifies objective */
        //localTermStore.clear();
    }

    @Override
    protected void computeExpectedIncompatibility() {
        float oldWeight, newWeight;
        //boolean runInference;
        int round = 0;
        final SVMRankStructComparator comparator = new SVMRankStructComparator();
        int numChanged;
        do {
            int numberOfActiveLossTerms = 0;
            numChanged = 0;
            log.info("Running inference to compute derivative, round {}", round);
            computeMPEState();

            //log.info("Objective: {}", GroundRules.getTotalWeightedIncompatibility(allRules));
            //runInference = false;
            int numPos = 0, numNeg = 0;
            Arrays.sort(dummyRules, comparator);
            newWeight = 0;
//			for (DummyGroundRule atom : dummyRules) {
//				System.out.println(atom + " " + atom.getAtom().getValue() + " " + trainingMap.getTrainingMap().get(atom.getAtom()).getValue());
//			}
            for (int i = 0; i < dummyRules.length; i++) {
                oldWeight = lossMap.get(dummyRules[i]).getWeight();
                if (trainingMap.getTrainingMap().get(dummyRules[i].getAtom()).getValue() == 1.0) {
                    newWeight = -(float) numNeg / totalPos / totalNeg;
//					newWeight = new PositiveWeight((double) numNeg);
                    numPos++;
                }
                else {
                    newWeight = -((float) numPos - totalPos) / totalPos / totalNeg;
//					newWeight = new NegativeWeight(((double) numPos - totalPos));
                    numNeg++;
                }
                if (newWeight != 0)
                    numberOfActiveLossTerms++;
                if (oldWeight != newWeight) {
                    dummyRules[i].setWeight(newWeight);
                    //lossMap.get(dummyRules[i]).setWeight(newWeight);
                    //termStore.updateWeight(dummyRules[i]);
                    inMPEState = false;
                    numChanged++;
                }
            }
            log.info("Number of active loss terms: {}, Changed: {}, Objective: {}", numberOfActiveLossTerms, numChanged, GroundRules.getTotalWeightedIncompatibility(allRules));
            round++;
        }
        while (!inMPEState && round < 20);

        log.info("Number of non-converged augmented terms: {}", numChanged);
		/* Computes incompatibility */
        //numGroundings = new double[kernels.size()];
        super.computeExpectedIncompatibility();

        double norm = 0.0;
        double margin = 0.0;
        margin -= GroundRules.getTotalWeightedIncompatibility(allRules);

        setLabeledRandomVariables();
        for (int i = 0; i < mutableRules.size(); i++) {
            norm += mutableRules.get(i).getWeight() * mutableRules.get(i).getWeight();
        }
        norm = Math.sqrt(norm)/2;
        margin += GroundRules.getTotalWeightedIncompatibility(allRules);

        log.info("Learning objective: {}. Norm: {}, margin violation: {}, loss: {}" , l2Regularization * norm + margin, norm, margin, getLoss());
        for (int i = 0; i < dummyRules.length; i++) {
            dummyRules[i].setWeight(0.0);
        }

    }

    protected class SVMRankStructComparator implements Comparator<DummyGroundRule> {

        private static final double epsilon = 1e-10;

        @Override
        public int compare(DummyGroundRule o1, DummyGroundRule o2) {
            double val1 = o1.getAtom().getValue();
            double val2 = o2.getAtom().getValue();
            val1 = (double)Math.round(val1 * 100000d) / 100000d;
            val2 = (double)Math.round(val2 * 100000d) / 100000d;
            if (val1 == val2) {
                double o1True = SVMStructRank.this.trainingMap.getTrainingMap().get((RandomVariableAtom)o1.getAtom()).getValue();
                double o2True = SVMStructRank.this.trainingMap.getTrainingMap().get((RandomVariableAtom)o2.getAtom()).getValue();

                return Double.compare(o1True, o2True);
            }
            else {
                return Double.compare(val1, val2);
            }
        }
    }


    protected class DummyGroundRule implements WeightedGroundRule {

        private Integer globalId;
        private Double weight;
        private GroundAtom atom;

        public DummyGroundRule(int globalId, GroundAtom atom) {
            this.globalId = globalId;
            this.atom = atom;
            this.weight = 0.0;
        }

        @Override
        public int hashCode() {
            return globalId.hashCode() + atom.hashCode();
        }

        @Override
        public String toString() {
            return "DUMMY-" + globalId.toString();
        }

        @Override
        public WeightedRule getRule() {
            return null;
        }

        @Override
        public boolean isSquared() {
            return false;
        }

        @Override
        public double getWeight() {
            return this.weight;
        }

        @Override
        public void setWeight(double weight) {
            this.weight = weight;
        }

        @Override
        public FunctionTerm getFunctionDefinition() {
            FunctionSum sum = new FunctionSum();
            sum.add(new FunctionSummand(1.0, atom.getVariable()));
            return sum;
        }

        @Override
        public double getIncompatibility() {
            return atom.getValue();
        }

        @Override
        public double getIncompatibility(GroundAtom replacementAtom, double replacementValue) {
            return getIncompatibility();
        }

        @Override
        public Set<GroundAtom> getAtoms() {
            return Sets.newHashSet(atom);
        }

        public GroundAtom getAtom() {
            return atom;
        }
    }

}

package org.linqs.psl.reasoner.marginals;

import com.google.common.collect.Sets;
import org.linqs.psl.config.Config;
import org.linqs.psl.reasoner.function.AtomFunctionVariable;
import org.linqs.psl.reasoner.marginals.term.MarginalObjectiveTerm;
import org.linqs.psl.reasoner.marginals.term.MarginalTermStore;
import org.linqs.psl.util.RandUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * #Metropolis within Gibbs with grouped variables.
 for i in tqdm_notebook(range(num_rounds-1)):
 samples_migg[i+1] = samples_migg[i]
 for j in variables_grouping:
 prev_y = samples_migg[i+1]
 new_y = prev_y.copy()
 zoo = 0#np.random.binomial(1,0.5)
 new_y[j[zoo]] = np.random.uniform(0,1)
 if j.shape[0] == 2:
 if zoo == 0:
 #new_y[j[1]] = np.random.uniform(new_y[j[0]] - 0.0, new_y[j[0]] - 0.0)
 new_y[j[1]] = np.random.uniform(0.999 - new_y[j[0]],  1.001 - new_y[j[0]])
 #new_y[j[1]] = np.random.normal(1-new_y[j[0]], 0.001)
 if j.shape[0] >2:
 print("ERRORRRRR.....")
 log_alpha = get_energy_more_eyes(prev_y, data, j) - get_energy_more_eyes(new_y, data, j)
 should_i_reject = False
 for g in j:
 if new_y[g] < 0 or new_y[g]>1:
 should_i_reject = True
 break
 if np.log(np.random.uniform(0,1)) < log_alpha and not should_i_reject:
 alphas[i+1, j] = 1
 samples_migg[i+1][j] = new_y[j]
 energies[i+1, j] = get_energy_more_eyes(samples_migg[i+1], data, j)
 * Created by sriramsrinivasan on 6/29/19.
 * BUGBUG: Not a very well written code.
 */
public class PairwiseABGibbsMarginalReasoner extends AbstractMarginalsReasoner {
    private static final Logger log = LoggerFactory.getLogger(PairwiseABGibbsMarginalReasoner.class);
    private  static final String PREFIX_STR = "abgibbs";

    private static final String WEIGHT_THRESHHOLD = PREFIX_STR+".weight_thresh";
    private static final float DEFAULT_WEIGHT_THRESHHOLD = 100f;
    private static final String INTERVAL_THRESHHOLD = PREFIX_STR+".int_thresh";
    private static final float DEFAULT_INTERVAL_THRESHHOLD = 0.1f;
    private static float weightThreshhold;
    private static float intervalThreshhold;

    public PairwiseABGibbsMarginalReasoner(){
        weightThreshhold = Config.getFloat(WEIGHT_THRESHHOLD, DEFAULT_WEIGHT_THRESHHOLD);
        intervalThreshhold = Math.max(Config.getFloat(INTERVAL_THRESHHOLD, DEFAULT_INTERVAL_THRESHHOLD), 0.0001f);
    }

    @Override
    protected void performSampling(MarginalTermStore marginalTermStore, int numVariables, float[][] samples) {
        log.info("Getting blocks.");
        Map<Integer, List<AtomFunctionVariable>> blockIdToVar = new HashMap<>();
        Map <AtomFunctionVariable, List<Block>> varToBlock = new HashMap<>();
        getBlocks(marginalTermStore, blockIdToVar, varToBlock);
        log.info("Starting to run Gibbs sampler. num samples:{}, number of variables: {}",
                num_samples, numVariables);
        int num_accepts = 0;
        Set<AtomFunctionVariable> sampledSoFar = new HashSet<>();
        for (int i = 1; i < num_samples; i++) {
            sampledSoFar.clear();
            for (List<AtomFunctionVariable> vars : blockIdToVar.values()) {
                float prev_energy = getEnergy(marginalTermStore, vars);
                for (int k = 0; k < vars.size() ; k++){
                    int id = RandUtils.nextInt(varToBlock.get(vars.get(k)).size());
                    varToBlock.get(vars.get(k)).get(id).sampleUpdate(vars.get(k), sampledSoFar);
                    sampledSoFar.add(vars.get(k));
                }
                for (AtomFunctionVariable var: vars) {
                    int j = marginalTermStore.getIndex(var);
                    samples[i][j] = (float)var.getValue();
                }
                float new_energy = getEnergy(marginalTermStore, vars);
                float log_alpha = prev_energy - new_energy;
                num_accepts++;
                //if reject then replace value to previous.
                if (log_alpha <= Math.log(RandUtils.nextFloat())) {
                    for (AtomFunctionVariable var: vars) {
                        int j = marginalTermStore.getIndex(var);
                        var.setValue(samples[i-1][j]);
                        samples[i][j] = samples[i - 1][j];
                    }
                    num_accepts--;
                }
            }
        }
        log.info("Completed running marginals: acceptance ratio = {}",
                ((float)num_accepts/((float)num_samples* numVariables)));
    }

    private void getBlocks(MarginalTermStore marginalTermStore, Map<Integer,
            List<AtomFunctionVariable>> blockIdToVar,
                           Map <AtomFunctionVariable, List<Block>> varToBlock){
        Map<AtomFunctionVariable, Integer> functionToBlockId = new HashMap<>();
        Map<Integer, Set<MarginalObjectiveTerm>> blockToObjectiveTerms = new HashMap<>();

        initializeBlocks(marginalTermStore, functionToBlockId, blockIdToVar, blockToObjectiveTerms);
        computeBlocks(marginalTermStore, functionToBlockId, blockIdToVar, blockToObjectiveTerms, varToBlock);
    }

    private void computeBlocks(MarginalTermStore marginalTermStore,
                               Map<AtomFunctionVariable, Integer> functionToBlockId,
                               Map<Integer, List<AtomFunctionVariable>> blockIdToVar,
                               Map<Integer, Set<MarginalObjectiveTerm>> blockIdToObjectiveTerms,
                               Map <AtomFunctionVariable, List<Block>> varToBlock) {
        boolean varPaired;
        for (int i = 0; i < marginalTermStore.getNumVariables(); i++) {
            final AtomFunctionVariable var1 = marginalTermStore.getVariable(i);
            final int setId1 = functionToBlockId.get(var1);
            final Set<MarginalObjectiveTerm> set1 = blockIdToObjectiveTerms.get(setId1);
            final List<AtomFunctionVariable> list1 = blockIdToVar.get(setId1);
            varPaired = false;
            List<Block> var1Blocks = varToBlock.get(var1) == null ? new ArrayList<Block>() : varToBlock.get(var1);
            for (int j = 0; j < marginalTermStore.getNumVariables(); j++) {
                final AtomFunctionVariable var2 = marginalTermStore.getVariable(j);
                final int setId2 = functionToBlockId.get(var2);
                if (setId1 == setId2) {
                    continue;
                }
                final Set<MarginalObjectiveTerm> set2 = blockIdToObjectiveTerms.get(setId2);
                final List<AtomFunctionVariable> list2 = blockIdToVar.get(setId2);
                List<Block> var2Blocks = varToBlock.get(var2) == null ? new ArrayList<Block>() : varToBlock.get(var2);
                if (!Sets.intersection(set1, set2).isEmpty()){
                    varPaired = true;
                    set1.addAll(set2);
                    functionToBlockId.put(var2, setId1);
                    list1.add(var2);
                    Block block = new PairwiseBlock(var1, var2, Sets.intersection(set1, set2));
                    var1Blocks.add(block);
                    var2Blocks.add(block);

                    list2.clear();
                    blockIdToVar.remove(setId2);
                    set2.clear();
                    blockIdToObjectiveTerms.remove(setId2);
                }
                varToBlock.put(var2, var2Blocks);
            }
            if (!varPaired) {
                // Could reuse object.
                var1Blocks.add(new SingleVariableBlock());
            }
            varToBlock.put(var1, var1Blocks);
        }
    }

    private void initializeBlocks(MarginalTermStore marginalTermStore,
                                  Map<AtomFunctionVariable, Integer> functionToBlockId,
                                  Map<Integer, List<AtomFunctionVariable>> blockIdToVar,
                                  Map<Integer, Set<MarginalObjectiveTerm>> blockToObjectiveTerms) {
        for (int i = 0; i < marginalTermStore.getNumVariables(); i++) {
            final AtomFunctionVariable var = marginalTermStore.getVariable(i);
            functionToBlockId.put(var, i);
            Set<MarginalObjectiveTerm> set = new HashSet<>();
            final List<MarginalObjectiveTerm> termsUsingVar = marginalTermStore.getTermsUsingVar(var);
            for (MarginalObjectiveTerm term : termsUsingVar) {
                if (term.getWeight() > weightThreshhold) {
                    set.add(term);
                }
            }
            blockToObjectiveTerms.put(i, set);
            List<AtomFunctionVariable> list = new ArrayList<>();
            list.add(var);
            blockIdToVar.put(i, list);
        }
    }

    interface Block {
        void sampleUpdate(AtomFunctionVariable var, Set<AtomFunctionVariable> sampledSet);
        AtomFunctionVariable getNeighbor(AtomFunctionVariable var);
    }

    class SingleVariableBlock implements Block {
        @Override
        public void sampleUpdate(AtomFunctionVariable var, Set<AtomFunctionVariable> sampledSet){
            if (sampledSet.contains(var)) {
                return;
            }
            var.setValue(RandUtils.nextFloat());
            sampledSet.add(var);
        }

        @Override
        public AtomFunctionVariable getNeighbor(AtomFunctionVariable var) {
            return null;
        }

    }

    class PairwiseBlock implements Block {
        private final AtomFunctionVariable var1;
        private final AtomFunctionVariable var2;
        private final Set<MarginalObjectiveTerm> commonTerms;
        private float rPlusLowerLim;
        private float rPlusUpperLim;
        private float plusWeight;
        private boolean plusActive;

        private float rMinusLowerLim;
        private float rMinusUpperLim;
        private float minusWeight;
        private boolean minusActive;

        PairwiseBlock(AtomFunctionVariable var1, AtomFunctionVariable var2,
                      Set<MarginalObjectiveTerm> commonTerms){
            this.var1 = var1;
            this.var2 = var2;
            this.commonTerms = commonTerms;
            rMinusLowerLim = 0;
            rMinusUpperLim = 1;
            rPlusLowerLim = 0;
            rPlusUpperLim = 1;
            plusWeight = weightThreshhold;
            minusWeight = weightThreshhold;
            plusActive = false;
            minusActive = false;
            computeUpperLowerLim();
        }

        void computeUpperLowerLim(){
            for (MarginalObjectiveTerm term: commonTerms){
                float c = term.getConstant();
                List<Float> coeffs = term.getCoeffs();
                float a = coeffs.get(0);
                float b = coeffs.get(1);
                if (a > 0 && b > 0 && c < rPlusUpperLim) {
                    //a+b <= c
                    rPlusUpperLim = c;
                    plusWeight = term.getWeight();
                    plusActive = true;
                } else if (a < 0 && b < 0 && -c > rPlusLowerLim ) {
                    //a+b >= -c
                    rPlusLowerLim = -c;
                    plusWeight = term.getWeight();
                    plusActive = true;
                } else if (a < 0 && b > 0 && -c > rMinusLowerLim) {
                    //a-b >= -c
                    rMinusLowerLim = -c;
                    minusWeight = term.getWeight();
                    minusActive = true;
                } else if (a > 0 && b < 0 && c < rMinusUpperLim) {
                    //a-b <= c
                    rMinusUpperLim = c;
                    minusWeight = term.getWeight();
                    minusActive = true;
                } else if (a == 0 || b == 0) {
                    throw new RuntimeException("There was a zero in the term : " + term.toString());
                }
            }
            if (rMinusLowerLim > rMinusUpperLim || rPlusLowerLim > rPlusUpperLim) {
                log.warn("Feasibility range is does not exist for this block. Might generate bad samples. ");
            } else if ((rMinusLowerLim > rPlusUpperLim && rMinusLowerLim > rMinusUpperLim) ||
                    (rPlusLowerLim > rMinusUpperLim && rPlusLowerLim > rMinusLowerLim)) {
                log.warn("Feasibility range is does not exist for this block due to " +
                        "non-overlapping plus and minus range. Might generate bad samples. ");
            }
        }

        //TODO: need to add comments explaining what is happening. The code sort of looks like a mess.
        public void sampleUpdate(AtomFunctionVariable var, Set<AtomFunctionVariable> sampledSet){
            if (var != this.var1 && var != this.var2) {
                throw new RuntimeException("Asking for a variable that does not exist in the block.");
            }
            if (sampledSet.contains(var)){
                return;
            }
            if ((!sampledSet.contains(this.var1) && this.var2 == var) || (!sampledSet.contains(this.var2) && this.var1 == var)) {
                var.setValue(RandUtils.nextFloat());
                sampledSet.add(var);
                return;
            }
            AtomFunctionVariable obsVar = (var == this.var1) ? this.var2 : this.var1;
            float obsVarVal = (float)obsVar.getValue();
            //TODO: For future, need to ensure we can have multiple active zones.
            if (plusActive && minusActive) {
                throw new UnsupportedOperationException("Cant have 2 active intervals yet.");
            }
            if (plusActive) {
                if (RandUtils.nextFloat() > 1/plusWeight &&
                        (Math.abs(rPlusUpperLim - rPlusLowerLim) < intervalThreshhold)) {
                    float ulim = 1;
                    float llim = 0;
                    if (rPlusLowerLim <= rPlusUpperLim) {
                        llim = rPlusLowerLim - obsVarVal;
                        ulim = rPlusUpperLim - obsVarVal;
                    } else {
                        llim = rPlusUpperLim - obsVarVal;
                        ulim = rPlusLowerLim - obsVarVal;
                    }
                    verifyRange(ulim, llim);
                    var.setValue(RandUtils.nextFloat(llim, ulim));
                } else {
                    var.setValue(RandUtils.nextFloat());
                }
            } else if (minusActive) {
                if (RandUtils.nextFloat() > 1/minusWeight &&
                        (Math.abs(rMinusUpperLim - rMinusLowerLim) < intervalThreshhold)) {
                    float ulim = 1;
                    float llim = 0;
                    if (obsVar == this.var1) {
                        if (rMinusLowerLim <= rMinusUpperLim) {
                            llim = obsVarVal - rMinusUpperLim;
                            ulim = obsVarVal - rMinusLowerLim;
                        } else {
                            llim = obsVarVal - rMinusLowerLim;
                            ulim = obsVarVal - rMinusUpperLim;
                        }
                    } else {
                        if (rMinusLowerLim <= rMinusUpperLim) {
                            llim = obsVarVal + rMinusLowerLim;
                            ulim = obsVarVal + rMinusUpperLim;
                        } else {
                            llim = obsVarVal + rMinusUpperLim;
                            ulim = obsVarVal + rMinusLowerLim;
                        }
                    }
                    verifyRange(ulim, llim);
                    var.setValue(RandUtils.nextFloat(llim, ulim));
                } else {
                    var.setValue(RandUtils.nextFloat());
                }
            } else {
                log.debug("No active range. This is same as doing Gibbs.");
                var.setValue(RandUtils.nextFloat());
            }
            sampledSet.add(var);
        }

        @Override
        public AtomFunctionVariable getNeighbor(AtomFunctionVariable var) {
            if (var == this.var1) {
                return var2;
            } else if (var == this.var2) {
                return var1;
            }
            return null;
        }

        private void verifyRange(float ulim, float llim) {
            if (llim < 0 || llim > 1 || ulim < 0 || ulim >1){
                throw new RuntimeException("Updated range not between zero and one.");
            }
        }


    }
}

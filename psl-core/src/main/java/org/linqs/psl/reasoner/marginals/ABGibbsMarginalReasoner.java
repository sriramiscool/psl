package org.linqs.psl.reasoner.marginals;

import com.google.common.collect.Sets;
import org.linqs.psl.reasoner.function.AtomFunctionVariable;
import org.linqs.psl.reasoner.marginals.term.MarginalObjectiveTerm;
import org.linqs.psl.reasoner.marginals.term.MarginalTermStore;

import java.util.*;

/**
 * Created by sriramsrinivasan on 6/29/19.
 */
public class ABGibbsMarginalReasoner extends AbstractMarginalsReasoner {
    @Override
    protected void performSampling(MarginalTermStore marginalTermStore, int numVariables, float[][] samples) {

    }

    private List<List<AtomFunctionVariable>> getBlocks(MarginalTermStore marginalTermStore){
        List<List<AtomFunctionVariable>> blocks = new ArrayList<>();
        Map<AtomFunctionVariable, Integer> functionToBlockId = new HashMap<>();
        Map<Integer, List<AtomFunctionVariable>> blockIdToVar = new HashMap<>();
        Map<Integer, Set<MarginalObjectiveTerm>> blockToObjectiveTerms = new HashMap<>();

        for (int i = 0; i < marginalTermStore.getNumVariables(); i++) {
            final AtomFunctionVariable var = marginalTermStore.getVariable(i);
            functionToBlockId.put(var, i);
            Set<MarginalObjectiveTerm> set = new HashSet<>();
            set.addAll(marginalTermStore.getTermsUsingVar(var));
            blockToObjectiveTerms.put(i, set);
            List<AtomFunctionVariable> list = new ArrayList<>();
            list.add(var);
            blockIdToVar.put(i, list);
        }
        for (int i = 0; i < marginalTermStore.getNumVariables(); i++) {
            final AtomFunctionVariable var1 = marginalTermStore.getVariable(i);
            final int setId1 = functionToBlockId.get(var1);
            final Set<MarginalObjectiveTerm> set1 = blockToObjectiveTerms.get(setId1);
            final List<AtomFunctionVariable> list1 = blockIdToVar.get(setId1);
            for (int j = 0; j < marginalTermStore.getNumVariables(); j++) {
                final AtomFunctionVariable var2 = marginalTermStore.getVariable(j);
                final int setId2 = functionToBlockId.get(var1);
                final Set<MarginalObjectiveTerm> set2 = blockToObjectiveTerms.get(setId2);
                final List<AtomFunctionVariable> list2 = blockIdToVar.get(setId2);
                if (!Sets.intersection(set1, set2).isEmpty()){
                    set1.addAll(set2);
                    functionToBlockId.put(var2, setId1);
                    list1.add(var2);

                    list2.clear();
                    blockIdToVar.remove(setId2);
                    set2.clear();
                    blockToObjectiveTerms.remove(setId2);
                }
            }
        }
        blocks.addAll(blockIdToVar.values());
        return blocks;
    }
}

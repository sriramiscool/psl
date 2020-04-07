package org.linqs.psl.application.learning.structure;

import org.deeplearning4j.rl4j.learning.Learning;
import org.deeplearning4j.rl4j.network.dqn.IDQN;
import org.deeplearning4j.rl4j.policy.DQNPolicy;
import org.deeplearning4j.rl4j.space.Encodable;
import org.linqs.psl.application.learning.structure.mdp.StructureLearner;
import org.nd4j.linalg.api.ndarray.INDArray;

import java.util.Set;

/**
 * Created by sriramsrinivasan on 4/5/20.
 */
public class CustomDLPolicy<O extends Encodable> extends DQNPolicy {
    private StructureLearner s;
    public CustomDLPolicy(IDQN dqn, StructureLearner s) {
        super(dqn);
        this.s = s;
    }

    @Override
    public Integer nextAction(INDArray input) {
        INDArray output = this.getNeuralNet().output(input);
        Set<Integer> validActions = s.getValidActions();
        for (int i = 0; i < output.length(); i++) {
            if (!validActions.contains(i)){
                output.putScalar(i, Integer.MIN_VALUE);
            }
        }
        return Learning.getMaxAction(output);
    }

}

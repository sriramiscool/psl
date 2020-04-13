package org.linqs.psl.application.learning.structure.mdp;

//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by Fernflower decompiler)
//

import org.deeplearning4j.rl4j.learning.Learning;
import org.deeplearning4j.rl4j.mdp.MDP;
import org.deeplearning4j.rl4j.network.dqn.DQN;
import org.deeplearning4j.rl4j.network.dqn.IDQN;
import org.deeplearning4j.rl4j.policy.Policy;
import org.deeplearning4j.rl4j.space.DiscreteSpace;
import org.deeplearning4j.rl4j.space.Encodable;
import org.nd4j.linalg.api.ndarray.INDArray;

import java.io.IOException;
import java.util.Set;

public class CustomDQNPolicy<O extends Encodable> extends Policy<O, Integer> {
    private final IDQN dqn;
    private final MDP mdp;

    public static <O extends Encodable> org.deeplearning4j.rl4j.policy.DQNPolicy<O> load(String path) throws IOException {
        return new org.deeplearning4j.rl4j.policy.DQNPolicy(DQN.load(path));
    }

    public IDQN getNeuralNet() {
        return this.dqn;
    }

    public Integer nextAction(INDArray input) {
        INDArray output = this.dqn.output(input);
        Set<Integer> validActions = ((StructureLearner)this.mdp).getValidActions();
        for (int i = 0; i < output.length(); i++) {
            if (!validActions.contains(i)){
                output.putScalar(i, Integer.MIN_VALUE);
            }
        }
        return Learning.getMaxAction(output);
    }

    public void save(String filename) throws IOException {
        this.dqn.save(filename);
    }

    public CustomDQNPolicy(IDQN dqn, MDP<O, Integer, DiscreteSpace> mdp) {
        this.dqn = dqn;
        this.mdp = mdp;
    }
}

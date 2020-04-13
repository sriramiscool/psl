package org.linqs.psl.application.learning.structure.mdp;

//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by Fernflower decompiler)
//

import org.deeplearning4j.rl4j.learning.StepCountable;
import org.deeplearning4j.rl4j.mdp.MDP;
import org.deeplearning4j.rl4j.policy.EpsGreedy;
import org.deeplearning4j.rl4j.policy.Policy;
import org.deeplearning4j.rl4j.space.ActionSpace;
import org.deeplearning4j.rl4j.space.Encodable;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class CustomEpsGreedy<O extends Encodable, A, AS extends ActionSpace<A>> extends EpsGreedy {
    private static final Logger log = LoggerFactory.getLogger(org.deeplearning4j.rl4j.policy.EpsGreedy.class);
    private final MDP<O, A, AS> mdp;
    private final Random rd;
    private final StepCountable learning;
    private final Policy<O, A> policy;

    @Override
    public A nextAction(INDArray input) {
        float ep = this.getEpsilon();
        if (this.learning.getStepCounter() % 500 == 1) {
            log.info("EP: " + ep + " " + this.learning.getStepCounter());
        }

        List<Integer> randomActions = new ArrayList<Integer>(((StructureLearner) this.mdp).getValidActions());
        Integer randomAction = randomActions.get(rd.nextInt(randomActions.size()));
        return this.rd.nextFloat() > ep ? this.policy.nextAction(input) : (A)randomAction;
    }

    public CustomEpsGreedy(Policy<O, A> policy, MDP<O, A, AS> mdp, int updateStart, int epsilonNbStep, Random rd, float minEpsilon, StepCountable learning) {
        super(policy, mdp, updateStart, epsilonNbStep, rd, minEpsilon, learning);
        this.mdp = mdp;
        this.rd = rd;
        this.learning = learning;
        this.policy = policy;
    }
}

package org.linqs.psl.application.learning.structure;

import org.deeplearning4j.rl4j.mdp.MDP;
import org.deeplearning4j.rl4j.network.dqn.DQNFactory;
import org.deeplearning4j.rl4j.network.dqn.DQNFactoryStdDense;
import org.deeplearning4j.rl4j.network.dqn.IDQN;
import org.deeplearning4j.rl4j.space.DiscreteSpace;
import org.deeplearning4j.rl4j.space.Encodable;
import org.deeplearning4j.rl4j.util.IDataManager;

public class CustomQLearningDiscreteDense<O extends Encodable> extends CustomQLearningDiscrete<O> {
    public CustomQLearningDiscreteDense(MDP<O, Integer, DiscreteSpace> mdp, IDQN dqn, QLConfiguration conf, IDataManager dataManager) {
        super(mdp, dqn, conf, dataManager, conf.getEpsilonNbStep());
    }

    public CustomQLearningDiscreteDense(MDP<O, Integer, DiscreteSpace> mdp, DQNFactory factory, QLConfiguration conf, IDataManager dataManager) {
        this(mdp, factory.buildDQN(mdp.getObservationSpace().getShape(), ((DiscreteSpace) mdp.getActionSpace()).getSize()), conf, dataManager);
    }

    public CustomQLearningDiscreteDense(MDP<O, Integer, DiscreteSpace> mdp, DQNFactoryStdDense.Configuration netConf, QLConfiguration conf, IDataManager dataManager) {
        this(mdp, (DQNFactory) (new DQNFactoryStdDense(netConf)), conf, dataManager);
    }
}

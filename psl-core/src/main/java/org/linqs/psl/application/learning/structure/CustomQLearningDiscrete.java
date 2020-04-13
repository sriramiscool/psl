//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by Fernflower decompiler)
//

package org.linqs.psl.application.learning.structure;

import org.deeplearning4j.gym.StepReply;
import org.deeplearning4j.rl4j.learning.Learning;
import org.deeplearning4j.rl4j.learning.sync.Transition;
import org.deeplearning4j.rl4j.learning.sync.qlearning.QLearning;
import org.deeplearning4j.rl4j.mdp.MDP;
import org.deeplearning4j.rl4j.network.dqn.IDQN;
import org.deeplearning4j.rl4j.policy.EpsGreedy;
import org.deeplearning4j.rl4j.space.DiscreteSpace;
import org.deeplearning4j.rl4j.space.Encodable;
import org.deeplearning4j.rl4j.util.DataManagerSyncTrainingListener;
import org.deeplearning4j.rl4j.util.IDataManager;
import org.linqs.psl.application.learning.structure.mdp.CustomDQNPolicy;
import org.linqs.psl.application.learning.structure.mdp.CustomEpsGreedy;
import org.linqs.psl.application.learning.structure.mdp.StructureLearner;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.factory.Nd4j;
import org.nd4j.linalg.indexing.INDArrayIndex;
import org.nd4j.linalg.indexing.NDArrayIndex;
import org.nd4j.linalg.primitives.Pair;
import org.nd4j.linalg.util.ArrayUtil;

import java.util.ArrayList;
import java.util.Set;

public abstract class CustomQLearningDiscrete<O extends Encodable> extends QLearning<O, Integer, DiscreteSpace> {
    private final QLConfiguration configuration;
    private final MDP<O, Integer, DiscreteSpace> mdp;
    private final IDQN currentDQN;
    private CustomDQNPolicy<O> policy;
    private EpsGreedy<O, Integer, DiscreteSpace> egPolicy;
    private IDQN targetDQN;
    private int lastAction;
    private INDArray[] history;
    private double accuReward;


    /** @deprecated */
    @Deprecated
    public CustomQLearningDiscrete(MDP<O, Integer, DiscreteSpace> mdp, IDQN dqn, QLConfiguration conf, IDataManager dataManager, int epsilonNbStep) {
        this(mdp, dqn, conf, epsilonNbStep);
        this.addListener(DataManagerSyncTrainingListener.builder(dataManager).build());
    }

    public CustomQLearningDiscrete(MDP<O, Integer, DiscreteSpace> mdp, IDQN dqn, QLConfiguration conf, int epsilonNbStep) {
        super(conf);
        this.history = null;
        this.accuReward = 0.0D;
        this.configuration = conf;
        this.mdp = mdp;
        this.currentDQN = dqn;
        this.targetDQN = dqn.clone();
        this.policy = new CustomDQNPolicy(this.getCurrentDQN(), this.mdp);
        this.egPolicy = new CustomEpsGreedy(this.policy, mdp, conf.getUpdateStart(), epsilonNbStep, this.getRandom(), conf.getMinEpsilon(), this);
        ((DiscreteSpace)mdp.getActionSpace()).setSeed(conf.getSeed());
    }

    public void postEpoch() {
        if (this.getHistoryProcessor() != null) {
            this.getHistoryProcessor().stopMonitor();
        }

    }

    public void preEpoch() {
        this.history = null;
        this.lastAction = 0;
        this.accuReward = 0.0D;
    }

    protected QLStepReturn<O> trainStep(O obs) {
        INDArray input = this.getInput(obs);
        boolean isHistoryProcessor = this.getHistoryProcessor() != null;
        if (isHistoryProcessor) {
            this.getHistoryProcessor().record(input);
        }

        int skipFrame = isHistoryProcessor ? this.getHistoryProcessor().getConf().getSkipFrame() : 1;
        int historyLength = isHistoryProcessor ? this.getHistoryProcessor().getConf().getHistoryLength() : 1;
        int updateStart = this.getConfiguration().getUpdateStart() + (this.getConfiguration().getBatchSize() + historyLength) * skipFrame;
        Double maxQ = 0.0D / 0.0;
        Integer action;
        INDArray qs;
        if (this.getStepCounter() % skipFrame != 0) {
            action = this.lastAction;
        } else {
            if (this.history == null) {
                if (isHistoryProcessor) {
                    this.getHistoryProcessor().add(input);
                    this.history = this.getHistoryProcessor().getHistory();
                } else {
                    this.history = new INDArray[]{input};
                }
            }

            INDArray hstack = Transition.concat(Transition.dup(this.history));
            if (isHistoryProcessor) {
                hstack.muli(1.0D / this.getHistoryProcessor().getScale());
            }

            if (hstack.shape().length > 2) {
                hstack = hstack.reshape(Learning.makeShape(1, ArrayUtil.toInts(hstack.shape())));
            }

            qs = this.getCurrentDQN().output(hstack);
            //Modified from max to maxValid
            //int maxAction = Learning.getMaxAction(qs);

            Set<Integer> validActions = ((StructureLearner)this.mdp).getValidActions();
            for (int i = 0; i < qs.length(); i++) {
                if (!validActions.contains(i)){
                    qs.putScalar(i, Integer.MIN_VALUE);
                }
            }
            int maxAction = Learning.getMaxAction(qs);
            maxQ = qs.getDouble((long)maxAction);
            action = (Integer)this.getEgPolicy().nextAction(hstack);
        }

        this.lastAction = action;
        StepReply<O> stepReply = this.getMdp().step(action);
        this.accuReward += stepReply.getReward() * this.configuration.getRewardFactor();
        if (this.getStepCounter() % skipFrame == 0 || stepReply.isDone()) {
            qs = this.getInput((O) stepReply.getObservation());
            if (isHistoryProcessor) {
                this.getHistoryProcessor().add(qs);
            }

            INDArray[] nhistory = isHistoryProcessor ? this.getHistoryProcessor().getHistory() : new INDArray[]{qs};
            Transition<Integer> trans = new Transition(this.history, action, this.accuReward, stepReply.isDone(), nhistory[0]);
            this.getExpReplay().store(trans);
            if (this.getStepCounter() > updateStart) {
                Pair<INDArray, INDArray> targets = this.setTarget(this.getExpReplay().getBatch());
                this.getCurrentDQN().fit((INDArray)targets.getFirst(), (INDArray)targets.getSecond());
            }

            this.history = nhistory;
            this.accuReward = 0.0D;
        }

        return new QLStepReturn(maxQ, this.getCurrentDQN().getLatestScore(), stepReply);
    }

    protected Pair<INDArray, INDArray> setTarget(ArrayList<Transition<Integer>> transitions) {
        if (transitions.size() == 0) {
            throw new IllegalArgumentException("too few transitions");
        } else {
            int size = transitions.size();
            int[] shape = this.getHistoryProcessor() == null ? this.getMdp().getObservationSpace().getShape() : this.getHistoryProcessor().getConf().getShape();
            int[] nshape = makeShape(size, shape);
            INDArray obs = Nd4j.create(nshape);
            INDArray nextObs = Nd4j.create(nshape);
            int[] actions = new int[size];
            boolean[] areTerminal = new boolean[size];

            for(int i = 0; i < size; ++i) {
                Transition<Integer> trans = (Transition)transitions.get(i);
                areTerminal[i] = trans.isTerminal();
                actions[i] = (Integer)trans.getAction();
                INDArray[] obsArray = trans.getObservation();
                if (obs.rank() == 2) {
                    obs.putRow((long)i, obsArray[0]);
                } else {
                    for(int j = 0; j < obsArray.length; ++j) {
                        obs.put(new INDArrayIndex[]{NDArrayIndex.point((long)i), NDArrayIndex.point((long)j)}, obsArray[j]);
                    }
                }

                INDArray[] nextObsArray = Transition.append(trans.getObservation(), trans.getNextObservation());
                if (nextObs.rank() == 2) {
                    nextObs.putRow((long)i, nextObsArray[0]);
                } else {
                    for(int j = 0; j < nextObsArray.length; ++j) {
                        nextObs.put(new INDArrayIndex[]{NDArrayIndex.point((long)i), NDArrayIndex.point((long)j)}, nextObsArray[j]);
                    }
                }
            }

            if (this.getHistoryProcessor() != null) {
                obs.muli(1.0D / this.getHistoryProcessor().getScale());
                nextObs.muli(1.0D / this.getHistoryProcessor().getScale());
            }

            INDArray dqnOutputAr = this.dqnOutput(obs);
            INDArray dqnOutputNext = this.dqnOutput(nextObs);
            INDArray targetDqnOutputNext = null;
            INDArray tempQ = null;
            INDArray getMaxAction = null;
            if (this.getConfiguration().isDoubleDQN()) {
                targetDqnOutputNext = this.targetDqnOutput(nextObs);
                getMaxAction = Nd4j.argMax(dqnOutputNext, new int[]{1});
            } else {
                tempQ = Nd4j.max(dqnOutputNext, 1);
            }

            for(int i = 0; i < size; ++i) {
                double yTar = ((Transition)transitions.get(i)).getReward();
                double q;
                if (!areTerminal[i]) {
                    q = 0.0D;
                    if (this.getConfiguration().isDoubleDQN()) {
                        q += targetDqnOutputNext.getDouble((long)i, (long)getMaxAction.getInt(new int[]{i}));
                    } else {
                        q += tempQ.getDouble((long)i);
                    }

                    yTar += this.getConfiguration().getGamma() * q;
                }

                q = dqnOutputAr.getDouble((long)i, (long)actions[i]);
                double lowB = q - this.getConfiguration().getErrorClamp();
                double highB = q + this.getConfiguration().getErrorClamp();
                double clamped = Math.min(highB, Math.max(yTar, lowB));
                dqnOutputAr.putScalar((long)i, (long)actions[i], clamped);
            }

            return new Pair(obs, dqnOutputAr);
        }
    }

    public QLConfiguration getConfiguration() {
        return this.configuration;
    }

    public MDP<O, Integer, DiscreteSpace> getMdp() {
        return this.mdp;
    }

    public IDQN getCurrentDQN() {
        return this.currentDQN;
    }

    public CustomDQNPolicy<O> getPolicy() {
        return this.policy;
    }

    public EpsGreedy<O, Integer, DiscreteSpace> getEgPolicy() {
        return this.egPolicy;
    }

    public IDQN getTargetDQN() {
        return this.targetDQN;
    }

    public void setTargetDQN(IDQN targetDQN) {
        this.targetDQN = targetDQN;
    }
}

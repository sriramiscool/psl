package org.linqs.psl.application.learning.structure.mdp;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.deeplearning4j.gym.StepReply;
import org.deeplearning4j.rl4j.learning.NeuralNetFetchable;
import org.deeplearning4j.rl4j.mdp.MDP;
import org.deeplearning4j.rl4j.network.dqn.IDQN;
import org.deeplearning4j.rl4j.space.ArrayObservationSpace;
import org.deeplearning4j.rl4j.space.DiscreteSpace;
import org.deeplearning4j.rl4j.space.ObservationSpace;
import org.json.JSONObject;
import org.linqs.psl.application.learning.structure.rulegen.DRLRuleGenerator;
import org.linqs.psl.model.predicate.StandardPredicate;
import org.linqs.psl.model.rule.WeightedRule;

import java.util.*;

@Slf4j
public class StructureLearner implements MDP<State, Integer, DiscreteSpace> {
    private int numRules;
    private int numActions;
    private int numTemplates;
    private int numPredicates;
    private int ruleLength;
    private Map<Integer, DRLRuleGenerator> idToTemplate;
    private Map<Integer, StandardPredicate> idToPredicate;
    private DiscreteSpace actionSpace;
    private ObservationSpace<State> observationSpace;
    private Set<StandardPredicate> openPredicates;
    @Getter
    private State state;
    private NeuralNetFetchable<IDQN> fetchable;
    private boolean isDone;


    public StructureLearner(int numRules, int ruleLength, Map<Integer, DRLRuleGenerator> idToTemplate, Map<Integer, StandardPredicate> idToPredicate, Set<StandardPredicate> openPredicates) {
        this.numTemplates = idToTemplate.size();
        this.numPredicates = idToPredicate.size();
        this.numActions = this.numTemplates + this.numPredicates;
        this.ruleLength = ruleLength;
        this.numRules = numRules;
        this.idToTemplate = idToTemplate;
        this.idToPredicate = idToPredicate;
        this.actionSpace = new DiscreteSpace(this.numActions);
        this.state = new State(numRules, ruleLength, this.numActions, idToTemplate, idToPredicate);
        this.observationSpace = new ArrayObservationSpace(new int[] {numActions*numRules*ruleLength});
        this.openPredicates = openPredicates;
    }

    public void close() {
    }

    @Override
    public ObservationSpace<State> getObservationSpace() {
        return observationSpace;
    }

    @Override
    public DiscreteSpace getActionSpace() {
        return actionSpace;
    }

    public State reset() {
        this.isDone = false;
        return state.reset();
    }

    private double computeReward() {
        if (!this.state.getInRule()) {
            Set<WeightedRule> uniqueRules = new LinkedHashSet<>();
            uniqueRules.addAll(this.state.getRules());
            int numNewRules = uniqueRules.size();
            int numOldRules = this.state.getRules().size();
            if (numNewRules == numOldRules) {
                return numNewRules * 1000;
            }
            else {
                return 0;
            }
        }
        else {
            return 100;
        }
    }

    private boolean checkValidAction(Integer a) {
        boolean isValid = false;
        if (a.intValue() < numTemplates) {
            if(!this.state.getInRule()) {
                isValid =  true;
            }
        }
        else if (this.state.getInRule()) {
            DRLRuleGenerator template = this.state.getCurrentRuleTemplate();
            StandardPredicate selectedPredicate = idToPredicate.get(a);
            if (this.state.getCurrentTargetPredicate() == null) {
                if (this.openPredicates.contains(selectedPredicate)) {
                    isValid = true;
                }
            }
            else {
                List<StandardPredicate> rulePredicates = this.state.getRulePredicates();
                StandardPredicate targetPredicate = this.state.getCurrentTargetPredicate();
                if(template.isValid(targetPredicate, rulePredicates, selectedPredicate)) {
                    isValid =  true;
                }
            }
        }

        return isValid;
    }

    public Set<Integer> getValidActions(){
        Set<Integer> val = new HashSet<>();
        for (int i = 0 ; i < this.numActions ; i++){
            if (checkValidAction(i)){
                val.add(i);
            }
        }
        return val;
    }

    public StepReply<State> step(Integer a) {
        double reward = 0;

        if(checkValidAction(a)) {
            this.state.updateState(a);
            reward = computeReward();
        }
        else {
            //Move it to the right place
            reward = -10000;
        }

        isDone = false;
        if(this.state.getPos() == this.numRules*this.ruleLength*this.numActions) {
            isDone = true;
        }
        return new StepReply<>(this.state, reward, isDone, new JSONObject("{}"));
    }

    @Override
    public boolean isDone() {
        return isDone;
    }

    @Override
    public MDP<State, Integer, DiscreteSpace> newInstance() {
        StructureLearner pslSL = new StructureLearner(numRules, ruleLength, this.idToTemplate, this.idToPredicate, this.openPredicates);
        pslSL.setFetchable(fetchable);
        return pslSL;
    }

    public void setFetchable(NeuralNetFetchable<IDQN> fetchable) {
        this.fetchable = fetchable;
    }

    public String getString(){
        return this.state.toString();

    }
}

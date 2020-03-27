package org.linqs.psl.application.learning.structure.mdp;

import org.deeplearning4j.rl4j.space.Encodable;
import org.linqs.psl.application.learning.structure.rulegen.DRLRuleGenerator;
import org.linqs.psl.model.predicate.StandardPredicate;

import java.util.ArrayList;
import java.util.Map;

/**
 * @author varunembar (vembar@ucsc.edu) 03/22/20.
 */

public class State implements Encodable {

    private final double[] state;
    private int pos;
    private int numRules;
    private int ruleLength;
    private int numActions;
    private boolean inRule;
    private DRLRuleGenerator currentTemplate;
    private StandardPredicate currentTargetPredicate;
    private ArrayList<StandardPredicate> rulePredicates;
    private Map<Integer, DRLRuleGenerator> idToTemplate;
    private Map<Integer, StandardPredicate> idToPredicate;

    State(int numRules, int ruleLength, int numActions, Map<Integer, DRLRuleGenerator> idToTemplate, Map<Integer, StandardPredicate> idToPredicate) {

        this.state = new double[numActions*ruleLength*numRules];
        this.ruleLength = ruleLength;
        this.numRules = numRules;
        this.numActions = numActions;
        this.pos = 0;
        this.inRule = false;
        this.currentTemplate = null;
        this.rulePredicates = new ArrayList<>();
        this.idToPredicate = idToPredicate;
        this.idToTemplate = idToTemplate;
        this.currentTargetPredicate = null;
    }

    public void updateState(double val){
        Integer value = new Integer((int)val);
        this.state[this.pos + (int)val] = 1;
        this.pos += numActions;
        if(idToTemplate.containsKey(value)) {
            this.inRule = true;
            this.currentTemplate = idToTemplate.get(value);
            this.rulePredicates = new ArrayList<>();
            this.currentTargetPredicate = null;
        }
        else {
            this.rulePredicates.add(idToPredicate.get(value));
            //Check if end of rule
            if((this.pos % (numActions*ruleLength)) == 0) {
                this.inRule = false;
            }
            //Check if it is target predicate
            if((this.pos % (numActions*ruleLength)) == 2*numActions) {
                this.currentTargetPredicate = idToPredicate.get(value);
            }
        }
    }

    public int getPos() {
        return this.pos;
    }

    public StandardPredicate getCurrentTargetPredicate() {
        return currentTargetPredicate;
    }

    public DRLRuleGenerator getCurrentRuleTemplate() {
        return currentTemplate;
    }

    public ArrayList<StandardPredicate> getRulePredicates() {
        return rulePredicates;
    }
    @Override
    public double[] toArray() {
        return state;
    }

    public int getSize(){
        return this.state.length;
    }

    public boolean getInRule() {
        return this.inRule;
    }

    public State reset() {
        for (int i = 0; i < this.state.length; i++) {
            state[i] = 0;
        }
        this.pos = 0;
        this.inRule = false;
        this.currentTemplate = null;
        this.rulePredicates = new ArrayList<>();
        return this;
    }
}

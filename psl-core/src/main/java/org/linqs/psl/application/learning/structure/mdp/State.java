package org.linqs.psl.application.learning.structure.mdp;

import org.deeplearning4j.rl4j.space.Encodable;
import org.linqs.psl.application.learning.structure.rulegen.DRLRuleGenerator;
import org.linqs.psl.model.predicate.StandardPredicate;
import org.linqs.psl.model.rule.WeightedRule;

import java.util.ArrayList;
import java.util.List;
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
    private List<StandardPredicate> rulePredicates;
    private List<Boolean> isNegated;
    private Map<Integer, DRLRuleGenerator> idToTemplate;
    private Map<Integer, StandardPredicate> idToPredicate;
    private List<WeightedRule> rules;

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
        this.rules = new ArrayList<>();
        this.isNegated = new ArrayList<>();
    }
    private WeightedRule getRule(){
        List<StandardPredicate> body = new ArrayList<>();
        body.addAll(rulePredicates);
        body.remove(0);
        StandardPredicate head = rulePredicates.get(0);
        return (WeightedRule) this.currentTemplate.generateRule(head, body, isNegated);
    }


    public void updateState(double val){
        Integer value = new Integer((int)val);
        this.state[this.pos + (int)val] = 1;
        this.pos += numActions;
        if(idToTemplate.containsKey(value)) {
            if (this.currentTemplate != null) {
                rules.add(getRule());
            }
            this.inRule = true;
            this.currentTemplate = idToTemplate.get(value);
            this.rulePredicates = new ArrayList<>();
            this.currentTargetPredicate = null;
        }
        else {
            this.rulePredicates.add(idToPredicate.get(value));
            //for now all positive.
            this.isNegated.add(false);
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

    public List<StandardPredicate> getRulePredicates() {
        return rulePredicates;
    }

    public List<WeightedRule> getRules(){
        if (this.currentTemplate!=null) {
            rules.add(getRule());
            this.currentTemplate = null;
            this.rulePredicates = new ArrayList<>();
            this.currentTargetPredicate = null;

        }
        return rules;
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
        this.rules.clear();
        this.isNegated.clear();
        return this;
    }
}

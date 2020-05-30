package org.linqs.psl.reasoner.admm.term;

import org.linqs.psl.model.atom.RandomVariableAtom;
import org.linqs.psl.model.rule.GroundRule;
import org.linqs.psl.model.rule.Rule;
import org.linqs.psl.reasoner.term.ReasonerLocalVariable;
import org.linqs.psl.reasoner.term.ReasonerTerm;
import org.linqs.psl.reasoner.term.TermStore;

import java.util.Iterator;

/**
 * Created by sriramsrinivasan on 5/30/20.
 */
public class FakeTermStore implements TermStore {

    private float weight;
    FakeTermStore (float weight) {
        this.weight = weight;
    }

    @Override
    public void add(GroundRule rule, ReasonerTerm term) {

    }

    @Override
    public void clear() {

    }

    @Override
    public void reset() {

    }

    @Override
    public void close() {

    }

    @Override
    public void iterationComplete() {

    }

    @Override
    public void initForOptimization() {

    }

    @Override
    public ReasonerTerm get(int index) {
        return null;
    }

    @Override
    public int size() {
        return 0;
    }

    @Override
    public void ensureCapacity(int capacity) {

    }

    @Override
    public void ensureVariableCapacity(int capacity) {

    }

    @Override
    public ReasonerLocalVariable createLocalVariable(RandomVariableAtom atom) {
        return null;
    }

    @Override
    public Iterator noWriteIterator() {
        return null;
    }

    @Override
    public double getWeight(int index) {
        return weight;
    }

    @Override
    public int getRuleInd(Rule rule) {
        return 0;
    }

    @Override
    public void addRule(Rule rule) {

    }

    @Override
    public Iterator iterator() {
        return null;
    }
}

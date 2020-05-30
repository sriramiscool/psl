/*
 * This file is part of the PSL software.
 * Copyright 2011-2015 University of Maryland
 * Copyright 2013-2020 The Regents of the University of California
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.linqs.psl.reasoner.term;

import org.linqs.psl.config.Options;
import org.linqs.psl.model.atom.RandomVariableAtom;
import org.linqs.psl.model.rule.GroundRule;
import org.linqs.psl.model.rule.Rule;
import org.linqs.psl.model.rule.WeightedRule;
import org.linqs.psl.util.RandUtils;

import java.util.*;

public class MemoryTermStore<T extends ReasonerTerm> implements TermStore<T, RandomVariableAtom> {
    private ArrayList<T> store;

    /**
     * hold array of rules and indecies
     */
    protected List<Rule> rules;
    protected Map<Rule, Integer> ruleToIndex;

    public MemoryTermStore() {
        this(Options.MEMORY_TS_INITIAL_SIZE.getInt());
        rules = new ArrayList<>();
        ruleToIndex = new HashMap<>();
    }

    public MemoryTermStore(int initialSize) {
        store = new ArrayList<T>(initialSize);
    }

    @Override
    public synchronized void add(GroundRule rule, T term) {
        if (!ruleToIndex.containsKey(rule.getRule())) {
            throw new RuntimeException("Rule not added before adding ground rule.");
        }
        store.add(term);
    }

    @Override
    public double getWeight(int index){
        if (rules.get(index) == null){
            throw new RuntimeException("Something is seriously wrong. Rule is accessed before adding or index issue.");
        }
        if (rules.get(index) instanceof WeightedRule){
            return ((WeightedRule) rules.get(index)).getWeight();
        }
        throw new UnsupportedOperationException("Rule not weighted: " + rules.get(index).toString());
    }

    @Override
    public int getRuleInd(Rule rule) {
        return ruleToIndex.containsKey(rule) ? ruleToIndex.get(rule) : -1;
    }

    @Override
    public synchronized void addRule(Rule rule) {
        if (!ruleToIndex.containsKey(rule)) {
            ruleToIndex.put(rule, rules.size());
            rules.add(rule);
        }
    }

    @Override
    public void clear() {
        if (store != null) {
            store.clear();
        }
    }

    @Override
    public void reset() {
        // Nothing is required for a MemoryTermStore to reset.
    }

    @Override
    public void close() {
        clear();

        store = null;
    }

    @Override
    public void initForOptimization() {
    }

    @Override
    public void iterationComplete() {
    }

    @Override
    public T get(int index) {
        return store.get(index);
    }

    @Override
    public int size() {
        return store.size();
    }

    @Override
    public void ensureCapacity(int capacity) {
        assert(capacity >= 0);

        if (capacity == 0) {
            return;
        }

        store.ensureCapacity(capacity);
    }

    @Override
    public Iterator<T> iterator() {
        return store.iterator();
    }

    @Override
    public Iterator<T> noWriteIterator() {
        return iterator();
    }

    @Override
    public RandomVariableAtom createLocalVariable(RandomVariableAtom atom) {
        return atom;
    }

    @Override
    public void ensureVariableCapacity(int capacity) {
    }

    public void shuffle() {
        RandUtils.shuffle(store);
    }
}

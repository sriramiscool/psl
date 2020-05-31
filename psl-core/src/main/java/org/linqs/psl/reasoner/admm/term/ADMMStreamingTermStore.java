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
package org.linqs.psl.reasoner.admm.term;

import org.linqs.psl.database.atom.AtomManager;
import org.linqs.psl.model.atom.RandomVariableAtom;
import org.linqs.psl.model.rule.Rule;
import org.linqs.psl.reasoner.term.streaming.ADMMTermPool;
import org.linqs.psl.reasoner.term.streaming.BasicStreamingTermStore;
import org.linqs.psl.reasoner.term.streaming.StreamingIterator;
import org.linqs.psl.reasoner.term.streaming.TermPool;

import java.util.List;

/**
 * A term store that iterates over ground queries directly (obviating the GroundRuleStore).
 * Note that the iterators given by this class are meant to be exhaustd (at least the first time).
 * Remember that this class will internally iterate over an unknown number of groundings.
 * So interrupting the iteration can cause the term count to be incorrect.
 */
public class ADMMStreamingTermStore extends BasicStreamingTermStore<ADMMObjectiveTerm, LocalVariable> {
    public ADMMStreamingTermStore(List<Rule> rules, AtomManager atomManager) {
        super(rules, atomManager, new ADMMTermGenerator());
    }

    @Override
    protected TermPool<ADMMObjectiveTerm> getTermPool(int pageSize) {
        return new ADMMTermPool(pageSize);
    }

    protected boolean supportsRule(Rule rule) {
        // No special requirements for rules.
        return true;
    }

    @Override
    protected StreamingIterator<ADMMObjectiveTerm> getInitialRoundIterator() {
        return new ADMMStreamingInitialRoundIterator(
                this, rules, atomManager, termGenerator,
                termCache, termPool, termBuffer, volatileBuffer, pageSize);
    }

    @Override
    protected StreamingIterator<ADMMObjectiveTerm> getCacheIterator() {
        return new ADMMStreamingCacheIterator(
                this, false, termCache, termPool,
                termBuffer, volatileBuffer, shufflePage, shuffleMap, randomizePageAccess, numPages);
    }

    @Override
    protected StreamingIterator<ADMMObjectiveTerm> getNoWriteIterator() {
        return new ADMMStreamingCacheIterator(
                this, true, termCache, termPool,
                termBuffer, volatileBuffer, shufflePage, shuffleMap, randomizePageAccess, numPages);
    }

    public int getNumVariables() {
        return store.getNumVariables();
    }

    public Iterable<LocalVariable> getVariables() {
        throw new UnsupportedOperationException();
    }

    @Override
    public float[] getVariableValues() {
        return store.getVariableValues();
    }

    @Override
    public float getVariableValue(int index) {
        return store.getVariableValue(index);
    }

    @Override
    public int getVariableIndex(LocalVariable variable) {
        return variable.getGlobalId();
    }

    @Override
    public synchronized LocalVariable createLocalVariable(RandomVariableAtom atom) {
        store.createLocalVariable(atom);
        int consensusId = store.getVariableIndex(atom);

        LocalVariable localVariable = new LocalVariable(consensusId, (float)atom.getValue());

        return localVariable;
    }

}

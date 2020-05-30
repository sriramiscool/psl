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
package org.linqs.psl.reasoner.term.streaming;

import org.linqs.psl.database.atom.AtomManager;
import org.linqs.psl.model.atom.RandomVariableAtom;
import org.linqs.psl.model.rule.Rule;
import org.linqs.psl.reasoner.term.HyperplaneTermGenerator;
import org.linqs.psl.reasoner.term.ReasonerTerm;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * A term store that does not hold all the terms in memory, but instead keeps most terms on disk.
 * Variables are kept in memory, but terms are kept on disk.
 */
public abstract class RVAStreamingTermStore<T extends ReasonerTerm> extends BasicStreamingTermStore<T, RandomVariableAtom> {
    private static final Logger log = LoggerFactory.getLogger(RVAStreamingTermStore.class);


    public RVAStreamingTermStore(List<Rule> rules, AtomManager atomManager,
                                 HyperplaneTermGenerator<T, RandomVariableAtom> termGenerator) {
        super(rules, atomManager, termGenerator);
    }
    @Override
    public int getNumVariables() {
        return store.getNumVariables();
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
    public int getVariableIndex(RandomVariableAtom variable) {
        return store.getVariableIndex(variable);
    }

    @Override
    public Iterable<RandomVariableAtom> getVariables() {
        return store.getVariables();
    }

    @Override
    public RandomVariableAtom createLocalVariable(RandomVariableAtom atom) {
        return store.createLocalVariable(atom);
    }

}

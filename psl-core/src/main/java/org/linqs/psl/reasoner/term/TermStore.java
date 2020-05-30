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

import org.linqs.psl.model.atom.RandomVariableAtom;
import org.linqs.psl.model.rule.GroundRule;
import org.linqs.psl.model.rule.Rule;

import java.util.Iterator;

/**
 * A place to store terms that are to be optimized.
 */
public interface TermStore<T extends ReasonerTerm, V extends ReasonerLocalVariable> extends Iterable<T> {
    /**
     * Add a term to the store that was generated from the given ground rule.
     */
    public void add(GroundRule rule, T term);

    /**
     * Remove any existing terms and prepare for a new set.
     */
    public void clear();

    /**
     * Reset the existing terms for another round of inference.
     * Atom values are used to reset variables.
     * Does NOT clear().
     */
    public void reset();

    /**
     * Close down the term store, it will not be used any more.
     */
    public void close();

    /**
     * A notification by the Reasoner that a single iteration is complete.
     * TermStores may use this as a chance to update and data structures.
     */
    public void iterationComplete();

    /**
     * A notification by the Reasoner that optimization is about to begin.
     * TermStores may use this as a chance to finalize data structures.
     */
    public void initForOptimization();

    public T get(int index);

    public int size();

    /**
     * Ensure that the underlying stuctures can have the required term capacity.
     * This is more of a hint to the store about how much memory will be used.
     * This is best called on an empty store so it can prepare.
     */
    public void ensureCapacity(int capacity);

    /**
     * Ensure that the underlying stuctures can have the required variable capacity.
     * This is more of a hint to the store about how much memory will be used.
     * This is best called on an empty store so it can prepare.
     * Not all term stores will even manage variables.
     */
    public void ensureVariableCapacity(int capacity);

    /**
     * Create a variable local to a specific term.
     */
    public V createLocalVariable(RandomVariableAtom atom);

    /**
     * Get an iterator over the terms in the store that does not write to disk.
     */
    public Iterator<T> noWriteIterator();

    /**
     * Get weight of any rule given its index if the rule exists in the termstore
     */
    public double getWeight(int index);

    /**
     * Get the rule index stored.
     */
    public int getRuleInd(Rule rule);

    /**
     * Add the rule index stored. Mainly so that we can keep all rules in store and
     * keep track of weight at rule level instead of term.
     */
    public void addRule(Rule rule);
}

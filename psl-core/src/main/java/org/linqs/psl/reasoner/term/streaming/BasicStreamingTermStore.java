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

import org.linqs.psl.config.Options;
import org.linqs.psl.database.atom.AtomManager;
import org.linqs.psl.model.atom.RandomVariableAtom;
import org.linqs.psl.model.rule.GroundRule;
import org.linqs.psl.model.rule.Rule;
import org.linqs.psl.model.rule.WeightedRule;
import org.linqs.psl.reasoner.admm.term.ADMMObjectiveTerm;
import org.linqs.psl.reasoner.term.*;
import org.linqs.psl.util.SystemUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.nio.ByteBuffer;
import java.nio.file.Paths;
import java.util.*;

/**
 * A term store that does not hold all the terms in memory, but instead keeps most terms on disk.
 * Variables are kept in memory, but terms are kept on disk.
 */
public abstract class BasicStreamingTermStore<T extends ReasonerTerm, V extends ReasonerLocalVariable> implements VariableTermStore<T, V> {
    private static final Logger log = LoggerFactory.getLogger(BasicStreamingTermStore.class);

    public static final int INITIAL_PATH_CACHE_SIZE = 100;

    protected List<WeightedRule> rules;
    protected AtomManager atomManager;

    protected List<String> termPagePaths;
    protected List<String> volatilePagePaths;

    protected boolean initialRound;
    protected StreamingIterator<T> activeIterator;
    protected int seenTermCount;
    protected int numPages;

    protected HyperplaneTermGenerator<T, V> termGenerator;

    protected int pageSize;
    protected String pageDir;
    protected boolean shufflePage;
    protected boolean randomizePageAccess;

    protected boolean warnRules;
    protected Map<Rule, Integer> ruleToIndex;

    /**
     * An internal store to track the terms and consensus variables.
     */
    protected MemoryVariableTermStore<ADMMObjectiveTerm, RandomVariableAtom> store;
    /**
     * The IO buffer for terms.
     * This buffer is only written on the first iteration,
     * and contains only components of the terms that do not change.
     */
    protected ByteBuffer termBuffer;

    /**
     * The IO buffer for volatile values.
     * These values change every iteration, and need to be updated.
     */
    protected ByteBuffer volatileBuffer;

    /**
     * Terms in the current page.
     * On the initial round, this is filled from DB and flushed to disk.
     * On subsequent rounds, this is filled from disk.
     */
    protected List<T> termCache;

    /**
     * Terms that we will reuse when we start pulling from the cache.
     * This should be a fill page's worth.
     * After the initial round, terms will bounce between here and the term cache.
     */
    protected List<T> termPool;

    /**
     * When we shuffle pages, we need to know how they were shuffled so the volatile
     * cache can be writtten in the same order.
     * So we will shuffle this list of sequential ints in the same order as the page.
     */
    protected int[] shuffleMap;

    public BasicStreamingTermStore(List<Rule> rules, AtomManager atomManager,
                                   HyperplaneTermGenerator<T, V> termGenerator) {
        pageSize = Options.STREAMING_TS_PAGE_SIZE.getInt();
        pageDir = Options.STREAMING_TS_PAGE_LOCATION.getString();
        shufflePage = Options.STREAMING_TS_SHUFFLE_PAGE.getBoolean();
        randomizePageAccess = Options.STREAMING_TS_RANDOMIZE_PAGE_ACCESS.getBoolean();
        warnRules = Options.STREAMING_TS_WARN_RULES.getBoolean();
        ruleToIndex = new HashMap<>();

        this.rules = new ArrayList<WeightedRule>();
        int ruleNum = 0;
        for (Rule rule : rules) {
            if (!rule.supportsIndividualGrounding()) {
                if (warnRules) {
                    log.warn("Streaming term stores do not support rules that cannot individually ground (arithmetic rules with summations): " + rule);
                }
                continue;
            }

            if (!supportsRule(rule)) {
                if (warnRules) {
                    log.warn("Rule not supported: " + rule);
                }

                continue;
            }
            // HACK(eriq): This is not actually true,
            //  but I am putting it in place for efficiency reasons.
            if (rule.isWeighted() && ((WeightedRule)rule).getWeight() < 0.0) {
                if (warnRules) {
                    log.warn("Streaming term stores do not support negative weights: " + rule);
                }
                continue;
            }


            this.rules.add((WeightedRule)rule);
            this.ruleToIndex.put(rule, ruleNum++);
        }

        if (this.rules.size() == 0) {
            throw new IllegalArgumentException("Found no valid rules for a streaming term store.");
        }

        this.atomManager = atomManager;
        this.termGenerator = termGenerator;
        ensureVariableCapacity(atomManager.getCachedRVACount());

        termPagePaths = new ArrayList<String>(INITIAL_PATH_CACHE_SIZE);
        volatilePagePaths = new ArrayList<String>(INITIAL_PATH_CACHE_SIZE);

        initialRound = true;
        activeIterator = null;
        numPages = 0;

        termBuffer = null;
        volatileBuffer = null;

        SystemUtils.recursiveDelete(pageDir);
        if (pageSize <= 1) {
            throw new IllegalArgumentException("Page size is too small.");
        }

        termCache = new ArrayList<T>(pageSize);
        termPool = new ArrayList<T>(pageSize);
        shuffleMap = new int[pageSize];

        (new File(pageDir)).mkdirs();
    }

    public boolean isLoaded() {
        return !initialRound;
    }

    @Override
    public int size() {
        return seenTermCount;
    }

    @Override
    public void add(GroundRule rule, T term) {
        throw new UnsupportedOperationException();
    }

    @Override
    public T get(int index) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void ensureCapacity(int capacity) {
        throw new UnsupportedOperationException();
    }

    public String getTermPagePath(int index) {
        // Make sure the path is built.
        for (int i = termPagePaths.size(); i <= index; i++) {
            termPagePaths.add(Paths.get(pageDir, String.format("%08d_term.page", i)).toString());
        }

        return termPagePaths.get(index);
    }

    public String getVolatilePagePath(int index) {
        // Make sure the path is built.
        for (int i = volatilePagePaths.size(); i <= index; i++) {
            volatilePagePaths.add(Paths.get(pageDir, String.format("%08d_volatile.page", i)).toString());
        }

        return volatilePagePaths.get(index);
    }

    /**
     * A callback for the initial round iterator.
     * The ByterBuffers are here because of possible reallocation.
     */
    public void initialIterationComplete(int termCount, int numPages, ByteBuffer termBuffer, ByteBuffer volatileBuffer) {
        seenTermCount = termCount;
        this.numPages = numPages;
        this.termBuffer = termBuffer;
        this.volatileBuffer = volatileBuffer;

        initialRound = false;
        activeIterator = null;
    }

    /**
     * A callback for the non-initial round iterator.
     */
    public void cacheIterationComplete() {
        activeIterator = null;
    }

    /**
     * Get an iterator that goes over all the terms for only reading.
     * Before this method can be called, a full iteration must have already been done.
     * (The cache will need to have been built.)
     */
    public Iterator<T> noWriteIterator() {
        if (activeIterator != null) {
            throw new IllegalStateException("Iterator already exists for this RVAStreamingTermStore. Exhaust the iterator first.");
        }

        if (initialRound) {
            throw new IllegalStateException("A full iteration must have already been completed before asking for a read-only iterator.");
        }

        activeIterator = getNoWriteIterator();

        return activeIterator;
    }

    @Override
    public Iterator<T> iterator() {
        if (activeIterator != null) {
            throw new IllegalStateException("Iterator already exists for this RVAStreamingTermStore. Exhaust the iterator first.");
        }

        if (initialRound) {
            activeIterator = getInitialRoundIterator();
        } else {
            activeIterator = getCacheIterator();
        }

        return activeIterator;
    }



    @Override
    public void syncAtoms() {
        store.syncAtoms();
    }

    public void ensureVariableCapacity(int capacity) {
        store.ensureCapacity(capacity);
    }


    @Override
    public void reset() {
        store.reset();
    }


    @Override
    public void clear() {
        initialRound = true;
        numPages = 0;

        if (activeIterator != null) {
            activeIterator.close();
            activeIterator = null;
        }

        if (termCache != null) {
            termCache.clear();
        }

        if (termPool != null) {
            termPool.clear();
        }

        store.clear();
        SystemUtils.recursiveDelete(pageDir);
    }

    @Override
    public void close() {
        clear();

        if (termBuffer != null) {
            termBuffer.clear();
            termBuffer = null;
        }

        if (volatileBuffer != null) {
            volatileBuffer.clear();
            volatileBuffer = null;
        }

        if (termCache != null) {
            termCache = null;
        }

        if (termPool != null) {
            termPool = null;
        }
        store.close();
    }

    @Override
    public void initForOptimization() {
    }

    @Override
    public void iterationComplete() {
    }

    @Override
    public double getWeight(int index){
        return this.rules.get(index).getWeight();
    }


    @Override
    public int getRuleInd(Rule rule) {
        return ruleToIndex.containsKey(rule) ? ruleToIndex.get(rule) : -1;
    }

    @Override
    public void addRule(Rule rule) {
        throw new UnsupportedOperationException();
    }


    /**
     * Check if this term store supports this rule.
     * @return true if the rule is supported.
     */
    protected abstract boolean supportsRule(Rule rule);

    /**
     * Get an iterator that will perform grounding queries and write the initial pages to disk.
     */
    protected abstract StreamingIterator<T> getInitialRoundIterator();

    /**
     * Get an iterator that will read and write from disk.
     */
    protected abstract StreamingIterator<T> getCacheIterator();

    /**
     * Get an iterator that will not write to disk.
     */
    protected abstract StreamingIterator<T> getNoWriteIterator();
}

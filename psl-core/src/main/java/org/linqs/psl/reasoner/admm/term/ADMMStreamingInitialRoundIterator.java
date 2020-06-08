/**
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
import org.linqs.psl.model.rule.WeightedRule;
import org.linqs.psl.reasoner.term.HyperplaneTermGenerator;
import org.linqs.psl.reasoner.term.streaming.ADMMTermPool;
import org.linqs.psl.reasoner.term.streaming.StreamingInitialRoundIterator;
import org.linqs.psl.reasoner.term.streaming.TermPool;
import org.linqs.psl.util.RuntimeStats;

import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.List;

/**
 * Iterate over all the terms that come up from grounding.
 * On this first iteration, we will build the term cache up from ground rules
 * and flush the terms to disk.
 */
public class ADMMStreamingInitialRoundIterator extends StreamingInitialRoundIterator<ADMMObjectiveTerm, LocalVariable> {
    public ADMMStreamingInitialRoundIterator(
            ADMMStreamingTermStore parentStore, List<WeightedRule> rules,
            AtomManager atomManager, HyperplaneTermGenerator<ADMMObjectiveTerm, LocalVariable> termGenerator,
            List<ADMMObjectiveTerm> termCache, TermPool<ADMMObjectiveTerm> termPool,
            ByteBuffer termBuffer, ByteBuffer volatileBuffer,
            int pageSize) {
        super(parentStore, rules, atomManager, termGenerator, termCache, termPool, termBuffer, volatileBuffer, pageSize);
    }

    @Override
    protected void writeFullPage(String termPagePath, String volatilePagePath) {
        flushTermCache(termPagePath);
        flushVolatileCache(volatilePagePath);

        termCache.clear();
    }

    private void flushTermCache(String termPagePath) {
        // Count the exact size we will need to write.
        int termsSize = 0;
        for (ADMMObjectiveTerm term : termCache) {
            termsSize += term.fixedByteSize();
            termsSize += Integer.SIZE / 8; // to store the type of ADMMObjectiveTerm
        }

        // Allocate an extra two ints for the number of terms and size of terms in that page.
        int termBufferSize = termsSize + (Integer.SIZE / 8) * 2;

        if (termBuffer == null || termBuffer.capacity() < termBufferSize) {
            termBuffer = ByteBuffer.allocate((int)(termBufferSize * OVERALLOCATION_RATIO));
        }
        termBuffer.clear();

        // First put the size of the terms and number of terms.
        termBuffer.putInt(termsSize);
        termBuffer.putInt(termCache.size());

        // Now put in all the terms.
        for (ADMMObjectiveTerm term : termCache) {
            termBuffer.putInt(ADMMTermPool.classToTermType.get(term.getClass()));
            term.writeFixedValues(termBuffer);
        }

        try (FileOutputStream stream = new FileOutputStream(termPagePath)) {
            stream.write(termBuffer.array(), 0, termBufferSize);
        } catch (IOException ex) {
            throw new RuntimeException("Unable to write term cache page: " + termPagePath, ex);
        }

        // Log io.
        RuntimeStats.logDiskWrite(termBufferSize);
    }

    private void flushVolatileCache(String volatilePagePath) {
        // Count the exact size we will need to write.
        int volatileSize = 0;
        for (ADMMObjectiveTerm term : termCache) {
            volatileSize += term.volatileByteSize();
        }

        int volatileBufferSize = (Float.SIZE / 8) + volatileSize;

        if (volatileBuffer == null || volatileBuffer.capacity() < volatileBufferSize) {
            volatileBuffer = ByteBuffer.allocate((int)(volatileBufferSize * OVERALLOCATION_RATIO));
        }
        volatileBuffer.clear();

        volatileBuffer.putInt(volatileSize);

        // Put in all the volatile values.
        for (ADMMObjectiveTerm term : termCache) {
            term.writeVolatileValues(volatileBuffer);
        }

        try (FileOutputStream stream = new FileOutputStream(volatilePagePath)) {
            stream.write(volatileBuffer.array(), 0, volatileBufferSize);
        } catch (IOException ex) {
            throw new RuntimeException("Unable to write volatile cache page: " + volatilePagePath, ex);
        }

        // Log io.
        RuntimeStats.logDiskWrite(volatileBufferSize);
    }
}
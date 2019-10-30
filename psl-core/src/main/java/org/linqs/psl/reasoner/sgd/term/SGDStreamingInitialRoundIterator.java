/**
 * This file is part of the PSL software.
 * Copyright 2011-2015 University of Maryland
 * Copyright 2013-2019 The Regents of the University of California
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
package org.linqs.psl.reasoner.sgd.term;

import org.linqs.psl.database.atom.AtomManager;
import org.linqs.psl.model.atom.RandomVariableAtom;
import org.linqs.psl.model.rule.WeightedRule;
import org.linqs.psl.reasoner.term.HyperplaneTermGenerator;
import org.linqs.psl.reasoner.term.streaming.StreamingInitialRoundIterator;
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
public class SGDStreamingInitialRoundIterator extends StreamingInitialRoundIterator<SGDObjectiveTerm> {
    public SGDStreamingInitialRoundIterator(
            SGDStreamingTermStore parentStore, List<WeightedRule> rules,
            AtomManager atomManager, HyperplaneTermGenerator<SGDObjectiveTerm, RandomVariableAtom> termGenerator,
            List<SGDObjectiveTerm> termCache, List<SGDObjectiveTerm> termPool,
            ByteBuffer termBuffer, ByteBuffer volatileBuffer,
            int pageSize) {
        super(parentStore, rules, atomManager, termGenerator, termCache, termPool, termBuffer, volatileBuffer, pageSize);
    }

    @Override
    protected void writeFullPage(String termPagePath, String volatilePagePath) {
        flushTermCache(termPagePath);

        termCache.clear();

        // SGD doesn't use a volatile buffer.
        if (volatileBuffer == null) {
            volatileBuffer = ByteBuffer.allocate(0);
        }
    }

    private void flushTermCache(String termPagePath) {
        // Count the exact size we will need to write.
        int termsSize = 0;
        for (SGDObjectiveTerm term : termCache) {
            termsSize += term.fixedByteSize();
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
        for (SGDObjectiveTerm term : termCache) {
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
}

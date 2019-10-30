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

import org.linqs.psl.model.atom.RandomVariableAtom;
import org.linqs.psl.reasoner.term.streaming.StreamingCacheIterator;
import org.linqs.psl.util.RuntimeStats;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.Map;

public class SGDStreamingCacheIterator extends StreamingCacheIterator<SGDObjectiveTerm> {
    public SGDStreamingCacheIterator(
            SGDStreamingTermStore parentStore, boolean readonly,
            List<SGDObjectiveTerm> termCache, List<SGDObjectiveTerm> termPool,
            ByteBuffer termBuffer, ByteBuffer volatileBuffer,
            boolean shufflePage, int[] shuffleMap, boolean randomizePageAccess,
            int numPages) {
        super(parentStore, readonly, termCache, termPool, termBuffer,
                volatileBuffer, shufflePage, shuffleMap, randomizePageAccess, numPages);
    }

    @Override
    protected void readPage(String termPagePath, String volatilePagePath) {
        int termsSize = 0;
        int numTerms = 0;
        int headerSize = (Integer.SIZE / 8) * 2;

        try (FileInputStream termStream = new FileInputStream(termPagePath)) {
            // First read the term size information.
            termStream.read(termBuffer.array(), 0, headerSize);

            termsSize = termBuffer.getInt();
            numTerms = termBuffer.getInt();

            // Now read in all the terms.
            termStream.read(termBuffer.array(), headerSize, termsSize);
        } catch (IOException ex) {
            throw new RuntimeException(String.format("Unable to read cache page: [%s].", termPagePath), ex);
        }

        // Log io.
        RuntimeStats.logDiskRead(headerSize + termsSize);

        // Convert all the terms from binary to objects.
        // Use the terms from the pool.

        for (int i = 0; i < numTerms; i++) {
            SGDObjectiveTerm term = termPool.get(i);
            term.read(termBuffer, volatileBuffer);
            termCache.add(term);
        }
    }

    @Override
    protected void writeVolatilePage(String volatilePagePath) {
        // SGD doesn't need write pages.
    }
}

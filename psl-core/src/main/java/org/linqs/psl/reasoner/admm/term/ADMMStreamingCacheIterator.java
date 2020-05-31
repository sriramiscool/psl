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

import org.linqs.psl.reasoner.term.streaming.ADMMTermPool;
import org.linqs.psl.reasoner.term.streaming.StreamingCacheIterator;
import org.linqs.psl.reasoner.term.streaming.TermPool;
import org.linqs.psl.util.RuntimeStats;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.List;

public class ADMMStreamingCacheIterator extends StreamingCacheIterator<ADMMObjectiveTerm, LocalVariable> {

    public ADMMStreamingCacheIterator(
            ADMMStreamingTermStore parentStore, boolean readonly,
            List<ADMMObjectiveTerm> termCache, TermPool<ADMMObjectiveTerm> termPool,
            ByteBuffer termBuffer, ByteBuffer volatileBuffer,
            boolean shufflePage, int[] shuffleMap, boolean randomizePageAccess,
            int numPages) {
        super(parentStore, readonly, termCache, termPool, termBuffer,
                volatileBuffer, shufflePage, shuffleMap, randomizePageAccess, numPages);
    }

    @Override
    protected void readPage(String termPagePath, String volatilePagePath) {
        int termsSize = 0;
        int volatileSize = 0;
        int numTerms = 0;
        int headerSize = (Integer.SIZE / 8) * 2;
        int volatileHeaderSize = (Integer.SIZE / 8);

        try (FileInputStream termStream = new FileInputStream(termPagePath);
             FileInputStream volatileStream = new FileInputStream(volatilePagePath)) {
            // First read the term size information.
            termStream.read(termBuffer.array(), 0, headerSize);
            volatileStream.read(volatileBuffer.array(), 0, volatileHeaderSize);

            termsSize = termBuffer.getInt();
            numTerms = termBuffer.getInt();
            volatileSize = volatileBuffer.getInt();

            // Now read in all the terms.
            termStream.read(termBuffer.array(), headerSize, termsSize);
            volatileStream.read(volatileBuffer.array(), volatileHeaderSize, volatileSize);
        } catch (IOException ex) {
            throw new RuntimeException(String.format("Unable to read cache page: [%s].", termPagePath), ex);
        }

        // Log io.
        RuntimeStats.logDiskRead(headerSize + termsSize);

        // Convert all the terms from binary to objects.
        // Use the terms from the pool.

        for (int i = 0; i < numTerms; i++) {
            int termType = termBuffer.getInt();
            ADMMObjectiveTerm term = termPool.get(i, ADMMTermPool.termTypeToClass.get(termType));
            term.read(termBuffer, volatileBuffer);
            termCache.add(term);
        }
    }

    @Override
    protected void writeVolatilePage(String volatilePagePath) {
        // Count the exact size we will need to write.
        int volatileSize = 0;
        for (ADMMObjectiveTerm term : termCache) {
            volatileSize += term.volatileByteSize();
        }

        int volatileBufferSize = (Float.SIZE / 8) + volatileSize;

        volatileBuffer.putInt(volatileSize);

        // If this page was picked up from the cache (and not from grounding) and shuffled,
        // then we will need to use the shuffle map to write the volatile values back in
        // the same order as the terms.
        if (shufflePage) {
            for (int shuffledIndex = 0; shuffledIndex < termCache.size(); shuffledIndex++) {
                int writeIndex = shuffleMap[shuffledIndex];
                ADMMObjectiveTerm term = termCache.get(shuffledIndex);
                volatileBuffer.mark();
                volatileBuffer.position(writeIndex * term.volatileByteSize());
                term.writeVolatileValues(volatileBuffer);
                volatileBuffer.reset();
            }
        } else {
            for (ADMMObjectiveTerm term : termCache) {
                term.writeVolatileValues(volatileBuffer);
            }
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

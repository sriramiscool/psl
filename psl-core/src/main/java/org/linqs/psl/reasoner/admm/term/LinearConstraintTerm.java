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

import org.linqs.psl.reasoner.function.FunctionComparator;
import org.linqs.psl.reasoner.term.Hyperplane;
import org.linqs.psl.reasoner.term.TermStore;
import org.linqs.psl.util.MathUtils;

import java.nio.ByteBuffer;

/**
 * ADMMReasoner objective term of the form:
 * (0 if coefficients^T * x OP constant)
 * or (infinity otherwise).
 * Where OP is equals, less than or eqauls, or greater than or equals.
 *
 * All coefficients must be non-zero.
 */
public class LinearConstraintTerm extends HyperplaneTerm {
    private FunctionComparator comparator;

    protected LinearConstraintTerm(Hyperplane<LocalVariable> hyperplane, FunctionComparator comparator, int ruleIndex) {
        super(hyperplane, ruleIndex);
        this.comparator = comparator;
    }

    @Override
    public float evaluate(TermStore termStore) {
        return evaluateInternal(null, termStore);
    }

    @Override
    public float evaluate(float[] consensusValues, TermStore termStore) {
        return evaluateInternal(consensusValues, termStore);
    }

    /**
     * if (coefficients^T * x [comparator] constant) { return 0.0 }
     * else { return infinity }
     */
    private float evaluateInternal(float[] consensusValues, TermStore termStore) {
        float value = 0.0f;
        if (consensusValues == null) {
            value = super.evaluate(termStore);
        } else {
            value = super.evaluate(consensusValues, termStore);
        }

        if (comparator.equals(FunctionComparator.EQ)) {
            if (MathUtils.isZero(value, MathUtils.RELAXED_EPSILON)) {
                return 0.0f;
            }
            return Float.POSITIVE_INFINITY;
        } else if (comparator.equals(FunctionComparator.LTE)) {
            if (value <= 0.0f) {
                return 0.0f;
            }
            return Float.POSITIVE_INFINITY;
        } else if (comparator.equals(FunctionComparator.GTE)) {
            if (value >= 0.0f) {
                return 0.0f;
            }
            return Float.POSITIVE_INFINITY;
        } else {
            throw new IllegalStateException("Unknown comparison function.");
        }
    }

    @Override
    public void minimize(float stepSize, float[] consensusValues, TermStore termStore) {
        // If it's not an equality constraint, first tries to minimize without the constraint.
        if (!comparator.equals(FunctionComparator.EQ)) {

            // Initializes scratch data.
            float total = 0.0f;

            // Minimizes without regard for the constraint, i.e., solves
            // argmin stepSize/2 * \|x - z + y / stepSize \|_2^2
            for (int i = 0; i < size; i++) {
                LocalVariable variable = variables[i];
                variable.setValue(consensusValues[variable.getGlobalId()] - variable.getLagrange() / stepSize);

                total += coefficients[i] * variable.getValue();
            }

            // Checks if the solution satisfies the constraint. If so, updates
            // the local primal variables and returns.
            if ( (comparator.equals(FunctionComparator.LTE) && total <= constant)
                    ||
                 (comparator.equals(FunctionComparator.GTE) && total >= constant)
                ) {
                return;
            }
        }

        // If the naive minimization didn't work, or if it's an equality constraint,
        // projects onto the hyperplane
        project(stepSize, consensusValues);
    }

    @Override
    public int fixedByteSize() {
        int bitSize = super.fixedByteSize();
        bitSize += Integer.SIZE / 8; //constant
        return bitSize;
    }

    @Override
    public void writeFixedValues(ByteBuffer fixedBuffer){
        super.writeFixedValues(fixedBuffer);
        fixedBuffer.putInt(comparator.ordinal());
    }

    @Override
    public void read(ByteBuffer fixedBuffer, ByteBuffer volatileBuffer){
        super.read(fixedBuffer, volatileBuffer);
        comparator = FunctionComparator.values()[fixedBuffer.getInt()];
    }
}

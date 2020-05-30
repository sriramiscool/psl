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

import org.junit.Assert;
import org.junit.Test;
import org.linqs.psl.reasoner.function.FunctionComparator;
import org.linqs.psl.reasoner.term.Hyperplane;

import java.io.IOException;
import java.nio.ByteBuffer;

import static org.junit.Assert.assertEquals;

public class LinearConstraintTermTest {
    @Test
    public void testMinimize() {
        // Problem 1: Constraint inactive at solution
        float[] z = {0.2f, 0.5f};
        float[] y = {0.0f, 0.0f};
        float[] coeffs = {1.0f, 1.0f};
        float constant = 1.0f;
        FunctionComparator comparator = FunctionComparator.LTE;
        float stepSize = 1.0f;
        float[] expected = {0.2f, 0.5f};
        testProblem(z, y, coeffs, constant, comparator, stepSize, expected);

        // Problem 2: Constraint active at solution
        z = new float[] {0.7f, 0.5f};
        y = new float[] {0.0f, 0.0f};
        coeffs = new float[] {1.0f, 1.0f};
        constant = 1.0f;
        comparator = FunctionComparator.LTE;
        stepSize = 1.0f;
        expected = new float[] {0.6f, 0.4f};
        testProblem(z, y, coeffs, constant, comparator, stepSize, expected);

        // Problem 3: Equality constraint
        z = new float[] {0.7f, 0.5f};
        y = new float[] {0.0f, 0.0f};
        coeffs = new float[] {1.0f, -1.0f};
        constant = 0.0f;
        comparator = FunctionComparator.EQ;
        stepSize = 1.0f;
        expected = new float[] {0.6f, 0.6f};
        testProblem(z, y, coeffs, constant, comparator, stepSize, expected);
    }
    @Test
    public void testReadWrite() throws IOException {

        float[] z = new float[] {0.7f, 0.5f};
        float[] l = new float[] {0.05f, 1.0f};
        float[] coeffs = new float[] {1.0f, -1.0f};
        float constant = -0.5f;
        int wInd = 2;
        LocalVariable[] variables = new LocalVariable[z.length];
        for (int i = 0; i < z.length; i++) {
            variables[i] = new LocalVariable(i, z[i]);
            variables[i].setLagrange(l[i]);
        }

        LinearConstraintTerm term = new LinearConstraintTerm(
                new Hyperplane<LocalVariable>(variables, coeffs, constant, z.length),
                FunctionComparator.EQ, wInd);


        float[] z1 = new float[] {0.3f};
        float[] l1 = new float[] {0.5f};
        float[] coeffs1 = new float[] {-1.0f};
        float constant1 = -0.8f;
        int wInd1 = 1;
        LocalVariable[] variables1 = new LocalVariable[z1.length];
        for (int i = 0; i < z1.length; i++) {
            variables1[i] = new LocalVariable(i, z1[i]);
            variables1[i].setLagrange(l1[i]);
        }
        LinearConstraintTerm term1 = new LinearConstraintTerm(
                new Hyperplane<LocalVariable>(variables1, coeffs1, constant1, z1.length),
                FunctionComparator.GTE, wInd1);

        Assert.assertEquals((Float.SIZE*3 + Integer.SIZE * 5) / 8, term.fixedByteSize()); // 2 coeff + const + ruleIndex + 2 globalId + size + comparator
        Assert.assertEquals((Float.SIZE*4) / 8, term.volatileByteSize());
        ByteBuffer fixedBuffer = ByteBuffer.allocate((term.fixedByteSize()));
        ByteBuffer volatileBuffer = ByteBuffer.allocate((term.fixedByteSize()));
        fixedBuffer.mark();
        volatileBuffer.mark();
        term.writeFixedValues(fixedBuffer);
        term.writeVolatileValues(volatileBuffer);
        fixedBuffer.reset();
        volatileBuffer.reset();
        term1.read(fixedBuffer, volatileBuffer);
        Assert.assertEquals(term, term1);

    }
    @Test
    public void testReadWrite2() throws IOException {

        float[] z = new float[] {0.7f, 0.5f};
        float[] l = new float[] {0.05f, 1.0f};
        float[] coeffs = new float[] {1.0f, -1.0f};
        float constant = -0.5f;
        int wInd = 2;
        LocalVariable[] variables = new LocalVariable[z.length];
        for (int i = 0; i < z.length; i++) {
            variables[i] = new LocalVariable(i, z[i]);
            variables[i].setLagrange(l[i]);
        }

        LinearConstraintTerm term = new LinearConstraintTerm(
                new Hyperplane<LocalVariable>(variables, coeffs, constant, z.length),
                FunctionComparator.EQ, wInd);


        float[] z1 = new float[] {0.3f};
        float[] l1 = new float[] {0.5f};
        float[] coeffs1 = new float[] {-1.0f};
        float constant1 = -0.8f;
        int wInd1 = 1;
        LocalVariable[] variables1 = new LocalVariable[z1.length];
        for (int i = 0; i < z1.length; i++) {
            variables1[i] = new LocalVariable(i, z1[i]);
            variables1[i].setLagrange(l1[i]);
        }
        LinearConstraintTerm term1 = new LinearConstraintTerm(
                new Hyperplane<LocalVariable>(variables1, coeffs1, constant1, z1.length),
                FunctionComparator.GTE, wInd1);

        Assert.assertEquals((Float.SIZE*2 + Integer.SIZE * 4) / 8, term1.fixedByteSize()); // 1 coeff + const + ruleIndex + 1 globalId + size + comparator
        Assert.assertEquals((Float.SIZE*2) / 8, term1.volatileByteSize());
        ByteBuffer fixedBuffer = ByteBuffer.allocate((term1.fixedByteSize()));
        ByteBuffer volatileBuffer = ByteBuffer.allocate((term1.fixedByteSize()));
        fixedBuffer.mark();
        volatileBuffer.mark();
        term1.writeFixedValues(fixedBuffer);
        term1.writeVolatileValues(volatileBuffer);
        fixedBuffer.reset();
        volatileBuffer.reset();
        term.read(fixedBuffer, volatileBuffer);
        Assert.assertEquals(term1, term);

    }


    private void testProblem(float[] z, float[] y, float[] coeffs, float constant,
            FunctionComparator comparator, final float stepSize, float[] expected) {
        LocalVariable[] variables = new LocalVariable[z.length];

        for (int i = 0; i < z.length; i++) {
            variables[i] = new LocalVariable(i, z[i]);
            variables[i].setLagrange(y[i]);
        }

        LinearConstraintTerm term = new LinearConstraintTerm(new Hyperplane<LocalVariable>(variables, coeffs, constant, z.length), comparator, 0);
        term.minimize(stepSize, z, null);

        for (int i = 0; i < z.length; i++) {
            assertEquals(expected[i], variables[i].getValue(), 5e-5);
        }
    }
}

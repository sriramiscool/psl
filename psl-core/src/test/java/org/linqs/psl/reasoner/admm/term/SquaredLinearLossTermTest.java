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
import org.linqs.psl.reasoner.term.Hyperplane;

import java.io.IOException;
import java.nio.ByteBuffer;

import static org.junit.Assert.assertEquals;

public class SquaredLinearLossTermTest {
    @Test
    public void testMinimize() {
        // Problem 1
        float[] z = {0.4f, 0.5f, 0.1f};
        float[] y = {0.0f, 0.0f, -0.05f};
        float[] coeffs = {0.3f, -1.0f, 0.4f};
        float constant = -20.0f;
        float weight = 0.5f;
        float stepSize = 2.0f;
        float[] expected = {-1.41569f, 6.55231f, -2.29593f};
        testProblem(z, y, coeffs, constant, weight, stepSize, expected);
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

        SquaredLinearLossTerm term = new SquaredLinearLossTerm(new Hyperplane<LocalVariable>(variables, coeffs, constant, z.length), wInd);


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

        SquaredLinearLossTerm term1 = new SquaredLinearLossTerm(new Hyperplane<LocalVariable>(variables1, coeffs1, constant1, z1.length), wInd1);

        Assert.assertEquals((Float.SIZE*3 + Integer.SIZE * 4) / 8, term.fixedByteSize()); // 2 coeff + ruleIndex + 2 globalId + size + constant (useless though)
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

        SquaredLinearLossTerm term = new SquaredLinearLossTerm(new Hyperplane<LocalVariable>(variables, coeffs, constant, z.length), wInd);


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

        SquaredLinearLossTerm term1 = new SquaredLinearLossTerm(new Hyperplane<LocalVariable>(variables1, coeffs1, constant1, z1.length), wInd1);

        Assert.assertEquals((Float.SIZE*2 + Integer.SIZE * 3) / 8, term1.fixedByteSize()); // 1 coeff + ruleIndex + 1 globalId + size + constant (useless though)
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
            float weight, final float stepSize, float[] expected) {
        LocalVariable[] variables = new LocalVariable[z.length];

        for (int i = 0; i < z.length; i++) {
            variables[i] = new LocalVariable(i, z[i]);
            variables[i].setLagrange(y[i]);
        }

        SquaredLinearLossTerm term = new SquaredLinearLossTerm(new Hyperplane<LocalVariable>(variables, coeffs, constant, z.length), 0);
        term.minimize(stepSize, z, new FakeTermStore(weight));

        for (int i = 0; i < z.length; i++) {
            assertEquals(expected[i], variables[i].getValue(), 5e-5);
        }
    }
}

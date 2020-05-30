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

import org.linqs.psl.reasoner.term.Hyperplane;
import org.linqs.psl.reasoner.term.TermStore;
import org.linqs.psl.util.MathUtils;

import java.nio.ByteBuffer;

/**
 * Objective term for an ADMMReasoner that is based on a hyperplane in some way.
 *
 * Stores the characterization of the hyperplane as coefficients^T * x = constant
 * and projects onto the hyperplane.
 *
 * All coefficients must be non-zero.
 */
public abstract class HyperplaneTerm extends ADMMObjectiveTerm {
    protected float[] coefficients;
    protected float[] unitNormal;
    protected float constant;
    // Only allocate once.
    protected float[] point;

    public HyperplaneTerm(Hyperplane<LocalVariable> hyperplane, int ruleIndex) {
        super(hyperplane, ruleIndex);

        this.coefficients = hyperplane.getCoefficients();
        this.constant = hyperplane.getConstant();
        this.point = new float[size];

        if (size >= 3) {
            // Finds a unit vector normal to the hyperplane and a point in the hyperplane for future projections.
            float length = 0.0f;
            for (int i = 0; i < size; i++) {
                length += coefficients[i] * coefficients[i];
            }
            length = (float)Math.sqrt(length);

            unitNormal = new float[size];
            for (int i = 0; i < size; i++) {
                unitNormal[i] = coefficients[i] / length;
            }
        } else {
            unitNormal = null;
        }
    }

    /**
     * Finds the orthogonal projection onto the hyperplane <br />
     * argmin stepSize/2 * \|x - z + y / stepSize \|_2^2 <br />
     * such that coefficients^T * x = constant.
     * <p>
     * Stores the result in x.
     */
    protected void project(float stepSize, float[] consensusValues) {
        // Deal with short hyperplanes specially.
        if (size == 1) {
            variables[0].setValue(constant / coefficients[0]);
            return;
        }

        // Deal with short hyperplanes specially.
        if (size == 2) {
            float x0;
            float x1;
            float coeff0 = coefficients[0];
            float coeff1 = coefficients[1];

            x0 = stepSize * consensusValues[variables[0].getGlobalId()] - variables[0].getLagrange();
            x0 -= stepSize * coeff0 / coeff1 * (-1.0 * constant / coeff1 + consensusValues[variables[1].getGlobalId()] - variables[1].getLagrange() / stepSize);
            x0 /= stepSize * (1.0 + coeff0 * coeff0 / coeff1 / coeff1);

            x1 = (constant - coeff0 * x0) / coeff1;

            variables[0].setValue(x0);
            variables[1].setValue(x1);

            return;
        }

        for (int i = 0; i < size; i++) {
            point[i] = consensusValues[variables[i].getGlobalId()] - variables[i].getLagrange() / stepSize;
        }

        // For point (constant / coefficients[0], 0,...) in hyperplane dotted with unitNormal,
        float multiplier = -1.0f * constant / coefficients[0] * unitNormal[0];

        for (int i = 0; i < size; i++) {
            multiplier += point[i] * unitNormal[i];
        }

        for (int i = 0; i < size; i++) {
            variables[i].setValue(point[i] - multiplier * unitNormal[i]);
        }
    }

    /**
     * coefficients^T * x - constant
     */
    @Override
    public float evaluate(TermStore termStore) {
        float value = 0.0f;
        for (int i = 0; i < size; i++) {
            value += coefficients[i] * variables[i].getValue();
        }

        return value - constant;
    }

    @Override
    public float evaluate(float[] consensusValues, TermStore termStore) {
        float value = 0.0f;
        for (int i = 0; i < size; i++) {
            value += coefficients[i] * consensusValues[variables[i].getGlobalId()];
        }

        return value - constant;
    }

    @Override
    public int fixedByteSize() {
        int bitSize = super.fixedByteSize();
        bitSize += Float.SIZE / 8; //constant
        for (int i = 0; i < size; i++){
            bitSize += Float.SIZE / 8; // coefficient
        }

        return bitSize;
    }

    @Override
    public void writeFixedValues(ByteBuffer fixedBuffer){
        super.writeFixedValues(fixedBuffer);
        fixedBuffer.putFloat(constant);

        for (int i = 0; i < size; i++) {
            fixedBuffer.putFloat(coefficients[i]);
        }
    }

    @Override
    public void read(ByteBuffer fixedBuffer, ByteBuffer volatileBuffer){
        super.read(fixedBuffer, volatileBuffer);
        constant = fixedBuffer.getFloat();

        // Make sure that there is enough room for all.
        if (point.length < size) {
            point = new float[size];
        }
        if (coefficients.length < size) {
            coefficients = new float[size];
        }
        if (unitNormal ==null || unitNormal.length < size) {
            unitNormal = new float[size];
        }

        for (int i = 0; i < size; i++) {
            coefficients[i] = fixedBuffer.getFloat();
        }

        if (size >= 3) {
            // Finds a unit vector normal to the hyperplane and a point in the hyperplane for future projections.
            float length = 0.0f;
            for (int i = 0; i < size; i++) {
                length += coefficients[i] * coefficients[i];
            }
            length = (float)Math.sqrt(length);

            for (int i = 0; i < size; i++) {
                unitNormal[i] = coefficients[i] / length;
            }
        }
    }

    @Override
    public boolean equals(Object o){
        if (o==null || !super.equals(o)){
            return false;
        }
        if (!(o instanceof HyperplaneTerm)){
            return false;
        }
        HyperplaneTerm oth = (HyperplaneTerm) o;
        if (oth.constant != constant){
            return false;
        }
        for (int i = 0; i < size ; i++){
            if (!MathUtils.equals(coefficients[i], oth.coefficients[i]) ||
                    !MathUtils.equals(point[i], oth.point[i]) ||
                    (size > 3 && !MathUtils.equals(unitNormal[i], oth.unitNormal[i]))){
                return false;
            }
        }
        return true;
    }
}

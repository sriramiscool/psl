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
import org.linqs.psl.reasoner.term.ReasonerTerm;
import org.linqs.psl.reasoner.term.TermStore;
import org.linqs.psl.util.MathUtils;

import java.nio.ByteBuffer;

/**
 * A term in the objective to be optimized by an ADMMReasoner.
 */
public abstract class ADMMObjectiveTerm implements ReasonerTerm {
//    protected final GroundRule groundRule;
    protected int ruleIndex;
    protected LocalVariable[] variables;
    protected int size;
    protected float[] coefficients;


    /**
     * Caller releases control of the hyperplane and all members of it.
     */
    public ADMMObjectiveTerm(Hyperplane<LocalVariable> hyperplane, int ruleIndex) {
        this.coefficients = hyperplane.getCoefficients();
        this.variables = hyperplane.getVariables();
        this.size = hyperplane.size();
        this.ruleIndex = ruleIndex;
    }

    public void updateLagrange(float stepSize, float[] consensusValues) {
        // Use index instead of iterator here so we can see clear results in the profiler.
        // http://psy-lob-saw.blogspot.co.uk/2014/12/the-escape-of-arraylistiterator.html
        for (int i = 0; i < size; i++) {
            LocalVariable variable = variables[i];
            variable.setLagrange(variable.getLagrange() + stepSize * (variable.getValue() - consensusValues[variable.getGlobalId()]));
        }
    }

    public float[] getCoefficients(){
        return coefficients;
    }

    public int getRuleIndex() {
        return ruleIndex;
    }

    /**
     * Can be 0 for linear loss and otherwise the constant.
     * @return
     */
    public abstract float getConstant();

    /**
     * Updates x to the solution of <br />
     * argmin f(x) + stepSize / 2 * \|x - z + y / stepSize \|_2^2 <br />
     * for the objective term f(x)
     */
    public abstract void minimize(float stepSize, float[] consensusValues, TermStore store);

    /**
     * Evaluate this potential using the local variables.
     */
    public abstract float evaluate(TermStore store);

    /**
     * Evaluate this potential using the given consensus values.
     */
    public abstract float evaluate(float[] consensusValues, TermStore store);

    /**
     * Get the variables used in this term.
     * The caller should not modify the returned array, and should check size() for a reliable length.
     */
    public LocalVariable[] getVariables() {
        return variables;
    }

    /**
     * Get the number of variables in this term.
     */
    @Override
    public int size() {
        return size;
    }

    public int fixedByteSize() {
        int bitSize =
                Integer.SIZE  // ruleIndex
                        + Integer.SIZE ; // size
        bitSize = bitSize / 8;
        for (int i = 0; i < size; i++){
            bitSize += variables[i].fixedByteSize();
        }
        for (int i = 0; i < size; i++){
            bitSize += Float.SIZE / 8; // coefficient
        }

        return bitSize;
    }

    public int volatileByteSize(){
        int bitSize = 0; // size
        for (int i = 0; i < size; i++){
            bitSize += variables[i].volatileByteSize();
        }
        return bitSize;
    }

    public void writeFixedValues(ByteBuffer fixedBuffer){
        fixedBuffer.putInt(size);
        fixedBuffer.putInt(ruleIndex);

        for (int i = 0; i < size; i++) {
            variables[i].writeFixedValues(fixedBuffer);
        }

        for (int i = 0; i < size; i++) {
            fixedBuffer.putFloat(coefficients[i]);
        }
    }

    public void writeVolatileValues(ByteBuffer volatileBuffer){
        for (int i = 0; i < size; i++) {
            variables[i].writeVolatileValues(volatileBuffer);
        }
    }

    public void read(ByteBuffer fixedBuffer, ByteBuffer volatileBuffer){
        size = fixedBuffer.getInt();
        ruleIndex = fixedBuffer.getInt();

        // Make sure that there is enough room for all these variableIndexes.
        if (variables.length < size) {
            variables = new LocalVariable[size];
        }

        for (int i = 0; i < size; i++) {
            if (variables[i] == null){
                variables[i] = new LocalVariable(-1,-1);
            }
            variables[i].read(fixedBuffer, volatileBuffer);
        }

        if (coefficients.length < size) {
            coefficients = new float[size];
        }
        for (int i = 0; i < size; i++) {
            coefficients[i] = fixedBuffer.getFloat();
        }
    }

    @Override
    public boolean equals(Object o){
        if (o==null || !(o instanceof ADMMObjectiveTerm)) {
            return false;
        }
        ADMMObjectiveTerm oth = (ADMMObjectiveTerm) o;
        if (ruleIndex != oth.ruleIndex || size != oth.size) {
            return false;
        }
        for (int i = 0; i < size; i++) {
            if (!variables[i].equals(oth.variables[i])) {
                return false;
            }
        }
        for (int i = 0; i < size ; i++){
            if (!MathUtils.equals(coefficients[i], oth.coefficients[i])){
                return false;
            }
        }
        return true;
    }

    @Override
    public String toString(){
        String out = "Size: " + size;
        out += ", Coefficients: ";
        for (int i = 0; i < size; i++) {
            out += coefficients[i] + ", ";
        }
        out += "Local variables: ";
        for (int i = 0; i < size; i++) {
            out += variables[i].toString() + ", ";
        }
        out += "ruleIndex: " + ruleIndex;
        return out;
    }
}

/*
 * This file is part of the PSL software.
 * Copyright 2011-2015 University of Maryland
 * Copyright 2013-2018 The Regents of the University of California
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
package org.linqs.psl.model.rule.logical;

import org.linqs.psl.model.atom.GroundAtom;
import org.linqs.psl.model.formula.Disjunction;
import org.linqs.psl.model.formula.Formula;
import org.linqs.psl.model.formula.Negation;
import org.linqs.psl.model.rule.GroundRule;
import org.linqs.psl.model.rule.Rule;
import org.linqs.psl.reasoner.function.ConstantNumber;
import org.linqs.psl.reasoner.function.FunctionSum;
import org.linqs.psl.reasoner.function.FunctionSummand;
import org.linqs.psl.util.HashCode;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Base class for all ground logical rules.
 */
public abstract class AbstractGroundLogicalRule implements GroundRule {
	protected final AbstractLogicalRule rule;
	protected final List<GroundAtom> posLiterals;
	protected final List<GroundAtom> negLiterals;
	protected final FunctionSum function;

	private final int hashcode;

	protected AbstractGroundLogicalRule(AbstractLogicalRule rule, List<GroundAtom> posLiterals, List<GroundAtom> negLiterals) {
		this.rule = rule;
		this.posLiterals = Collections.unmodifiableList(new ArrayList<GroundAtom>(posLiterals));
		this.negLiterals = Collections.unmodifiableList(new ArrayList<GroundAtom>(negLiterals));

		// Construct the hash code.
		int hash = HashCode.build(rule);

		// Construct function definition.
		function = new FunctionSum();

		for (int i = 0; i < posLiterals.size(); i++) {
			function.add(new FunctionSummand(1.0, posLiterals.get(i).getVariable()));
			hash = HashCode.build(hash, posLiterals.get(i));
		}

		for (int i = 0; i < negLiterals.size(); i++) {
			function.add(new FunctionSummand(-1.0, negLiterals.get(i).getVariable()));
			hash = HashCode.build(hash, negLiterals.get(i));
		}

		function.add(new FunctionSummand(1.0, new ConstantNumber(1.0 - posLiterals.size())));
		hashcode = hash;
	}

	protected FunctionSum getFunction() {
		return function;
	}

	@Override
	public Set<GroundAtom> getAtoms() {
		HashSet<GroundAtom> atoms = new HashSet<GroundAtom>();

		for (GroundAtom atom : posLiterals) {
			atoms.add(atom);
		}

		for (GroundAtom atom : negLiterals) {
			atoms.add(atom);
		}

		return atoms;
	}

	public double getTruthValue() {
		return 1 - Math.max(getFunction().getValue(), 0.0);
	}

	public List<GroundAtom> getPositiveAtoms() {
		return posLiterals;
	}

	public List<GroundAtom> getNegativeAtoms() {
		return negLiterals;
	}

	@Override
	public boolean equals(Object other) {
		if (other == this) {
			return true;
		}

		if (other == null
				|| !(other instanceof AbstractGroundLogicalRule)
				|| this.hashCode() != other.hashCode()) {
			return false;
		}

		AbstractGroundLogicalRule otherRule = (AbstractGroundLogicalRule)other;
		if (!rule.equals(otherRule.getRule())) {
			return false;
		}

		return posLiterals.equals(otherRule.posLiterals)
				&& negLiterals.equals(otherRule.negLiterals);
	}

	@Override
	public Rule getRule() {
		return rule;
	}

	@Override
	public int hashCode() {
		return hashcode;
	}

	@Override
	public String toString() {
		/* Negates the clause again to show clause to maximize truth of */
		Formula[] literals = new Formula[posLiterals.size() + negLiterals.size()];
		int i;

		for (i = 0; i < posLiterals.size(); i++) {
			literals[i] = new Negation(posLiterals.get(i));
		}

		for (int j = 0; j < negLiterals.size(); j++) {
			literals[i++] = negLiterals.get(j);
		}

		return (literals.length > 1) ? new Disjunction(literals).toString() : literals[0].toString();
	}
}

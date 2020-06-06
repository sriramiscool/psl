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
package org.linqs.psl.application.inference.mpe;

import org.linqs.psl.database.Database;
import org.linqs.psl.grounding.GroundRuleStore;
import org.linqs.psl.model.rule.Rule;
import org.linqs.psl.reasoner.Reasoner;
import org.linqs.psl.reasoner.admm.SGDADMMStreamingReasoner;
import org.linqs.psl.reasoner.admm.term.ADMMStreamingTermStore;
import org.linqs.psl.reasoner.term.TermGenerator;
import org.linqs.psl.reasoner.term.TermStore;

import java.util.List;

/**
 * Use streaming grounding and inference with a DCD reasoner.
 */
public class ADMMStreamingInference extends MPEInference {
    public ADMMStreamingInference(List<Rule> rules, Database db) {
        super(rules, db, true);
    }

    @Override
    protected Reasoner createReasoner() {
        return new SGDADMMStreamingReasoner();
    }

    @Override
    protected TermStore createTermStore() {
        return new ADMMStreamingTermStore(rules, atomManager);
    }

    @Override
    protected GroundRuleStore createGroundRuleStore() {
        return null;
    }

    @Override
    protected TermGenerator createTermGenerator() {
        return null;
    }

    @Override
    public void close() {
        termStore.close();
        reasoner.close();

        termStore = null;
        reasoner = null;

        rules = null;
        db = null;
    }

    @Override
    protected void completeInitialize() {
        // Do nothing. Specifically, do not ground.
    }
}

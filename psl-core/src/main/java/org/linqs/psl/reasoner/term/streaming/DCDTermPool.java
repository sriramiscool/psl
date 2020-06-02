package org.linqs.psl.reasoner.term.streaming;

import org.linqs.psl.reasoner.dcd.term.DCDObjectiveTerm;

/**
 * Created by sriramsrinivasan on 5/30/20.
 */
public class DCDTermPool extends AbstractTermPool<DCDObjectiveTerm> {
    public DCDTermPool(int pageSize) {
        super(pageSize, DCDObjectiveTerm.class);
    }
}

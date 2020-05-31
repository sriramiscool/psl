package org.linqs.psl.reasoner.term.streaming;

import org.linqs.psl.reasoner.sgd.term.SGDObjectiveTerm;

/**
 * Created by sriramsrinivasan on 5/30/20.
 */
public class SGDTermPool extends ArrayListTermPool<SGDObjectiveTerm> {
    public SGDTermPool(int pageSize) {
        super(pageSize);
    }
}

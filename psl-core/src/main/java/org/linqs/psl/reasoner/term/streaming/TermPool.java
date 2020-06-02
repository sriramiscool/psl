package org.linqs.psl.reasoner.term.streaming;

import org.linqs.psl.reasoner.term.ReasonerTerm;

/**
 * Created by sriramsrinivasan on 5/30/20.
 */
public interface TermPool <T extends ReasonerTerm> {
    public T get(Class type);
    public void clear();
    public void add(T term);
    public void close();
    public void resetForReuse();
}

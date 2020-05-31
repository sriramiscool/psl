package org.linqs.psl.reasoner.term.streaming;

import org.linqs.psl.reasoner.term.ReasonerTerm;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by sriramsrinivasan on 5/30/20.
 */
public class ArrayListTermPool<T extends ReasonerTerm> implements TermPool<T>{
    List<T> termPool;

    public ArrayListTermPool (int pageSize) {
        termPool = new ArrayList<>(pageSize);
    }
    @Override
    public T get(int i, Class type) {
        return termPool.get(i);
    }

    @Override
    public void clear() {
        if (termPool!=null) {
            termPool.clear();
        }
    }

    @Override
    public void add(T term) {
        termPool.add(term);
    }

    @Override
    public void close() {
        clear();
        termPool = null;
    }

}

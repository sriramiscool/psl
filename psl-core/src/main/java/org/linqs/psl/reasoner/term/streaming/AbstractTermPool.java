package org.linqs.psl.reasoner.term.streaming;

import org.linqs.psl.reasoner.term.ReasonerTerm;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Created by sriramsrinivasan on 5/30/20.
 */
public abstract class AbstractTermPool<T extends ReasonerTerm> implements TermPool<T>{
    private static final Logger log = LoggerFactory.getLogger(ADMMTermPool.class);

    private Map<Class, List<T>> termPool;
    private Map<Class, Integer> typeToIndex;
    private int pageSize;

    public AbstractTermPool(int pageSize, Class... classes) {
        this.pageSize = pageSize;
        termPool = new HashMap<>();
        typeToIndex = new HashMap<>();
        for (Class c: classes){
            termPool.put(c, new ArrayList<>(pageSize));
            typeToIndex.put(c, 0);
        }

    }


    /*
    User has to reset after using for a page.
     */
    @Override
    public T get(Class type) {
        if (typeToIndex.get(type) >= termPool.get(type).size()){
            throw new RuntimeException("Please reset before reuse. idx is " + typeToIndex.get(type) +
                    " and size of pool is " + termPool.get(type).size() + ", for class " + type);
        }
        T admmObjectiveTerm = termPool.get(type).get(typeToIndex.get(type));
        typeToIndex.put(type, typeToIndex.get(type)+1);
        return admmObjectiveTerm;
    }

    public void resetForReuse(){
        for (Class c : typeToIndex.keySet()) {
            typeToIndex.put(c, 0);
        }
    }

    @Override
    public void clear() {
        if (termPool != null) {
            for (Class c : termPool.keySet()) {
                termPool.get(c).clear();
            }
        }
        if (typeToIndex != null){
            resetForReuse();
        }
    }

    /*
    Assuming person using will reset every page. Else we will end up filling up terms for all types.
     */
    @Override
    public void add(T term) {
        if (typeToIndex.get(term.getClass()) == pageSize) {
            log.warn("Pool is full have enough to fill a page. So ignoring adding to pool.");
            return;
        }
        typeToIndex.put(term.getClass(), typeToIndex.get(term.getClass())+1);
        if (typeToIndex.get(term.getClass()) <= termPool.get(term.getClass()).size()) {
            return;
        }
        termPool.get(term.getClass()).add(term);
    }

    @Override
    public void close() {
        clear();
        termPool = null;
        typeToIndex = null;
    }
}

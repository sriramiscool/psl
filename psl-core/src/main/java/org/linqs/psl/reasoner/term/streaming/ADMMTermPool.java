package org.linqs.psl.reasoner.term.streaming;

import org.linqs.psl.reasoner.admm.term.*;

import java.util.*;

/**
 * Created by sriramsrinivasan on 5/30/20.
 */
public class ADMMTermPool implements TermPool<ADMMObjectiveTerm> {

    public static final Map<Integer, Class> termTypeToClass;
    public static final Map<Class, Integer> classToTermType;
    static {
        Map<Integer, Class> termTypeToClassTemp = new HashMap<>();
        Map<Class, Integer> classToTermTypeTemp = new HashMap<>();
        termTypeToClassTemp.put(0, SquaredHingeLossTerm.class);
        termTypeToClassTemp.put(1, SquaredLinearLossTerm.class);
        termTypeToClassTemp.put(2, HingeLossTerm.class);
        termTypeToClassTemp.put(3, LinearLossTerm.class);
        termTypeToClassTemp.put(4, LinearConstraintTerm.class);
        classToTermTypeTemp.put(SquaredHingeLossTerm.class, 0);
        classToTermTypeTemp.put(SquaredLinearLossTerm.class, 1);
        classToTermTypeTemp.put(HingeLossTerm.class, 2);
        classToTermTypeTemp.put(LinearLossTerm.class, 3);
        classToTermTypeTemp.put(LinearConstraintTerm.class, 4);
        termTypeToClass = Collections.unmodifiableMap(termTypeToClassTemp);
        classToTermType = Collections.unmodifiableMap(classToTermTypeTemp);

    }
    private Map<Class, List<ADMMObjectiveTerm>> termPool;

    public ADMMTermPool(int pageSize) {
        termPool = new HashMap<>();
        for (Class c: classToTermType.keySet()){
            termPool.put(c, new ArrayList<>(pageSize));
        }
    }


    @Override
    public ADMMObjectiveTerm get(int i, Class type) {
        return termPool.get(type).get(i);
    }

    @Override
    public void clear() {
        if (termPool != null) {
            for (Class c : termPool.keySet()) {
                termPool.get(c).clear();
            }
        }
    }

    @Override
    public void add(ADMMObjectiveTerm term) {
        termPool.get(term.getClass()).add(term);
    }

    @Override
    public void close() {
        clear();
        termPool = null;
    }
}

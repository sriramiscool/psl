package org.linqs.psl.reasoner.term.streaming;

import org.linqs.psl.reasoner.admm.term.*;

import java.util.*;

/**
 * Created by sriramsrinivasan on 5/30/20.
 */
public class ADMMTermPool extends AbstractTermPool<ADMMObjectiveTerm> {

    public static final Map<Integer, Class> termTypeToClass;
    public static final Map<Class, Integer> classToTermType;
    private static final Class[] possibleClasses;
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
        possibleClasses = new Class[]{SquaredHingeLossTerm.class, SquaredLinearLossTerm.class,
                HingeLossTerm.class, LinearLossTerm.class, LinearConstraintTerm.class};

    }
    public ADMMTermPool(int pageSize) {
        super(pageSize, possibleClasses);
    }


}

package org.linqs.psl.reasoner.admm.term;

import java.util.List;

/**
 * Created by sriramsrinivasan on 4/6/18.
 */
public class AugmentedLinearLossTerm extends LinearLossTerm {
    /**
     * Caller releases control of |variables| and |coeffs|.
     *
     * @param variables
     * @param coeffs
     * @param weight
     */
    public AugmentedLinearLossTerm(List<LocalVariable> variables, List<Float> coeffs, float weight) {
        super(variables, coeffs, weight);
    }
}

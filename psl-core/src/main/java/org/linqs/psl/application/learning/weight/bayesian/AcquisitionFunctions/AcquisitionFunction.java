package org.linqs.psl.application.learning.weight.bayesian.AcquisitionFunctions;

import org.linqs.psl.application.learning.weight.bayesian.GaussianProcessPrior;

import java.util.List;

/**
 * Created by sriramsrinivasan on 3/16/19.
 */
public interface AcquisitionFunction {
    //Exploration strategy
    public int getNextPoint(List<GaussianProcessPrior.WeightConfig> configs, int iter);
}

package org.linqs.psl.application.learning.weight.bayesian.AcquisitionFunctions;

import org.jblas.util.Random;
import org.linqs.psl.application.learning.weight.bayesian.GaussianProcessPrior;

import java.util.List;

/**
 * Created by sriramsrinivasan on 3/17/19.
 */
public class ThompsonSampling implements AcquisitionFunction {
    @Override
    public int getNextPoint(List<GaussianProcessPrior.WeightConfig> configs, int iter) {
        int bestConfig = -1;
        float curBestVal = -Float.MAX_VALUE;
        for (int i = 0; i < configs.size(); i++) {
            float curVal = (float) Random.nextGaussian();
            curVal = (configs.get(i).valueAndStd.value) +
                    curVal * configs.get(i).valueAndStd.std;
            if(curBestVal < curVal){
                curBestVal = curVal;
                bestConfig = i;
            }
        }
        return bestConfig;
    }
}

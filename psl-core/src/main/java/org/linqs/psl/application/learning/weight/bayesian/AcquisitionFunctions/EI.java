package org.linqs.psl.application.learning.weight.bayesian.AcquisitionFunctions;

import org.linqs.psl.application.learning.weight.bayesian.GaussianProcessPrior;
import org.linqs.psl.config.Config;

import java.util.List;

/**
 * Created by sriramsrinivasan on 3/16/19.
 */
public class EI implements AcquisitionFunction {
    private float exploration;
    private static final String CONFIG_PREFIX = "gpp";
    private static final String EXPLORATION = ".explore";
    private static final float EXPLORATION_VAL = 0.0f;

    public EI(){
        exploration = Config.getFloat(CONFIG_PREFIX+EXPLORATION, EXPLORATION_VAL);
    }
    //Exploration strategy
    public int getNextPoint(List<GaussianProcessPrior.WeightConfig> configs, int iter){
        int bestConfig = -1;
        float curBestVal = -Float.MAX_VALUE;
        for (int i = 0; i < configs.size(); i++) {
            final float expec = configs.get(i).valueAndStd.value - exploration;
            final float std = configs.get(i).valueAndStd.std;
            final float x = expec / std;
            float curVal = expec*DistUtils.CNDF(x) + std*DistUtils.NDF(x);
            if(curBestVal < curVal){
                curBestVal = curVal;
                bestConfig = i;
            }
        }
        return bestConfig;
    }
}

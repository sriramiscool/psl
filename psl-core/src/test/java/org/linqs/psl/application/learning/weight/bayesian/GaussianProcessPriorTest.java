package org.linqs.psl.application.learning.weight.bayesian;

import com.google.common.collect.Lists;
import org.jblas.FloatMatrix;
import org.jblas.Solve;
import org.junit.Assert;
import org.junit.Test;
import org.linqs.psl.application.learning.weight.WeightLearningApplication;
import org.linqs.psl.application.learning.weight.WeightLearningTest;
import org.linqs.psl.config.Config;
import org.linqs.psl.database.Database;
import org.linqs.psl.model.rule.Rule;
import org.linqs.psl.model.rule.WeightedRule;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;


public class GaussianProcessPriorTest extends WeightLearningTest {
    @Override
    protected WeightLearningApplication getWLA() {
        return new GaussianProcessPrior(this.info.model.getRules(), weightLearningTrainDB, weightLearningTruthDB);
    }
    protected WeightLearningApplication getWLALocal(){
        return new GPPTest(this.info.model.getRules(), weightLearningTrainDB, weightLearningTruthDB);
    }
    @Test
    public void testGetNext(){
        GaussianProcessPrior wl = (GaussianProcessPrior) getWLA();
        List<Float> yPred = Lists.newArrayList(0.5f,0.4f,0.6f,0.7f);
        List<Float> yStd = Lists.newArrayList(0.2f,0.7f,0.3f,0.1f);
        List<GaussianProcessPrior.WeightConfig> weightConfigs = Lists.newArrayList();
        for (int i = 0; i < yPred.size(); i++) {
            weightConfigs.add(new GaussianProcessPrior.WeightConfig(null, yPred.get(i), yStd.get(i)));
        }
        Assert.assertEquals(1, wl.getNextPoint(weightConfigs, 1));
    }
    @Test
    public void testGetConfigs() throws NoSuchMethodException, InvocationTargetException, IllegalAccessException {
        Config.addProperty("gpp.maxconfigs", 5);
        Config.addProperty("gpp.randomConfigsOnly", false);
        GaussianProcessPrior wl = (GaussianProcessPrior) getWLA();
        Method method = wl.getClass().getDeclaredMethod("getConfigs", null);
        method.setAccessible(true);
        List<GaussianProcessPrior.WeightConfig> configs = (List<GaussianProcessPrior.WeightConfig>) method.invoke(wl);
        List<float[]> expected = Lists.newArrayList();
        expected.add(new float[]{0.0f,0.0f,0.0f});
        expected.add(new float[]{1.0f,0.0f,0.0f});
        expected.add(new float[]{0.0f,1.0f,0.0f});
        expected.add(new float[]{1.0f,1.0f,0.0f});
        expected.add(new float[]{0.0f,0.0f,1.0f});
        expected.add(new float[]{1.0f,0.0f,1.0f});
        expected.add(new float[]{0.0f,1.0f,1.0f});
        expected.add(new float[]{1.0f,1.0f,1.0f});
        for (int i = 0; i < configs.size(); i++) {
            for (int j = 0; j < configs.get(i).config.length; j++) {
                Assert.assertEquals(expected.get(i)[j], configs.get(i).config[j], 1e-5);
            }
        }
    }
    @Test
    public void testPredictFnValAndStd() throws NoSuchFieldException, IllegalAccessException {
        Config.addProperty("gppker.reldep", 100);
        Config.addProperty("gppker.scale", 1.0);
        Config.addProperty("gppker.space", "OS");
        GaussianProcessPrior wl = (GaussianProcessPrior) getWLA();
        Field field = wl.getClass().getDeclaredField("knownDataStdInv");
        field.setAccessible(true);
        FloatMatrix inverseMat = new FloatMatrix(3,3);
        inverseMat.put(0,0,1);
        inverseMat.put(0,1,1f);
        inverseMat.put(0,2,0.9999f);
        inverseMat.put(1,0,0.9999f);
        inverseMat.put(1,1,1);
        inverseMat.put(1,2,0.9999f);
        inverseMat.put(2,0,0.9999f);
        inverseMat.put(2,1,0.9999f);
        inverseMat.put(2,2,1);
        inverseMat = Solve.solve(inverseMat, FloatMatrix.eye(3));
        field.set(wl, inverseMat);
        //predict(np.array([.4,.2,.1]),[[.1,.2,.3],[.2,.2,.1],[.4,.3,.2]],
        // altExp, θ, np.array([[1,1,0.9999],[0.9999,1,0.9999],[0.9999,0.9999,1]]), [.5,.6,.7])
        float[] x = new float[]{0.4f,0.2f,0.1f};
        List<GaussianProcessPrior.WeightConfig> xKnown = Lists.newArrayList();
        xKnown.add(new GaussianProcessPrior.WeightConfig(new float[]{.1f,.2f,.3f}));
        xKnown.add(new GaussianProcessPrior.WeightConfig(new float[]{.2f,.2f,.1f}));
        xKnown.add(new GaussianProcessPrior.WeightConfig(new float[]{.4f,.3f,.2f}));
        float[] yKnown = new float[]{.5f,.6f,.7f};
        FloatMatrix blasYKnown = new FloatMatrix(yKnown);
        Field kernel = wl.getClass().getDeclaredField("kernel");
        kernel.setAccessible(true);
        kernel.set(wl, GaussianProcessKernels.kernelProvider(GaussianProcessKernels.SQUARED_EXP, wl));
        GaussianProcessPrior.ValueAndStd fnAndStd = wl.predictFnValAndStd(x, xKnown, blasYKnown);
        Assert.assertEquals(0.84939,fnAndStd.value, 1e-5);
        Assert.assertEquals(0.99656, fnAndStd.std, 1e-5);
    }
    @Test
    public void testDoLearn(){
        Config.addProperty("gppker.reldep", 1);
        Config.addProperty("gppker.space", "OS");
        Config.addProperty("gpp.maxconfigs", 5);
        Config.addProperty("gpp.maxiter", 3);
        Config.addProperty("gpp.kernel", GaussianProcessKernels.SQUARED_EXP);
        Config.addProperty("gpp.randomConfigsOnly", false);
        GaussianProcessPrior wl = (GaussianProcessPrior) getWLALocal();
        wl.doLearn();
        for (int i = 0; i < 3; i++) {
            Assert.assertEquals(1.0f, ((WeightedRule)this.info.model.getRules().get(i)).getWeight(), 1e-30);
        }

    }
    private class GPPTest extends GaussianProcessPrior{

        public GPPTest(List<Rule> rules, Database rvDB, Database observedDB) {
            super(rules, rvDB, observedDB);
        }

        @Override
        public float getFunctionValue(WeightConfig config){
            if (config.config[0] == 1.0 && config.config[1] == 1.0 && config.config[2] == 1.0){
                return 0.5f;
            }
            if (config.config[0] == 0.0 && config.config[1] == 1.0 && config.config[2] == 1.0){
                return 0.6f;
            }
            return 0.3f;
        }
    }
}

package org.linqs.psl.reasoner.marginals;

import org.linqs.psl.reasoner.admm.ADMMReasoner;
import org.linqs.psl.reasoner.admm.term.ADMMTermGenerator;
import org.linqs.psl.reasoner.admm.term.ADMMTermStore;
import org.linqs.psl.reasoner.function.AtomFunctionVariable;
import org.linqs.psl.reasoner.marginals.term.MarginalTermStore;
import org.linqs.psl.reasoner.term.TermStore;
import org.linqs.psl.util.RandUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This class will compute marginal using Metropolis-in-Gibbs sampling.
 * The samples will be stored in a file and mean of samples will be stored
 * in the database.
 *
 * for i in tqdm_notebook(range(num_rounds-1)):
     samples_mig[i+1] = samples_mig[i]
     for j in range(data.n_cols):
         prev_y = samples_mig[i+1]
         new_y = prev_y.copy()
         new_y[j] = np.random.uniform(0,1)
         log_alpha = get_energy(prev_y, data, j) - get_energy(new_y, data, j)
         if np.log(np.random.uniform(0,1)) < log_alpha:
         alphas[i+1, j] = 1
         samples_mig[i+1][j] = new_y[j]
     energies[i+1, j] = get_energy(samples_mig[i+1], data, j)
 *
 *
 * Created by sriramsrinivasan on 6/23/19.
 */
public class GibbsMarginalReasoner extends AbstractMarginalsReasoner {
    private static final Logger log = LoggerFactory.getLogger(GibbsMarginalReasoner.class);

    @Override
    public void optimize(TermStore termStore) {
        MarginalTermStore marginalTermStore = (MarginalTermStore) termStore;
        final int numVariables = marginalTermStore.getNumVariables();
        final float approx_size_in_mem = numVariables * num_samples * 4/(1024*1024*1024);
        final float RAM_size = Runtime.getRuntime().maxMemory()/(1024*1024*1024);
        if (approx_size_in_mem > 0.5 * RAM_size && approx_size_in_mem < 0.95 * RAM_size ){
            log.warn("The array size required to store data is {}GB which is more than half RAM size {}GB",
                    approx_size_in_mem, RAM_size);
        }
        if (approx_size_in_mem >= 0.95*RAM_size) {
            log.error("Array to be created is larger than RAM size. Killing task. " +
                    "Memory required {}GB and RAM size {}GB", approx_size_in_mem, RAM_size);
            throw new RuntimeException(String.format("Array size in memory {}GB and RAM size {}GB",
                    approx_size_in_mem, RAM_size));
        }
        writeVariableIdAndName(marginalTermStore);
        float[][] samples = new float[num_samples][numVariables];
        if(map_init) {
            computeMapState(marginalTermStore);
            for (int i = 0; i < numVariables; i++) {
                samples[0][i] = (float)marginalTermStore.getVariable(i).getValue();
            }
        } else {
            for (int i = 0; i < numVariables; i++) {
                samples[0][i] = RandUtils.nextFloat();
            }
            marginalTermStore.updateVariables(samples[0]);
        }
        log.info("Starting to run Gibbs sampler. num samples:{}, number of variables: {}",
                num_samples, numVariables);
        int num_accepts = 0;
        for (int i = 1; i < num_samples; i++) {
            for (int j = 0; j < numVariables; j++) {
                AtomFunctionVariable var = marginalTermStore.getVariable(j);
                float prev_val = (float) var.getValue();
                float prev_energy = getEnergy(marginalTermStore, var);
                float new_val = RandUtils.nextFloat();
                var.setValue((double) new_val);
                samples[i][j] = new_val;
                float new_energy = getEnergy(marginalTermStore, var);
                float log_alpha = prev_energy - new_energy;
                num_accepts++;
                //if reject then replace value to previous.
                if (log_alpha <= Math.log(RandUtils.nextFloat())) {
                    var.setValue(prev_val);
                    samples[i][j] = prev_val;
                    num_accepts--;
                }
            }
        }
        log.info("Completed running marginals: acceptance ratio = {}",
                ((float)num_accepts/((float)num_samples* numVariables)));
        writeSamples(samples, marginalTermStore);
        float[] expectation = getAverages(numVariables, samples);
        marginalTermStore.updateVariables(expectation);

    }

    private void computeMapState(MarginalTermStore marginalTermStore) {
        ADMMReasoner admmReasoner = new ADMMReasoner();
        ADMMTermStore admmTermStore = new ADMMTermStore();
        ADMMTermGenerator admmTermGenerator = new ADMMTermGenerator();
        final int admmTermsGenerated = admmTermGenerator.generateTerms(marginalTermStore.getRuleStore(),
                admmTermStore);
        admmReasoner.optimize(admmTermStore);
        admmReasoner.close();
        admmTermStore.close();
    }


    @Override
    public void close() {
    }
}

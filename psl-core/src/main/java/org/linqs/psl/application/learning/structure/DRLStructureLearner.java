package org.linqs.psl.application.learning.structure;

import org.deeplearning4j.rl4j.learning.sync.qlearning.QLearning;
import org.deeplearning4j.rl4j.learning.sync.qlearning.discrete.QLearningDiscreteDense;
import org.deeplearning4j.rl4j.network.dqn.DQNFactoryStdDense;
import org.deeplearning4j.rl4j.util.DataManager;
import org.linqs.psl.application.learning.structure.mdp.State;
import org.linqs.psl.application.learning.structure.mdp.StructureLearner;
import org.linqs.psl.application.learning.structure.rulegen.DRLRuleGenerator;
import org.linqs.psl.application.learning.structure.rulegen.LocalRandomRuleGenerator;
import org.linqs.psl.application.learning.structure.rulegen.PathRandomRuleGenerator;
import org.linqs.psl.application.learning.structure.rulegen.SimRandomRuleGenerator;
import org.linqs.psl.config.Config;
import org.linqs.psl.database.Database;
import org.linqs.psl.model.predicate.StandardPredicate;
import org.linqs.psl.model.rule.Rule;
import org.linqs.psl.model.rule.WeightedRule;
import org.nd4j.linalg.learning.config.Adam;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;


/**
 * Created by varunembar on 03/20/20.
 */

public class DRLStructureLearner extends AbstractStructureLearningApplication{

    private static final Logger log = LoggerFactory.getLogger(DRLStructureLearner.class);

    private static final String CONFIG_PREFIX = "drlsl";
    private static final String ITERATIONS_KEY = CONFIG_PREFIX + ".iter";
    private static final int ITERATIONS_DEFAULT = 1;
    private static final String MAX_RULE_LEN_KEY = CONFIG_PREFIX + ".rulelen";
    private static final int MAX_RULE_LEN_DEFAULT = 4;
    private static final String NUM_RULES_KEY = CONFIG_PREFIX + ".numrules";
    private static final int NUM_RULES_DEFAULT = 5;
    private static final int NUM_TRIES_PER_RULE_DEFAULT = 5;

    private int numIte;
    protected Map<StandardPredicate, Integer> predicateToId;
    protected Map<Integer, StandardPredicate> idToPredicate;
    private int numRules;
    protected Map<Integer, DRLRuleGenerator> idToTemplate;
    private Set<StandardPredicate> openPredicates;
    private int maxRuleLen;

    private int numTemplates;
    private int numPredicates;

    private StructureLearner mdp;
    private QLearningDiscreteDense<State> dql;
    private CustomDLPolicy<State> pol;

    public DRLStructureLearner(List<Rule> rules, Database rvDB, Database observedDB,
                                  Set<StandardPredicate> closedPredicates,
                                  Set<StandardPredicate> openPredicates) {
        super(rules, rvDB, observedDB, closedPredicates, openPredicates);
        this.numIte = Config.getInt(ITERATIONS_KEY, ITERATIONS_DEFAULT);
        this.numRules = Config.getInt(NUM_RULES_KEY, NUM_RULES_DEFAULT);
        this.maxRuleLen = Config.getInt(MAX_RULE_LEN_KEY, MAX_RULE_LEN_DEFAULT);
        this.predicateToId = new HashMap<>();
        this.idToPredicate = new HashMap<>();
        for (int i = 0; i < this.predicates.size() ; i++) {
            this.predicateToId.put(this.predicates.get(i), i);
        }
        this.idToTemplate = new HashMap<>();
        
        this.idToTemplate.put(0, new PathRandomRuleGenerator(closedPredicates, openPredicates));
        this.idToTemplate.put(1, new SimRandomRuleGenerator(closedPredicates, openPredicates));
        this.idToTemplate.put(2, new LocalRandomRuleGenerator(closedPredicates, openPredicates));

        this.numTemplates = idToTemplate.size();
        this.numPredicates = this.predicates.size();
        this.openPredicates = openPredicates;

        for (int i = 0; i < this.predicates.size() ; i++) {
            this.predicateToId.put(this.predicates.get(i), i + numTemplates);
            this.idToPredicate.put(i + numTemplates, this.predicates.get(i));
        }

        this.mdp = new StructureLearner(this.NUM_RULES_DEFAULT, this.MAX_RULE_LEN_DEFAULT, this.idToTemplate, this.idToPredicate, this.openPredicates);

        DataManager manager = null;
        try {
            manager = new DataManager();
        } catch (IOException e) {
            log.error(e.toString());
        }
        //define the training method
        QLearning.QLConfiguration.QLConfigurationBuilder psl_ql = QLearning.QLConfiguration.builder();
        QLearning.QLConfiguration qlConfiguration = psl_ql.batchSize(12).maxEpochStep(20).maxStep(50000).
                expRepMaxSize(1).batchSize(32).targetDqnUpdateFreq(32).updateStart(0).rewardFactor(1).
                gamma(0.98).errorClamp(10000).minEpsilon(0.1f).epsilonNbStep(3000).doubleDQN(true).build();

        this.dql = new QLearningDiscreteDense<State>(this.mdp, PSL_NET, qlConfiguration, manager);
        //Learning<State, Integer, DiscreteSpace, IDQN> dql = new QLearningDiscreteDense<State>(mdp, PSL_NET, qlConfiguration, manager);

        //enable some logging for debug purposes on toy mdp
        this.mdp.setFetchable(this.dql);

        //get policy
        //DQNPolicy<State> pol = dql.getPolicy();
        this.pol = new CustomDLPolicy<>(this.dql.getPolicy().getNeuralNet(), this.mdp);
    }

    public static DQNFactoryStdDense.Configuration PSL_NET =
            DQNFactoryStdDense.Configuration.builder()
                    .l2(0.01).updater(new Adam(1e-2)).numLayer(2).numHiddenNodes(32).build();

    @Override
    protected void doLearn() {

        for (int i = 0; i < this.numIte; i++) {
            try {
                this.populateNextRulesOfModel();
            } catch (IOException e) {
                log.error("Unable to create Datamanager");
            }
            double metric = this.evaluator.getRepresentativeMetric();
            if (!this.evaluator.isHigherRepresentativeBetter()){
                metric *= -1;
            }
            if (metric > this.bestValueForRulesSoFar){
                this.bestValueForRulesSoFar = metric;
                this.bestRulesSoFar.clear();
                this.bestRulesSoFar.addAll(this.allRules);
                log.debug("Got a better model with metric: " + metric);

            }
        }

    }

    private void populateNextRulesOfModel() throws IOException {
        this.resetModel();

        //start the training
        dql.train();

        //evaluate the agent
        double rewards = 0;
        mdp.reset();
        double reward = pol.play(mdp);
        for(WeightedRule r: mdp.getState().getRules()){
            r.setWeight(1.0);
            this.addRuleToModel(r);
        }
        //Currently does nothing. Have to be careful with this if something is added.
        mdp.close();
        mdp.reset();

        //Learn weights
        //this.getNewWeightLearner().learn();
        //For now set weights to 1.
        for(WeightedRule r: this.mutableRules) {
            r.setWeight(1);
        }
        this.evaluator.compute(trainingMap);
    }

}

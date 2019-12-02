package org.linqs.psl.application.learning.structure;

import org.linqs.psl.application.ModelApplication;
import org.linqs.psl.application.learning.weight.TrainingMap;
import org.linqs.psl.application.learning.weight.WeightLearningApplication;
import org.linqs.psl.application.learning.weight.maxlikelihood.MaxLikelihoodMPE;
import org.linqs.psl.config.Config;
import org.linqs.psl.database.Database;
import org.linqs.psl.database.atom.PersistedAtomManager;
import org.linqs.psl.evaluation.statistics.DiscreteEvaluator;
import org.linqs.psl.evaluation.statistics.Evaluator;
import org.linqs.psl.grounding.GroundRuleStore;
import org.linqs.psl.grounding.Grounding;
import org.linqs.psl.grounding.MemoryGroundRuleStore;
import org.linqs.psl.model.Model;
import org.linqs.psl.model.atom.GroundAtom;
import org.linqs.psl.model.atom.ObservedAtom;
import org.linqs.psl.model.atom.RandomVariableAtom;
import org.linqs.psl.model.predicate.StandardPredicate;
import org.linqs.psl.model.rule.GroundRule;
import org.linqs.psl.model.rule.Rule;
import org.linqs.psl.model.rule.WeightedRule;
import org.linqs.psl.reasoner.Reasoner;
import org.linqs.psl.reasoner.admm.ADMMReasoner;
import org.linqs.psl.reasoner.admm.term.ADMMTermGenerator;
import org.linqs.psl.reasoner.admm.term.ADMMTermStore;
import org.linqs.psl.reasoner.term.TermGenerator;
import org.linqs.psl.reasoner.term.TermStore;
import org.linqs.psl.util.Reflection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.*;

/**
 * Created by sriramsrinivasan on 10/30/19.
 */
public abstract class AbstractStructureLearningApplication implements ModelApplication {
    private static final Logger log = LoggerFactory.getLogger(AbstractStructureLearningApplication.class);

    /**
     * Prefix of property keys used by this class.
     */
    public static final String CONFIG_PREFIX = "stucturelearning";

    /**
     * The class to use for inference.
     */
    public static final String REASONER_KEY = CONFIG_PREFIX + ".reasoner";
    public static final String REASONER_DEFAULT = ADMMReasoner.class.getName();

    /**
     * The class to use for ground rule storage.
     */
    public static final String GROUND_RULE_STORE_KEY = CONFIG_PREFIX + ".groundrulestore";
    public static final String GROUND_RULE_STORE_DEFAULT = MemoryGroundRuleStore.class.getName();

    /**
     * The class to use for term storage.
     * Should be compatible with REASONER_KEY.
     */
    public static final String TERM_STORE_KEY = CONFIG_PREFIX + ".termstore";
    public static final String TERM_STORE_DEFAULT = ADMMTermStore.class.getName();

    /**
     * The class to use for term generator.
     * Should be compatible with REASONER_KEY and TERM_STORE_KEY.
     */
    public static final String TERM_GENERATOR_KEY = CONFIG_PREFIX + ".termgenerator";
    public static final String TERM_GENERATOR_DEFAULT = ADMMTermGenerator.class.getName();

    /**
     * An evalautor capable of producing a score for the current weight configuration.
     * Child methods may use this at their own discrection.
     * This is only used for logging/information, and not for gradients.
     */
    public static final String EVALUATOR_KEY = CONFIG_PREFIX + ".evaluator";
    public static final String EVALUATOR_DEFAULT = DiscreteEvaluator.class.getName();


    /**
     * The class to use for weight learning.
     */
    public static final String WLEARNER_KEY = CONFIG_PREFIX + ".wlearning";
    public static final String WLEARNER_DEFAULT = MaxLikelihoodMPE.class.getName();
    protected final List<StandardPredicate> predicates;
    protected final Set<StandardPredicate> openPredicates;
    protected final Set<StandardPredicate> closedPredicates;


    protected Database rvDB;
    protected Database observedDB;

    /**
     * An atom manager on top of the rvDB.
     */
    protected PersistedAtomManager atomManager;

    protected List<Rule> allRules;
    protected List<WeightedRule> mutableRules;
    protected List<Rule> bestRulesSoFar;
    protected List<Rule> initialListOfRules;
    protected double bestValueForRulesSoFar;

    protected TrainingMap trainingMap;

    protected Reasoner reasoner;
    //TODO: this is very specific right now. Needs to become more general so we can use any weight learning.
    private MaxLikelihoodMPE weightLearner;
    protected GroundRuleStore groundRuleStore;
    protected TermGenerator termGenerator;
    protected TermStore termStore;

    protected Evaluator evaluator;

    private boolean groundModelInit;

    /**
     * Flags to track if the current variable configuration is an MPE state.
     * This will get set to true when computeMPEState is called,
     * but besides that it is up to children to set to false when weights are changed.
     */
    protected boolean inMPEState;

    public AbstractStructureLearningApplication(List<Rule> rules, Database rvDB, Database observedDB,
                                                Set<StandardPredicate> closedPredicates,
                                                Set<StandardPredicate> openPredicates) {
        this.rvDB = rvDB;
        this.observedDB = observedDB;
        List<StandardPredicate> predicates = new ArrayList<>();
        this.initialListOfRules = new ArrayList<>();
        for (StandardPredicate pred: closedPredicates){
            predicates.add(pred);
        }
        for (StandardPredicate pred: openPredicates){
            predicates.add(pred);
        }
        this.predicates = Collections.unmodifiableList(predicates);
        this.closedPredicates = Collections.unmodifiableSet(closedPredicates);
        this.openPredicates = Collections.unmodifiableSet(openPredicates);


        allRules = new ArrayList<Rule>();
        mutableRules = new ArrayList<WeightedRule>();
        this.bestRulesSoFar = new ArrayList<>();
        this.bestRulesSoFar.addAll(rules);

        for (Rule rule : rules) {
            allRules.add(rule);
            initialListOfRules.add(rule);

            if (rule instanceof WeightedRule) {
                mutableRules.add((WeightedRule)rule);
            }
        }

        groundModelInit = false;
        inMPEState = false;
        this.bestValueForRulesSoFar = Double.NEGATIVE_INFINITY;

        evaluator = (Evaluator) Config.getNewObject(EVALUATOR_KEY, EVALUATOR_DEFAULT);
        //Avoid persistentatommanager exception that is thrown if target data is not complete.
        Config.addProperty(PersistedAtomManager.THROW_ACCESS_EXCEPTION_KEY, false);
        Config.addProperty(MaxLikelihoodMPE.NUM_STEPS_KEY, 10);
        Config.addProperty(ADMMReasoner.MAX_ITER_KEY, 100);
    }

    /**
     * Learns new Rules.
     * <p>
     * The {@link RandomVariableAtom RandomVariableAtoms} in the distribution are those
     * persisted in the random variable Database when this method is called. All
     * RandomVariableAtoms which the Model might access must be persisted in the Database.
     * <p>
     * Each such RandomVariableAtom should have a corresponding {@link ObservedAtom}
     * in the observed Database, unless the subclass implementation supports latent
     * variables.
     */
    public void learn() {
        // Sets up the ground model.
        initGroundModel();

        // Learns new weights.
        doLearn();
    }

    /**
     * Do the actual learning procedure.
     */
    protected abstract void doLearn();


    public GroundRuleStore getGroundRuleStore() {
        return groundRuleStore;
    }

    /**
     * Initialize all the infrastructure dealing with the ground model.
     * Children should favor overriding postInitGroundModel() instead of this.
     */
    protected void initGroundModel() {
        if (groundModelInit) {
            return;
        }

        PersistedAtomManager atomManager = createAtomManager();

        // Ensure all targets from the observed (truth) database exist in the RV database.
        ensureTargets(atomManager);

        GroundRuleStore groundRuleStore = (GroundRuleStore)Config.getNewObject(GROUND_RULE_STORE_KEY, GROUND_RULE_STORE_DEFAULT);

        log.info("Grounding out model.");
        int groundCount = Grounding.groundAll(allRules, atomManager, groundRuleStore);

        initGroundModel(atomManager, groundRuleStore);
    }

    /**
     * Init the ground model using an already populated ground rule store.
     * All the targets from the obserevd database should already exist in the RV database
     * before this ground rule store was populated.
     * This means that this variant will not call ensureTargets() (unlike the no parameter variant).
     */
    public void initGroundModel(GroundRuleStore groundRuleStore) {
        if (groundModelInit) {
            return;
        }

        initGroundModel(createAtomManager(), groundRuleStore);
    }

    private void initGroundModel(PersistedAtomManager atomManager, GroundRuleStore groundRuleStore) {
        if (groundModelInit) {
            return;
        }

        TermStore termStore = (TermStore)Config.getNewObject(TERM_STORE_KEY, TERM_STORE_DEFAULT);
        TermGenerator termGenerator = (TermGenerator)Config.getNewObject(TERM_GENERATOR_KEY, TERM_GENERATOR_DEFAULT);

        log.debug("Initializing objective terms for {} ground rules.", groundRuleStore.size());
        termStore.ensureVariableCapacity(atomManager.getCachedRVACount());
        @SuppressWarnings("unchecked")
        int termCount = termGenerator.generateTerms(groundRuleStore, termStore);
        log.debug("Generated {} objective terms from {} ground rules.", termCount, groundRuleStore.size());

        TrainingMap trainingMap = new TrainingMap(atomManager, observedDB, false);

        Reasoner reasoner = (Reasoner)Config.getNewObject(REASONER_KEY, REASONER_DEFAULT);

        initGroundModel(reasoner, groundRuleStore, termStore, termGenerator, atomManager, trainingMap);
    }

    public void resetModel(){
        this.termStore.close();
        this.allRules.clear();
        this.allRules.addAll(initialListOfRules);
        this.mutableRules.clear();
        //TODO: Can be made more efficient by not having to reground initial model.
        this.groundRuleStore.close();
        this.groundModelInit = false;
        this.initGroundModel();
    }

    protected boolean addRuleToModel(Rule r){
        int count = r.groundAll(atomManager, groundRuleStore);
        log.debug("New rule: " + r + " generated " + count + " groundings.");
        allRules.add(r);
        if (r instanceof WeightedRule) {
            mutableRules.add((WeightedRule) r);
        }
        termStore.ensureCapacity(atomManager.getCachedRVACount());
        List<GroundRule> grules = new ArrayList<>();
        for (GroundRule gr: groundRuleStore.getGroundRules(r)){
            grules.add(gr);
        }
        int i = termGenerator.generateTerms(r, grules, termStore);

        return false;
    }


    /**
     * Pass in all the ground model infrastructure.
     * The caller should be careful calling this method instead of the other variant.
     * Children should favor overriding postInitGroundModel() instead of this.
     */
    public void initGroundModel(
            Reasoner reasoner, GroundRuleStore groundRuleStore,
            TermStore termStore, TermGenerator termGenerator,
            PersistedAtomManager atomManager, TrainingMap trainingMap) {
        if (groundModelInit) {
            return;
        }

        this.reasoner = reasoner;
        this.groundRuleStore = groundRuleStore;
        this.termStore = termStore;
        this.termGenerator = termGenerator;
        this.atomManager = atomManager;
        this.trainingMap = trainingMap;

        postInitGroundModel();

        groundModelInit = true;

    }

    public WeightLearningApplication getNewWeightLearner(){
        if (!groundModelInit){
            return null;
        }
        //Not sure if this is needed.
//        if (this.weightLearner != null) {
//            this.weightLearner.close();
//        }
        this.weightLearner = new MaxLikelihoodMPE(allRules, mutableRules, rvDB, observedDB,
                reasoner, groundRuleStore, termStore, termGenerator, atomManager, trainingMap);
        return this.weightLearner;
    }

    /**
     * A convenient place for children to do additional ground model initialization.
     */
    protected void postInitGroundModel() {}

    @SuppressWarnings("unchecked")
    protected void computeMPEState() {
        if (inMPEState) {
            return;
        }

        termStore.clear();
        termStore.ensureVariableCapacity(atomManager.getCachedRVACount());
        termGenerator.generateTerms(groundRuleStore, termStore);

        reasoner.optimize(termStore);

        inMPEState = true;
    }

    public Model getBestModel(){
        Model bestModel = new Model();
        for (Rule rule : bestRulesSoFar) {
            bestModel.addRule(rule);
        }
        return bestModel;
    }

    @Override
    public void close() {
        if (groundRuleStore != null) {
            groundRuleStore.close();
            groundRuleStore = null;
        }

        if (termStore != null) {
            termStore.close();
            termStore = null;
        }

        if (reasoner != null) {
            reasoner.close();
            reasoner = null;
        }

        termGenerator = null;
        trainingMap = null;
        atomManager = null;
        rvDB = null;
        observedDB = null;
        if (this.weightLearner != null) {
            this.weightLearner.close();
        }
    }

    /**
     * Set RandomVariableAtoms with training labels to their observed values.
     */
    protected void setLabeledRandomVariables() {
        inMPEState = false;

        for (Map.Entry<RandomVariableAtom, ObservedAtom> entry : trainingMap.getTrainingMap().entrySet()) {
            entry.getKey().setValue(entry.getValue().getValue());
        }
    }

    /**
     * Set RandomVariableAtoms with training labels to their default values.
     */
    protected void setDefaultRandomVariables() {
        inMPEState = false;

        for (RandomVariableAtom atom : trainingMap.getTrainingMap().keySet()) {
            atom.setValue(0.0f);
        }

        for (RandomVariableAtom atom : trainingMap.getLatentVariables()) {
            atom.setValue(0.0f);
        }
    }

    /**
     * Create an atom manager on top of the RV database.
     * This allows an opportunity for subclasses to create a special manager.
     */
    protected PersistedAtomManager createAtomManager() {
        return new PersistedAtomManager(rvDB);
    }

    /**
     * Make sure that all targets from the observed database exist in the RV database.
     */
    private void ensureTargets(PersistedAtomManager atomManager) {
        // Iterate through all of the registered predicates in the observed.
        for (StandardPredicate predicate : observedDB.getDataStore().getRegisteredPredicates()) {
            // Ignore any closed predicates.
            if (observedDB.isClosed(predicate)) {
                continue;
            }

            // Commit the atoms into the RV databse with the default value.
            for (ObservedAtom observedAtom : observedDB.getAllGroundObservedAtoms(predicate)) {
                GroundAtom otherAtom = atomManager.getAtom(observedAtom.getPredicate(), observedAtom.getArguments());

                if (otherAtom instanceof ObservedAtom) {
                    continue;
                }

                RandomVariableAtom rvAtom = (RandomVariableAtom)otherAtom;
                rvAtom.setValue(0.0f);
            }
        }

        atomManager.commitPersistedAtoms();
    }

    /**
     * Construct a weight learning application given the data.
     * Look for a constructor like: (List<Rule>, Database (rv), Database (observed)).
     */
    public static AbstractStructureLearningApplication getSLA(String name, List<Rule> rules,
                                                              Database randomVariableDatabase,
                                                              Database observedTruthDatabase,
                                                              Set<StandardPredicate> closedPredicates,
                                                              Set<StandardPredicate> openPredicates) {
        String className = Reflection.resolveClassName(name);
        if (className == null) {
            throw new IllegalArgumentException("Could not find class: " + name);
        }

        Class<? extends AbstractStructureLearningApplication> classObject = null;
        try {
            @SuppressWarnings("unchecked")
            Class<? extends AbstractStructureLearningApplication> uncheckedClassObject = (Class<? extends AbstractStructureLearningApplication>)Class.forName(className);
            classObject = uncheckedClassObject;
        } catch (ClassNotFoundException ex) {
            throw new IllegalArgumentException("Could not find class: " + className, ex);
        }

        Constructor<? extends AbstractStructureLearningApplication> constructor = null;
        try {
            constructor = classObject.getConstructor(List.class, Database.class, Database.class, Set.class, Set.class);
        } catch (NoSuchMethodException ex) {
            throw new IllegalArgumentException("No sutible constructor found for structure learner: " + className + ".", ex);
        }

        AbstractStructureLearningApplication sla = null;
        try {
            sla = constructor.newInstance(rules, randomVariableDatabase, observedTruthDatabase,
                    closedPredicates, openPredicates);
        } catch (InstantiationException ex) {
            throw new RuntimeException("Unable to instantiate structure learner (" + className + ")", ex);
        } catch (IllegalAccessException ex) {
            throw new RuntimeException("Insufficient access to constructor for " + className, ex);
        } catch (InvocationTargetException ex) {
            throw new RuntimeException("Error thrown while constructing " + className, ex);
        }

        return sla;
    }

}

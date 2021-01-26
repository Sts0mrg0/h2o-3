package hex.tree.isoforextended;

import hex.ModelBuilder;
import hex.ModelCategory;
import jsr166y.CountedCompleter;
import org.apache.log4j.Logger;
import water.*;
import water.fvec.Frame;
import water.util.*;

import java.util.Random;

/**
 * Extended isolation forest implementation. Algorithm comes from https://arxiv.org/pdf/1811.02141.pdf paper.
 *
 * @author Adam Valenta
 */
public class ExtendedIsolationForest extends ModelBuilder<ExtendedIsolationForestModel,
        ExtendedIsolationForestModel.ExtendedIsolationForestParameters,
        ExtendedIsolationForestModel.ExtendedIsolationForestOutput> {

    private static final Logger LOG = Logger.getLogger(ExtendedIsolationForest.class);

    transient IsolationTree[] _iTrees;
    transient Random _rand;

    // Called from an http request
    public ExtendedIsolationForest(ExtendedIsolationForestModel.ExtendedIsolationForestParameters parms) {
        super(parms);
        init(false);
    }

    public ExtendedIsolationForest(ExtendedIsolationForestModel.ExtendedIsolationForestParameters parms, Key<ExtendedIsolationForestModel> key) {
        super(parms, key);
        init(false);
    }

    public ExtendedIsolationForest(ExtendedIsolationForestModel.ExtendedIsolationForestParameters parms, Job job) {
        super(parms, job);
        init(false);
    }

    public ExtendedIsolationForest(boolean startup_once) {
        super(new ExtendedIsolationForestModel.ExtendedIsolationForestParameters(), startup_once);
    }

    @Override
    public void init(boolean expensive) {
        super.init(expensive);
        if (_parms.train() != null) {
            long extensionLevelMax = _parms.train().numCols() - 1;
            if (_parms.extension_level < 0 || _parms.extension_level > extensionLevelMax) {
                throw new IllegalStateException("Parameter extension_level must be in interval [0, "
                        + extensionLevelMax + "] but it is " + _parms.extension_level);
            }
            long sampleSizeMax = _parms.train().numRows();
            if (_parms._sample_size < 0 || _parms._sample_size > sampleSizeMax) {
                throw new IllegalStateException("Parameter sample_size must be in interval [0, "
                        + sampleSizeMax + "] but it is " + _parms._sample_size);
            }
        }
    }

    @Override
    protected Driver trainModelImpl() {
        return new ExtendedIsolationForestDriver();
    }

    @Override
    public ModelCategory[] can_build() {
        return new ModelCategory[]{
                ModelCategory.AnomalyDetection
        };
    }

    @Override
    public boolean isSupervised() {
        return false;
    }

    @Override
    public boolean havePojo() {
        return false;
    }

    @Override
    public boolean haveMojo() {
        return false;
    }

    protected void addCustomInfo(ExtendedIsolationForestModel.ExtendedIsolationForestOutput out) {
        if (_iTrees != null) {
            out.iTrees = _iTrees;
        }
    }

    private class ExtendedIsolationForestDriver extends Driver {

        @Override
        public void computeImpl() {
            _rand = RandomUtils.getRNG(_parms._seed);
            _iTrees = new IsolationTree[_parms._ntrees];
            ExtendedIsolationForestModel model = new ExtendedIsolationForestModel(dest(), _parms,
                    new ExtendedIsolationForestModel.ExtendedIsolationForestOutput(ExtendedIsolationForest.this));
            model.delete_and_lock(_job);
            IsolationTreeForkJoinTask [] iTreeTasks = new IsolationTreeForkJoinTask[_parms._ntrees];
            for (int t = 0; t < _parms._ntrees; t++) {
                iTreeTasks[t] = new IsolationTreeForkJoinTask(t);
                H2O.submitTask(iTreeTasks[t]);
            }
            for (int t = 0; t < _parms._ntrees; t++) {
                _iTrees[t] = iTreeTasks[t].getResult();
            }
            model.unlock(_job);
            addCustomInfo(model._output);
        }
    }

    private class IsolationTreeForkJoinTask extends H2O.H2OCountedCompleter<IsolationTreeForkJoinTask> {

        private IsolationTree iTree;
        private int treeNum;
        
        public IsolationTreeForkJoinTask(int treeNum) {
            super();
            this.treeNum = treeNum;     
        }

        @Override
        public void compute2() {
            try {
                Scope.enter();
                int heightLimit = (int) Math.ceil(MathUtils.log2(_parms._sample_size));
                int randomUnit = _rand.nextInt();

                Frame subSample = new SubSampleTask(_parms._sample_size, _parms._seed + randomUnit)
                        .doAll(_train.types(), _train.vecs()).outputFrame(Key.make(), _train.names(), _train.domains());
                Scope.track(subSample);

                iTree = new IsolationTree(subSample._key, heightLimit, _parms._seed + randomUnit, _parms.extension_level, treeNum);
                iTree.buildTree();
                if (LOG.isDebugEnabled()) {
                    iTree.logNodesNumRows();
                    iTree.logNodesHeight();
                }
                tryComplete();
            } finally {
                Scope.exit();
            }
        }

        /**
         * Blocking call to obtain a result of computation.
         */
        public IsolationTree getResult() {
            join();
            return this.iTree;
        }

        @Override
        public void onCompletion(CountedCompleter caller) {
            _job.update(1);
            LOG.info("Tree " + treeNum + " is done.");
        }

        @Override
        public boolean onExceptionalCompletion(Throwable ex, CountedCompleter caller) {
            LOG.error(ex);
            return true;
        }
    }

}
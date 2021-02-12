package hex.tree.isoforextended;

import org.apache.log4j.Logger;
import water.Iced;
import water.util.ArrayUtils;
import water.util.MathUtils;

import java.util.Arrays;

/**
 * @author Adam Valenta
 */
public class CompressedIsolationTree extends Iced<CompressedIsolationTree> {
    private static final Logger LOG = Logger.getLogger(CompressedIsolationTree.class);

    private final AbstractCompressedNode[] _nodes;

    public CompressedIsolationTree(int heightLimit) {
        _nodes = new AbstractCompressedNode[(int) Math.pow(2, heightLimit) - 1];
    }
    
    public AbstractCompressedNode[] getNodes() {
        return  _nodes;
    }

    private int leftChildIndex(int i) {
        return 2 * i + 1;
    }

    private int rightChildIndex(int i) {
        return 2 * i + 2;
    }

//    /**
//     * Helper method. Print nodes' size of the tree.
//     */
//    public void logNodesNumRows() {
//        if (_nodes == null) {
//            LOG.debug("Not available for buildTreeRecursive()");
//        }
//        StringBuilder logMessage = new StringBuilder();
//        for (int i = 0; i < _nodes.length; i++) {
//            if (_nodes[i] == null)
//                logMessage.append(". ");
//            else
//                logMessage.append(_nodes[i]._numRows + " ");
//        }
//        LOG.debug(logMessage.toString());
//    }
    
//    /**
//     * Helper method. Print height (length of path from root) of each node in trees. Root is 0.
//     */
//    public void logNodesHeight() {
//        if (_nodes == null) {
//            LOG.debug("Not available for buildTreeRecursive()");
//        }
//        StringBuilder logMessage = new StringBuilder();
//        for (int i = 0; i < _nodes.length; i++) {
//            if (_nodes[i] == null)
//                logMessage.append(". ");
//            else
//                logMessage.append(_nodes[i]._height + " ");
//        }
//        LOG.debug(logMessage.toString());
//    }

    /**
     * Implementation of Algorithm 3 (pathLength) from paper.
     */
    public double computePathLength(double[] row) {
        int position = 0;
        AbstractCompressedNode node = _nodes[0];
        while (!(node instanceof CompressedLeaf)) {
            CompressedNode compressedNode = (CompressedNode) node;
            double mul = ArrayUtils.subAndMul(row, compressedNode._p, compressedNode._n);
            if (mul <= 0) {
                position = leftChildIndex(position);
            } else {
                position = rightChildIndex(position);
            }
            if (position < _nodes.length)
                node = _nodes[position];
            else
                break;
        }
        return node._height + averagePathLengthOfUnsuccessfulSearch(((CompressedLeaf) node)._numRows);
    }
    
    public static abstract class AbstractCompressedNode extends Iced<AbstractCompressedNode> {
        private final int _height;
    
        public AbstractCompressedNode(int height) {
            _height = height;
        }
    }
    
    
    public static class CompressedNode extends AbstractCompressedNode {

        /**
         * Random slope
         */
        private final double[] _n;

        /**
         * Random intercept point
         */
        private final double[] _p;

        public CompressedNode(IsolationTree.Node node) {
            this(node._n, node._p, node._height);
        }
        
        public CompressedNode(double[] n, double[] p, int currentHeight) {
            super(currentHeight);
            this._n = n == null ? null : Arrays.copyOf(n, n.length);
            this._p = p == null ? null : Arrays.copyOf(p, p.length);
        }
    }
    
    public static class CompressedLeaf extends AbstractCompressedNode {
        private final int _numRows;
    
        public CompressedLeaf(IsolationTree.Node node) {
            this(node._height, node._numRows);
        }
        
        public CompressedLeaf(int currentHeight, int numRows) {
            super(currentHeight);
            _numRows = numRows;
        }
    }

    /**
     * Gives the average path length of unsuccessful search in BST.
     * Comes from Algorithm 3 (pathLength) and Equation 2 in paper
     *
     * @param n number of elements
     */
    public static double averagePathLengthOfUnsuccessfulSearch(long n) {
        if (n <= 0)
            return 0;
        if (n == 2)
            return 1;
        return 2 * MathUtils.harmonicNumberEstimation(n - 1) - (2.0 * (n - 1.0)) / n;
    }
}

package ubc.cosc322;

import java.util.Map;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Random;
import ygraph.ai.smartfox.games.amazons.AmazonsGameMessage;

/* MonteCarloPlayer.java (completed with Monte Carlo Tree Search)
 *
 * A monte carlo player that makes moves using the monte carlo tree search algorithm.
 * It extends the BasePlayer class and overrides the processMove method to select moves
 * based on simulated playouts.
 *
 * The Monte Carlo Tree Search algorithm consists of four phases:
 *   1. Selection: Traverse the tree using the UCT formula to select a promising node.
 *   2. Expansion: Expand the tree by adding a new child node with an untried move.
 *   3. Simulation: Run a random playout from the expanded node to a terminal state.
 *   4. Backpropagation: Update win/visit counts along the path based on the simulation result.
 *
 * This implementation is integrated with the game engine and runs similarly to the RandomPlayer.
 */
public class MonteCarloPlayer extends BasePlayer {
    private static final int ITERATIONS = 1000; // Number of iterations for MCTS
    private Random random = new Random();

    public MonteCarloPlayer(String userName, String passwd) {
        super(userName, passwd);
    }
    
    @Override
    protected void processMove(Map<String, Object> msgDetails) {
        // Get a copy of the current board state
        LocalBoard rootBoard = localBoard.copy();
        // Our player identifier (the one making the move)
        int ourPlayer = localBoard.getLocalPlayer();
        
        // Create the root node for the MCTS tree
        TreeNode rootNode = new TreeNode(rootBoard, null, null);
        
        // Run MCTS iterations
        for (int i = 0; i < ITERATIONS; i++) {
            // Phase 1: Selection
            TreeNode node = selectNode(rootNode);
            
            // Phase 2: Expansion (if the node is not terminal)
            if (!isTerminal(node.board)) {
                node = expand(node);
            }
            
            // Phase 3: Simulation (Random playout from the node's state)
            int simulationResult = simulatePlayout(node.board, ourPlayer) ? 1 : 0;
            
            // Phase 4: Backpropagation (Update the nodes along the path)
            backpropagate(node, simulationResult);
        }
        
        // Select the move from the root with the highest visit count
        TreeNode bestChild = null;
        double bestScore = -1;
        for (TreeNode child : rootNode.children) {
            double score = child.visits;
            if (score > bestScore) {
                bestScore = score;
                bestChild = child;
            }
        }
        
        // If no move was found, output error
        if (bestChild == null || bestChild.action == null) {
            System.out.println("No valid move selected by MCTS!");
            return;
        }
        
        // Execute the chosen move
        MoveAction moveAction = bestChild.action;
        localBoard.updateState(moveAction);
        
        // Create move message map to send to the game server
        Map<String, Object> moveMsg = new HashMap<>();
        moveMsg.put(AmazonsGameMessage.QUEEN_POS_CURR, new ArrayList<>(moveAction.getQueenCurrent()));
        moveMsg.put(AmazonsGameMessage.QUEEN_POS_NEXT, new ArrayList<>(moveAction.getQueenTarget()));
        moveMsg.put(AmazonsGameMessage.ARROW_POS, new ArrayList<>(moveAction.getArrowTarget()));
        
        gamegui.updateGameState(moveMsg);
        gameClient.sendMoveMessage(moveMsg);
    }
    
    /**
     * Determines if the given board state is terminal.
     * A terminal state is reached when the current player has no valid moves.
     */
    private boolean isTerminal(LocalBoard board) {
        MoveActionFactory factory = new MoveActionFactory(board.getState(), board.getLocalPlayer());
        return factory.getActions().isEmpty();
    }
    
    /**
     * Selection phase: Traverse the tree from the given node using the UCT formula
     * until reaching a node that is not fully expanded or is terminal.
     */
    private TreeNode selectNode(TreeNode node) {
        while (node.untriedMoves.isEmpty() && !node.children.isEmpty() && !isTerminal(node.board)) {
            node = bestUCTChild(node);
        }
        return node;
    }
    
    /**
     * Returns the child node with the highest UCT (Upper Confidence Bound) value.
     * UCT = (win/visits) + sqrt(2 * ln(parent.visits) / visits)
     */
    private TreeNode bestUCTChild(TreeNode node) {
        TreeNode bestChild = null;
        double bestUCT = Double.NEGATIVE_INFINITY;
        for (TreeNode child : node.children) {
            double winRate = (child.visits == 0) ? 0 : (double) child.wins / child.visits;
            double uctValue = winRate + Math.sqrt(2 * Math.log(node.visits + 1) / (child.visits + 1e-6));
            if (uctValue > bestUCT) {
                bestUCT = uctValue;
                bestChild = child;
            }
        }
        return bestChild;
    }
    
    /**
     * Expansion phase: Expand the node by selecting one untried move, removing it from the list,
     * and adding a new child node with the move applied to a copied board state.
     */
    private TreeNode expand(TreeNode node) {
        if (node.untriedMoves.isEmpty()) {
            return node; // Cannot expand further
        }
        // Randomly select one untried move
        int index = random.nextInt(node.untriedMoves.size());
        Map<String, Object> moveMap = node.untriedMoves.remove(index);
        
        // Convert move map to MoveAction
        List<Integer> queenCurrent = (List<Integer>) moveMap.get(AmazonsGameMessage.QUEEN_POS_CURR);
        List<Integer> queenTarget = (List<Integer>) moveMap.get(AmazonsGameMessage.QUEEN_POS_NEXT);
        List<Integer> arrowTarget = (List<Integer>) moveMap.get(AmazonsGameMessage.ARROW_POS);
        MoveAction moveAction = new MoveAction(queenCurrent, queenTarget, arrowTarget);
        
        // Create new board state by applying the move
        LocalBoard newBoard = node.board.copy();
        newBoard.updateState(moveAction);
        // Switch turn to the opponent after the move
        newBoard.setLocalPlayer(newBoard.getOpponent());
        
        // Create and add the new child node
        TreeNode childNode = new TreeNode(newBoard, node, moveAction);
        node.children.add(childNode);
        return childNode;
    }
    
    /**
     * Simulation phase: Run a random playout from the given board state until a terminal state is reached.
     * Returns true if the playout results in a win for the specified player, false otherwise.
     */
    private boolean simulatePlayout(LocalBoard board, int ourPlayer) {
        LocalBoard simulationBoard = board.copy();
        while (true) {
            MoveActionFactory factory = new MoveActionFactory(simulationBoard.getState(), simulationBoard.getLocalPlayer());
            List<Map<String, Object>> moves = factory.getActions();
            if (moves.isEmpty()) {
                // Terminal state reached; the current player cannot move, so the winner is the opponent.
                int winner = simulationBoard.getOpponent();
                return winner == ourPlayer;
            }
            // Choose a random move for simulation
            Map<String, Object> moveMap = moves.get(random.nextInt(moves.size()));
            List<Integer> queenCurrent = (List<Integer>) moveMap.get(AmazonsGameMessage.QUEEN_POS_CURR);
            List<Integer> queenTarget = (List<Integer>) moveMap.get(AmazonsGameMessage.QUEEN_POS_NEXT);
            List<Integer> arrowTarget = (List<Integer>) moveMap.get(AmazonsGameMessage.ARROW_POS);
            MoveAction moveAction = new MoveAction(queenCurrent, queenTarget, arrowTarget);
            simulationBoard.updateState(moveAction);
            // Switch turn for simulation
            simulationBoard.setLocalPlayer(simulationBoard.getOpponent());
        }
    }
    
    /**
     * Backpropagation phase: Update the win and visit counts along the path from the given node to the root.
     * The result is inverted at each level to account for alternating turns.
     */
    private void backpropagate(TreeNode node, int result) {
        while (node != null) {
            node.visits++;
            node.wins += result;
            // Invert the result for the parent's perspective
            result = 1 - result;
            node = node.parent;
        }
    }
    
    /**
     * Inner class representing a node in the Monte Carlo Tree.
     * Each node stores a board state, the move that led to that state, its parent, children, and simulation statistics.
     */
    private class TreeNode {
        LocalBoard board;
        TreeNode parent;
        List<TreeNode> children = new ArrayList<>();
        MoveAction action;
        int wins = 0;
        int visits = 0;
        // List of untried moves from this board state
        List<Map<String, Object>> untriedMoves;
    
        public TreeNode(LocalBoard board, TreeNode parent, MoveAction action) {
            this.board = board.copy();
            this.parent = parent;
            this.action = action;
            // Initialize untried moves using the MoveActionFactory
            MoveActionFactory factory = new MoveActionFactory(board.getState(), board.getLocalPlayer());
            this.untriedMoves = factory.getActions();
        }
    
        public void addChild(LocalBoard newBoard, MoveAction action) {
            children.add(new TreeNode(newBoard, this, action));
        }
    
        public int getVisits() { 
            return visits; 
        }
        
        public int getWins() {
            return wins;
        }
    }
}

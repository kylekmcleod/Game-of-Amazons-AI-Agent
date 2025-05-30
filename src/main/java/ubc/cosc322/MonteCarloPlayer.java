package ubc.cosc322;

import java.awt.Point;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Queue;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import ygraph.ai.smartfox.games.amazons.AmazonsGameMessage;

/**
 * MonteCarloPlayer.java
 * 
 * A Monte Carlo Tree Search (MCTS) based player for the Game of Amazons.
 * This player uses a combination of heuristics:
 * - Queen mobility: favoring moves that leave the queen with many options.
 * - Opponent blocking: preferring moves that reduce opponent mobility.
 * - Territory control: evaluating the long-term board control (via a flood-fill).
 *
 * The code uses a selective search over a subset of possible moves.
 */
public class MonteCarloPlayer extends BasePlayer {

    // MCTS parameters.
    private static final long MAX_TIME = 10 * 2800;
    private static final long MAX_MEMORY = 7L * 1024 * 1024 * 1024;
    private static final int SIMULATION_DEPTH = 25;

    private static int MOVE_CHOICES = 15;
    private static int INCREASE_MOVE_CHOICES = 5;
    private static int MAX_DEPTH = 1;
    private static int INCREASE_MAX_DEPTH_AFTER = 10;

    // Heuristic weights.
    private static final double MOBILITY_WEIGHT = 0.5;
    private static final double BLOCKING_WEIGHT = 1.0;

    private Random random = new Random();
    private static int moveCounter = 0;

    private static final int NUM_THREADS = Runtime.getRuntime().availableProcessors();
    private final AtomicLong iterationCount = new AtomicLong(0);

    public MonteCarloPlayer(String userName, String passwd) {
        super(userName, passwd);
    }
    
    @Override
    protected void processMove(Map<String, Object> msgDetails) {
        moveCounter++;
        LocalBoard rootBoard = localBoard.copy();
        int ourPlayer = localBoard.getLocalPlayer();
        System.out.println("Our Player: " + ourPlayer);
    
        TreeNode rootNode = new TreeNode(rootBoard, null, null);
        System.out.println("Starting MCTS with " + MAX_TIME/1000 + " seconds and " + NUM_THREADS + " threads.");
    
        // Create thread pool. This allows multiple threads to run.
        // Currently increases our iterations by around 45%
        ExecutorService executor = Executors.newWorkStealingPool(NUM_THREADS);
        long startTime = System.currentTimeMillis();
        iterationCount.set(0);
    
        for (int i = 0; i < NUM_THREADS; i++) {
            executor.submit(() -> {
                long endTime = startTime + MAX_TIME;
                while (System.currentTimeMillis() < endTime) {
                    
                    // Step 1: Selection
                    TreeNode selectedNode;
                    synchronized (rootNode) {
                        selectedNode = treePolicy(rootNode);
                    }
                    
                    // Step 2: Simulation
                    LocalBoard simulationBoard = selectedNode.board.copy();
                    boolean simulationResult = simulatePlayout(simulationBoard, ourPlayer);
                    int result = simulationResult ? 1 : 0;
                    
                    // Step 3: Backpropagation
                    backpropagate(selectedNode, result);
                    iterationCount.incrementAndGet();
                }
            });
        }
    
        executor.shutdown();
        try {
            executor.awaitTermination(MAX_TIME + 1000, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            System.err.println("Thread execution interrupted: " + e.getMessage());
        }
    
        System.out.println("MCTS iterations: " + iterationCount.get());
        printBestMoves(rootNode);
    
        TreeNode bestChild = null;
        double bestScore = -1;
        for (TreeNode child : rootNode.children) {
            double winRatio = (child.visits > 0) ? ((double) child.wins / (double) child.visits) : 0.5;
            if (winRatio > bestScore) {
                bestScore = winRatio;
                bestChild = child;
            }
        }
    
        if (bestChild == null || bestChild.action == null) {
            System.out.println("No valid move selected by MCTS!");
            return;
        }
    
        MoveAction moveAction = bestChild.action;
        localBoard.updateState(moveAction);
    
        Map<String, Object> moveMsg = new HashMap<>();
        moveMsg.put(AmazonsGameMessage.QUEEN_POS_CURR, new ArrayList<>(moveAction.getQueenCurrent()));
        moveMsg.put(AmazonsGameMessage.QUEEN_POS_NEXT, new ArrayList<>(moveAction.getQueenTarget()));
        moveMsg.put(AmazonsGameMessage.ARROW_POS, new ArrayList<>(moveAction.getArrowTarget()));
    
        gamegui.updateGameState(moveMsg);
        gameClient.sendMoveMessage(moveMsg);
        MOVE_CHOICES += INCREASE_MOVE_CHOICES;
        
        if (INCREASE_MAX_DEPTH_AFTER != 0 && moveCounter % INCREASE_MAX_DEPTH_AFTER == 0) {
            MAX_DEPTH++;
        }        
    }
    
    private boolean isTerminal(LocalBoard board) {
        int currentPlayer = board.getLocalPlayer();
        MoveActionFactory factory = new MoveActionFactory(board.getState(), currentPlayer);
        return factory.getActions().isEmpty();
    }
    
    private TreeNode bestUCTChild(TreeNode node) {
        TreeNode bestChild = null;
        double bestUCT = Double.NEGATIVE_INFINITY;
        double C = 1;
        boolean isOurPlayerTurn = (node.board.getLocalPlayer() == localBoard.getLocalPlayer());
        for (TreeNode child : node.children) {
            double exploitation = (child.visits > 0) ? (double) child.wins / child.visits : 0;
            if (!isOurPlayerTurn) {
                exploitation = 1 - exploitation;
            }
            double exploration = C * Math.sqrt(Math.log(node.visits) / (child.visits + 1e-10));
            double uctValue = exploitation + exploration;
            if (uctValue > bestUCT) {
                bestUCT = uctValue;
                bestChild = child;
            }
        }
        if (bestChild == null && !node.children.isEmpty()) {
            bestChild = node.children.get(0);
        }
        return bestChild;
    }
    
    private TreeNode treePolicy(TreeNode node) {
        int startingDepth = getNodeDepth(node);
        int currentDepth = 0;
        
        while (!isTerminal(node.board) && currentDepth < MAX_DEPTH) {
            if (!node.untriedMoves.isEmpty()) {
                return expand(node);
            } else if (!node.children.isEmpty()) {
                node = bestUCTChild(node);
                currentDepth = getNodeDepth(node) - startingDepth;
            } else {
                break;
            }
        }
        return node;
    }
    
    private int getNodeDepth(TreeNode node) {
        int depth = 0;
        TreeNode current = node;
        while (current.parent != null) {
            depth++;
            current = current.parent;
        }
        return depth;
    }
    
    private TreeNode expand(TreeNode node) {
        if (node.untriedMoves.isEmpty()) {
            return node;
        }
        
        PriorityQueue<Map<String, Object>> topMoves = new PriorityQueue<>(
            Comparator.comparingDouble(move -> calculateCombinedHeuristic(move, node.board))
        );
        
        for (Map<String, Object> move : node.untriedMoves) {
            double score = calculateCombinedHeuristic(move, node.board);
            
            if (topMoves.size() < MOVE_CHOICES) {
                topMoves.add(move);
            } else if (score > calculateCombinedHeuristic(topMoves.peek(), node.board)) {
                topMoves.poll();
                topMoves.add(move);
            }
        }
        
        List<Map<String, Object>> bestMoves = new ArrayList<>(topMoves);
        
        Map<String, Object> moveMap = bestMoves.get(bestMoves.size() - 1);
        node.untriedMoves = bestMoves;
        node.untriedMoves.remove(moveMap);
        
        List<Integer> queenCurrent = (List<Integer>) moveMap.get(AmazonsGameMessage.QUEEN_POS_CURR);
        List<Integer> queenTarget = (List<Integer>) moveMap.get(AmazonsGameMessage.QUEEN_POS_NEXT);
        List<Integer> arrowTarget = (List<Integer>) moveMap.get(AmazonsGameMessage.ARROW_POS);
        MoveAction moveAction = new MoveAction(queenCurrent, queenTarget, arrowTarget);
        
        LocalBoard newBoard = node.board.copy();
        newBoard.updateState(moveAction);
        
        TreeNode childNode = new TreeNode(newBoard, node, moveAction);
        node.children.add(childNode);
        return childNode;
    }
    
    /**
     * Queen mobility heuristic.
     * Evaluates how many moves the queen will have after moving to the target.
     */
    private double queenMobilityHeuristic(Map<String, Object> moveMap, LocalBoard board) {
        List<Integer> queenTarget = (List<Integer>) moveMap.get(AmazonsGameMessage.QUEEN_POS_NEXT);
        if (queenTarget == null || queenTarget.size() < 2) {
            return 0;
        }
        int x = queenTarget.get(0);
        int y = queenTarget.get(1);
        int currentPlayer = board.getLocalPlayer();
        MoveActionFactory factory = new MoveActionFactory(board.getState(), currentPlayer);
        List<List<Integer>> validMoves = factory.getValidMoves(x, y);
        double score = validMoves.size() * 3;
        return score;
    }
    
    /**
     * Opponent blocking heuristic.
     * Compares the opponent's mobility before and after the move.
     */
    private double opponentBlockingHeuristic(Map<String, Object> moveMap, LocalBoard board) {
        int opponentPlayer = board.getOpponent();
        MoveActionFactory factory = new MoveActionFactory(board.getState(), opponentPlayer);
        List<List<Integer>> opponentQueens = factory.getAllQueenCurrents();
        int mobilityBefore = 0;
        for (List<Integer> queen : opponentQueens) {
            int x = queen.get(0);
            int y = queen.get(1);
            List<List<Integer>> validMoves = factory.getValidMoves(x, y);
            mobilityBefore += validMoves.size();
        }
    
        List<Integer> queenCurrent = (List<Integer>) moveMap.get(AmazonsGameMessage.QUEEN_POS_CURR);
        List<Integer> queenTarget = (List<Integer>) moveMap.get(AmazonsGameMessage.QUEEN_POS_NEXT);
        List<Integer> arrowTarget = (List<Integer>) moveMap.get(AmazonsGameMessage.ARROW_POS);
    
        LocalBoard simulationBoard = board.copy();
        MoveAction moveAction = new MoveAction(queenCurrent, queenTarget, arrowTarget);
        simulationBoard.updateState(moveAction);
    
        factory = new MoveActionFactory(simulationBoard.getState(), opponentPlayer);
        int mobilityAfter = 0;
        int completelyBlockedQueens = 0;
        for (List<Integer> queen : opponentQueens) {
            int x = queen.get(0);
            int y = queen.get(1);
            List<List<Integer>> validMoves = factory.getValidMoves(x, y);
            mobilityAfter += validMoves.size();
            if (validMoves.isEmpty()) {
                completelyBlockedQueens++;
            }
        }
        int blockingEffect = mobilityBefore - mobilityAfter;
        return (blockingEffect * 2) + (completelyBlockedQueens * 15);
    }
    
    /**
     * Combined heuristic: sums up mobility, opponent blocking.
     */
    private double calculateCombinedHeuristic(Map<String, Object> moveMap, LocalBoard board) {
        double mobilityScore = queenMobilityHeuristic(moveMap, board);
        double blockingScore = opponentBlockingHeuristic(moveMap, board);
        return (blockingScore * BLOCKING_WEIGHT) +
               (mobilityScore * MOBILITY_WEIGHT);
    }
    
    private boolean simulatePlayout(LocalBoard board, int ourPlayer) {
        LocalBoard simulationBoard = board.copy();
        int currentPlayer = simulationBoard.getLocalPlayer();
        
        for (int depth = 0; depth < SIMULATION_DEPTH; depth++) {
            MoveActionFactory factory = new MoveActionFactory(simulationBoard.getState(), currentPlayer);
            List<Map<String, Object>> moves = factory.getActions();
            
            if (moves.isEmpty()) {
                int winner = (currentPlayer == 1) ? 2 : 1;
                return winner == ourPlayer;
            }
            
            Map<String, Object> moveMap = moves.get(random.nextInt(moves.size()));
            List<Integer> queenCurrent = (List<Integer>) moveMap.get(AmazonsGameMessage.QUEEN_POS_CURR);
            List<Integer> queenTarget = (List<Integer>) moveMap.get(AmazonsGameMessage.QUEEN_POS_NEXT);
            List<Integer> arrowTarget = (List<Integer>) moveMap.get(AmazonsGameMessage.ARROW_POS);
            MoveAction moveAction = new MoveAction(queenCurrent, queenTarget, arrowTarget);
            simulationBoard.updateState(moveAction);
            currentPlayer = (currentPlayer == 1) ? 2 : 1;
            simulationBoard.setLocalPlayer(currentPlayer);
        }

        int ourMobility = 0;
        int opponentMobility = 0;
        
        // Calculate our mobility
        int ourPlayerID = ourPlayer;
        MoveActionFactory factory = new MoveActionFactory(simulationBoard.getState(), ourPlayerID);
        List<List<Integer>> ourQueens = factory.getAllQueenCurrents();
        for (List<Integer> queen : ourQueens) {
            ourMobility += factory.getValidMoves(queen.get(0), queen.get(1)).size();
        }
        
        // Calculate opponent mobility
        int opponentID = (ourPlayerID == 1) ? 2 : 1;
        factory = new MoveActionFactory(simulationBoard.getState(), opponentID);
        List<List<Integer>> opponentQueens = factory.getAllQueenCurrents();
        for (List<Integer> queen : opponentQueens) {
            opponentMobility += factory.getValidMoves(queen.get(0), queen.get(1)).size();
        }
        
        return ourMobility > opponentMobility;
    }
    
    private void backpropagate(TreeNode node, int result) {
        TreeNode current = node;
        int ourPlayer = localBoard.getLocalPlayer();
        while (current != null) {
            current.visits++;
            boolean isOurTurn = (current.board.getLocalPlayer() == ourPlayer);
            if (isOurTurn) {
                current.wins += result;
            } else {
                current.wins += (1 - result);
            }
            current = current.parent;
        }
    }
    
    private void printBestMoves(TreeNode rootNode) {
        if (!rootNode.children.isEmpty()) {
            System.out.println("\nBOT TOP MOVES:");
            rootNode.children.sort((a, b) -> Integer.compare(b.visits, a.visits));
            int showTopN = Math.min(5, rootNode.children.size());
            for (int i = 0; i < showTopN; i++) {
                TreeNode child = rootNode.children.get(i);
                MoveAction move = child.action;
                int queenXCurrent = move.getQueenCurrent().get(0);
                int queenYCurrent = move.getQueenCurrent().get(1);
                int queenXTarget = move.getQueenTarget().get(0);
                int queenYTarget = move.getQueenTarget().get(1);
                int arrowXTarget = move.getArrowTarget().get(0);
                int arrowYTarget = move.getArrowTarget().get(1);
                double winRate = (child.visits > 0) ? 100.0 * child.wins / child.visits : 0.0;
                String formattedWinRate = String.format("%.2f%%", winRate);
                Map<String, Object> moveMap = new HashMap<>();
                moveMap.put(AmazonsGameMessage.QUEEN_POS_CURR, move.getQueenCurrent());
                moveMap.put(AmazonsGameMessage.QUEEN_POS_NEXT, move.getQueenTarget());
                moveMap.put(AmazonsGameMessage.ARROW_POS, move.getArrowTarget());
                double mobilityHeuristicValue = Math.round(queenMobilityHeuristic(moveMap, child.board) * MOBILITY_WEIGHT * 100.0) / 100.0;
                double blockingHeuristicValue = -Math.round(opponentBlockingHeuristic(moveMap, child.board) * BLOCKING_WEIGHT * 100.0) / 100.0;
                double totalHeuristicValue = mobilityHeuristicValue + blockingHeuristicValue;
    
                System.out.print((i + 1) + ". Move:");
                System.out.print("  Q:(" + queenXCurrent + "," + queenYCurrent + ")");
                System.out.print("  to (" + queenXTarget + "," + queenYTarget + ")");
                System.out.print("  A:(" + arrowXTarget + "," + arrowYTarget + ")");
                System.out.print("  Visits: " + child.visits);
                System.out.print("  Win rate: " + formattedWinRate);
                System.out.print("  M: " + mobilityHeuristicValue);
                System.out.print("  B: " + blockingHeuristicValue);
                System.out.print("  Total Heuristic: " + totalHeuristicValue);
                System.out.println();
            }
            System.out.println("Total Moves Considered: " + rootNode.children.size());
            System.out.println("Move number: " + moveCounter);
            System.out.println("Max Depth: " + MAX_DEPTH);
        }    
    }
    
    private class TreeNode {
        LocalBoard board;
        TreeNode parent;
        List<TreeNode> children = new ArrayList<>();
        MoveAction action;
        int wins = 0;
        int visits = 0;
        List<Map<String, Object>> untriedMoves;
    
        public TreeNode(LocalBoard board, TreeNode parent, MoveAction action) {
            this.board = board.copy();
            this.parent = parent;
            this.action = action;
            MoveActionFactory factory = new MoveActionFactory(board.getState(), board.getLocalPlayer());
            this.untriedMoves = factory.getActions();
        }
    }
}

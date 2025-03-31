package ubc.cosc322;

import java.awt.Point;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
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
    private static int MOVE_CHOICES = 15;
    private static int INCREASE_MOVE_CHOICES = 3;
    private static int MAX_DEPTH = 1;
    private static int INCREASE_MAX_DEPTH_AFTER = 20;

    // Heuristic weights.
    private static final double MOBILITY_WEIGHT = 0.3;
    private static final double BLOCKING_WEIGHT = 1.1;
    private static final double TERRITORY_WEIGHT = 0.6;

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
        ExecutorService executor = Executors.newFixedThreadPool(NUM_THREADS);
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
            double winRatio = child.visits > 0 ? (double) child.wins / child.visits : 0;
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
    
        if (moveCounter % INCREASE_MAX_DEPTH_AFTER == 0) {
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
        double C = 0.9;
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
        int depth = 0;
        while (!isTerminal(node.board) && depth < MAX_DEPTH) {
            if (!node.untriedMoves.isEmpty()) {
                return expand(node);
            } else if (!node.children.isEmpty()) {
                node = bestUCTChild(node);
            } else {
                break;
            }
            depth++;
        }
        return node;
    }
    
    private TreeNode expand(TreeNode node) {
        if (node.untriedMoves.isEmpty()) {
            return node;
        }
    
        // Sort untried moves using the combined heuristic.
        node.untriedMoves.sort((move1, move2) -> {
            double score1 = calculateCombinedHeuristic(move1, node.board);
            double score2 = calculateCombinedHeuristic(move2, node.board);
            return Double.compare(score2, score1);
        });
    
        if (node.untriedMoves.size() > MOVE_CHOICES) {
            node.untriedMoves = node.untriedMoves.subList(0, MOVE_CHOICES);
        }
    
        Map<String, Object> moveMap = node.untriedMoves.remove(0);
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
     * Flood-fill territory method.
     * Computes the number of empty squares (territory) reachable by all queens of the given player.
     * The board is assumed to be 10x10 (1-indexed).
     */
    private int floodFillTerritory(LocalBoard board, int player) {
        int boardSize = 10;
        boolean[][] visited = new boolean[boardSize + 1][boardSize + 1];
        Queue<Point> queue = new LinkedList<>();
    
        // Add all queen positions for the given player.
        for (int row = 1; row <= boardSize; row++) {
            for (int col = 1; col <= boardSize; col++) {
                // We use a temporary list to represent the (row, col) position.
                if (board.getPositionValue(Arrays.asList(row, col)) == player) {
                    Point p = new Point(row, col);
                    if (!visited[row][col]) {
                        visited[row][col] = true;
                        queue.add(p);
                    }
                }
            }
        }
    
        int territoryCount = 0;
        int[] dx = {-1, -1, -1, 0, 0, 1, 1, 1};
        int[] dy = {-1,  0,  1, -1, 1, -1, 0, 1};
    
        while (!queue.isEmpty()) {
            Point current = queue.poll();
            for (int i = 0; i < 8; i++) {
                int nx = current.x + dx[i];
                int ny = current.y + dy[i];
                // Slide like a queen until a boundary or non-empty cell is reached.
                while (nx >= 1 && nx <= boardSize && ny >= 1 && ny <= boardSize &&
                        board.getPositionValue(Arrays.asList(nx, ny)) == LocalBoard.EMPTY) {
                    if (!visited[nx][ny]) {
                        visited[nx][ny] = true;
                        territoryCount++;
                        queue.add(new Point(nx, ny));
                    }
                    nx += dx[i];
                    ny += dy[i];
                }
            }
        }
        return territoryCount;
    }
    
    /**
     * Territory control heuristic.
     * Simulates the move and computes the difference between our reachable territory
     * and the opponent's reachable territory.
     */
    private double territoryControlHeuristic(Map<String, Object> moveMap, LocalBoard board) {
        LocalBoard simulatedBoard = board.copy();
        List<Integer> queenCurrent = (List<Integer>) moveMap.get(AmazonsGameMessage.QUEEN_POS_CURR);
        List<Integer> queenTarget = (List<Integer>) moveMap.get(AmazonsGameMessage.QUEEN_POS_NEXT);
        List<Integer> arrowTarget = (List<Integer>) moveMap.get(AmazonsGameMessage.ARROW_POS);
        MoveAction moveAction = new MoveAction(queenCurrent, queenTarget, arrowTarget);
        simulatedBoard.updateState(moveAction);
    
        int ourPlayer = board.getLocalPlayer();
        int opponent = board.getOpponent();
    
        int ourTerritory = floodFillTerritory(simulatedBoard, ourPlayer);
        int opponentTerritory = floodFillTerritory(simulatedBoard, opponent);
    
        return ourTerritory - opponentTerritory;
    }
    
    /**
     * Combined heuristic: sums up mobility, opponent blocking, and territory control.
     */
    private double calculateCombinedHeuristic(Map<String, Object> moveMap, LocalBoard board) {
        double mobilityScore = queenMobilityHeuristic(moveMap, board);
        double blockingScore = opponentBlockingHeuristic(moveMap, board);
        double territoryScore = territoryControlHeuristic(moveMap, board);
        return (blockingScore * BLOCKING_WEIGHT) +
               (mobilityScore * MOBILITY_WEIGHT) +
               (territoryScore * TERRITORY_WEIGHT);
    }
    
    private boolean simulatePlayout(LocalBoard board, int ourPlayer) {
        LocalBoard simulationBoard = board.copy();
        int currentPlayer = simulationBoard.getLocalPlayer();
        while (true) {
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
                double territoryHeuristicValue = Math.round(territoryControlHeuristic(moveMap, child.board) * TERRITORY_WEIGHT * 100.0) / 100.0;
                double totalHeuristicValue = mobilityHeuristicValue + blockingHeuristicValue + territoryHeuristicValue;
    
                System.out.print((i + 1) + ". Move:");
                System.out.print("  Q:(" + queenXCurrent + "," + queenYCurrent + ")");
                System.out.print("  to (" + queenXTarget + "," + queenYTarget + ")");
                System.out.print("  A:(" + arrowXTarget + "," + arrowYTarget + ")");
                System.out.print("  Visits: " + child.visits);
                System.out.print("  Win rate: " + formattedWinRate);
                System.out.print("  M: " + mobilityHeuristicValue);
                System.out.print("  B: " + blockingHeuristicValue);
                System.out.print("  T: " + territoryHeuristicValue);
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

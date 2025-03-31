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
 * For the first four moves, an additional heuristic is applied that favors moves
 * that bring queens closer to the center of the board while keeping them sufficiently spread.
 */
public class MonteCarloPlayer extends BasePlayer {

    // MCTS parameters.
    private static final int MAX_DEPTH = 1;
    private static final long MAX_TIME = 10 * 100; // in milliseconds
    private static final long MAX_MEMORY = 4L * 1024 * 1024 * 1024;
    private static int MOVE_CHOICES = 20;
    private static int INCREASE_MOVE_CHOICES = 3;

    // Heuristic weights.
    private static final double MOBILITY_WEIGHT = 0.3;
    private static final double BLOCKING_WEIGHT = 1.0;
    private static final double TERRITORY_WEIGHT = 0.7;

    private Random random = new Random();
    private static int moveCounter = 0;

    public MonteCarloPlayer(String userName, String passwd) {
        super(userName, passwd);
    }
    
    @Override
    protected void processMove(Map<String, Object> msgDetails) {
        LocalBoard rootBoard = localBoard.copy();
        int ourPlayer = localBoard.getLocalPlayer();
        System.out.println("Our Player: " + ourPlayer);
    
        TreeNode rootNode = new TreeNode(rootBoard, null, null);
        System.out.println("Starting MCTS with " + MAX_TIME/1000 + " seconds and " + MAX_MEMORY/1024/1024 + " MB memory limit.");
    
        long startTime = System.nanoTime();
        long initialMemory = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();
        long iterationCount = 0;
    
        while (true) {
            long currentMemory = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();
            if (currentMemory - initialMemory > MAX_MEMORY) {
                System.out.println("Memory limit exceeded. Stopping MCTS.");
                break;
            }
            long elapsedTime = (System.nanoTime() - startTime) / 1000000;
            if (elapsedTime > MAX_TIME) {
                System.out.println("Time limit exceeded. Stopping MCTS.");
                break;
            }
    
            // Step 1: Selection.
            TreeNode selectedNode = treePolicy(rootNode);
            // Step 2: Simulation.
            LocalBoard simulationBoard = selectedNode.board.copy();
            boolean simulationResult = simulatePlayout(simulationBoard, ourPlayer);
            int result = simulationResult ? 1 : 0;
            // Step 3: Backpropagation.
            backpropagate(selectedNode, result);
    
            iterationCount++;
        }
        System.out.println("MCTS iterations: " + iterationCount);
        printBestMoves(rootNode);
    
        // Choose the best child.
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
        moveCounter++;
        MOVE_CHOICES += INCREASE_MOVE_CHOICES;
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
     * For the first 4 moves, an additional centralization heuristic is added.
     */
    private double calculateCombinedHeuristic(Map<String, Object> moveMap, LocalBoard board) {
        double mobilityScore = queenMobilityHeuristic(moveMap, board);
        double blockingScore = opponentBlockingHeuristic(moveMap, board);
        double territoryScore = territoryControlHeuristic(moveMap, board);
        double combined = (blockingScore * BLOCKING_WEIGHT) +
                          (mobilityScore * MOBILITY_WEIGHT) +
                          (territoryScore * TERRITORY_WEIGHT);
        if (moveCounter < 4) {
            combined += centralizationHeuristic(moveMap, board);
        }
        return combined;
    }
    
    /**
     * Centralization and Spread Heuristic.
     * For early moves, rewards moves that bring a queen closer to the center
     * while ensuring queens remain sufficiently spread out.
     */
    private double centralizationHeuristic(Map<String, Object> moveMap, LocalBoard board) {
        List<Integer> queenTarget = (List<Integer>) moveMap.get(AmazonsGameMessage.QUEEN_POS_NEXT);
        if (queenTarget == null || queenTarget.size() < 2) {
            return 0;
        }
        // Calculate distance from board center (using 5.5,5.5 for a 10x10 board)
        double targetX = queenTarget.get(0);
        double targetY = queenTarget.get(1);
        double centerX = 5.5;
        double centerY = 5.5;
        double dx = targetX - centerX;
        double dy = targetY - centerY;
        double distanceFromCenter = Math.sqrt(dx * dx + dy * dy);
        // Approximate maximum distance from center (from a corner)
        double maxDistance = Math.sqrt(Math.pow(5.5 - 1, 2) + Math.pow(5.5 - 1, 2));
        // The bonus is higher when the queen is closer to the center.
        double centralBonus = 2 * (maxDistance - distanceFromCenter);

        // Spread: simulate the move and check distances between the moved queen and other friendly queens.
        LocalBoard simulatedBoard = board.copy();
        List<Integer> queenCurrent = (List<Integer>) moveMap.get(AmazonsGameMessage.QUEEN_POS_CURR);
        List<Integer> arrowTarget = (List<Integer>) moveMap.get(AmazonsGameMessage.ARROW_POS);
        MoveAction moveAction = new MoveAction(queenCurrent, queenTarget, arrowTarget);
        simulatedBoard.updateState(moveAction);
        int ourPlayer = board.getLocalPlayer();
        double minDistance = Double.MAX_VALUE;
        for (int row = 1; row <= 10; row++) {
            for (int col = 1; col <= 10; col++) {
                if (simulatedBoard.getPositionValue(Arrays.asList(row, col)) == ourPlayer) {
                    // Skip the queen that just moved.
                    if (row == targetX && col == targetY) continue;
                    double d = Math.sqrt(Math.pow(targetX - row, 2) + Math.pow(targetY - col, 2));
                    if (d < minDistance) {
                        minDistance = d;
                    }
                }
            }
        }
        // Penalize moves that bring queens too close (desired minimum separation is set to 3).
        double spreadPenalty = 0;
        double desiredMinDistance = 3.0;
        if (minDistance < desiredMinDistance) {
            spreadPenalty = 2 * (desiredMinDistance - minDistance);
        }
        return centralBonus - spreadPenalty;
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

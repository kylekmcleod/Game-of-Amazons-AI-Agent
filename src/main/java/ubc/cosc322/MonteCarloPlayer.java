package ubc.cosc322;

import java.util.Map;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Random;
import ygraph.ai.smartfox.games.amazons.AmazonsGameMessage;

/* MonteCarloPlayer.java
 *
 * A monte carlo player that makes moves using the monte carlo tree search algorithm.
 * It extends the BasePlayer class and overrides the processMove method to select moves
 * based on simulated playouts.
 *
 * The bot is performing better than previously, but there is still improvement to be made.
 * We have to be careful to not run out of memory. Each node holds the board state, so there is a
 * nxn board on each node. This probably isn't optimal, but it's working for now.
 * 
 * PARAMETERS:
 *
 * TODO:
 * - Fine tune the bot so it picks better moves early game 
 * - Add parallelization to the MCTS algorithm
 * - Possibly add a better heuristic
 */

public class MonteCarloPlayer extends BasePlayer {

    // MCTS parameters:
    // These can be adjusted to improve the bot's performance.
    private static final int MAX_DEPTH = 20;
    private static final long MAX_TIME = 10 * 1000;
    private static final long MAX_MEMORY = 4L * 1024 * 1024 * 1024;
    private static final int  MOVE_CHOICES = 30;

    // Heuristic weights:
    // Higher decimal values mean the bot will prioritize that heuristic more.
    private static final double QUEEN_WEIGHT = 0.3;
    private static final double ARROW_WEIGHT = 0.3;
    private static final double MOBILITY_WEIGHT = 0.6;
    // New territory control weight
    private static final double TERRITORY_WEIGHT = 0.5;

    private Random random = new Random();
    private static int moveCounter = 0;

    public MonteCarloPlayer(String userName, String passwd) {
        super(userName, passwd);
    }
    
    @Override
    protected void processMove(Map<String, Object> msgDetails) {
        LocalBoard rootBoard = localBoard.copy();
        int ourPlayer = localBoard.getLocalPlayer();
        System.out.println(ourPlayer);
    
        TreeNode rootNode = new TreeNode(rootBoard, null, null);
        System.out.println("Starting MCTS with " + MAX_TIME/1000 + " seconds and " + MAX_MEMORY/1024/1024 + " MB memory limit.");
    
        // These get the time and memory usage before starting the MCTS algorithm, can adjust mem and time limit a top of class
        long startTime = System.nanoTime();
        long initialMemory = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();
        long iterationCount = 0;

        while (true) {
            // Stops when memory exceeded
            long currentMemory = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();
            if (currentMemory - initialMemory > MAX_MEMORY) {
                System.out.println("Memory limit exceeded. Stopping MCTS.");
                break;
            }
    
            // Stops when time limit is up
            long elapsedTime = (System.nanoTime() - startTime) / 1000000;
            if (elapsedTime > MAX_TIME) {
                System.out.println("Time limit exceeded. Stopping MCTS.");
                break;
            }
    
            // Step 1: Selection
            TreeNode selectedNode = treePolicy(rootNode);
    
            // Step 2: Simulation
            LocalBoard simulationBoard = selectedNode.board.copy();
            boolean simulationResult = simulatePlayout(simulationBoard, ourPlayer);
            int result = simulationResult ? 1 : 0;
    
            // Step 3: Backpropagation
            backpropagate(selectedNode, result);

            iterationCount++;
        }
        System.out.println("MCTS iterations: " + iterationCount);
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
        moveCounter++;
    }

    private boolean isTerminal(LocalBoard board) {
        int currentPlayer = board.getLocalPlayer();
        MoveActionFactory factory = new MoveActionFactory(board.getState(), currentPlayer);
        return factory.getActions().isEmpty();
    }

    private TreeNode bestUCTChild(TreeNode node) {
        TreeNode bestChild = null;
        double bestUCT = Double.NEGATIVE_INFINITY;
        double C = Math.sqrt(2);
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
    
    /*
     * According to the Amazons Strategy PDF page 2, the creator claims that the best starting positions are
     * close to {8, 3}, {8, 8}, {3, 3}, {3, 8}. This heuristic function calculates the distance from the
     * target queen position to these optimal positions and returns a score based on that.
     */
    private double optimalPositionHeuristic(Map<String, Object> moveMap, LocalBoard board) {
        List<Integer> queenTarget = (List<Integer>) moveMap.get(AmazonsGameMessage.QUEEN_POS_NEXT);
        int x = queenTarget.get(0);
        int y = queenTarget.get(1);
        
        int[][] optimalPositions = {
            {8, 3}, {8, 8}, {3, 3}, {3, 8}
        };
        
        double minDistance = Double.MAX_VALUE;
        for (int[] position : optimalPositions) {
            double distance = Math.abs(x - position[0]) + Math.abs(y - position[1]);
            minDistance = Math.min(minDistance, distance);
        }
        
        return 100.0 * (1.0 - (minDistance / 7));
    }
    
    /*
     * This heuristic function calculates the distance from the arrow target to the center of the board.
     * Usually, arrows are more effective when they are closer to the center of the board. This function
     * returns a score based on that.
     */
    private double arrowHeuristic(Map<String, Object> moveMap, LocalBoard board) {
        List<Integer> arrowTarget = (List<Integer>) moveMap.get(AmazonsGameMessage.ARROW_POS);
        int x = arrowTarget.get(0);
        int y = arrowTarget.get(1);
    
        int centerX = 5;
        int centerY = 5;
        
        double distanceToCenter = Math.abs(x - centerX) + Math.abs(y - centerY);
    
        double edgePenalty = 0;
        if (x == 1 || x == 10 || y == 1 || y == 10) {
            edgePenalty = 2;
        }
    
        return 100.0 * (1.0 - (distanceToCenter + edgePenalty) / 10.0);
    }

    /**
     * This heuristic evaluates the mobility of the queen after it moves to the target position.
     * It returns the amount of moves a queen has after it moves to the target position.
     * This tries to keep queens in positions where they have more moves.
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

        double score = 0;
        if (!validMoves.isEmpty()) {
            score = validMoves.size() * 3;
        }

        return score;
    }
    
    /**
     * This heuristic calculates territory control by estimating the number of empty cells
     * accessible from the queen's target position. It simulates the board after applying the move,
     * temporarily treats the queen's new square as empty (to simulate movement), and then uses a flood fill
     * (queen-like moves) to count reachable cells. The final score is normalized to a 0–100 scale.
     */
    private double territoryControlHeuristic(Map<String, Object> moveMap, LocalBoard board) {
        LocalBoard boardCopy = board.copy();
        List<Integer> queenTarget = (List<Integer>) moveMap.get(AmazonsGameMessage.QUEEN_POS_NEXT);
        List<Integer> queenCurrent = (List<Integer>) moveMap.get(AmazonsGameMessage.QUEEN_POS_CURR);
        List<Integer> arrowTarget = (List<Integer>) moveMap.get(AmazonsGameMessage.ARROW_POS);
        MoveAction moveAction = new MoveAction(queenCurrent, queenTarget, arrowTarget);
        boardCopy.updateState(moveAction);
        
        int[][] state = boardCopy.getState();
        int rows = state.length;
        int cols = state[0].length;
        int startX = queenTarget.get(0);
        int startY = queenTarget.get(1);
        
        int temp = state[startX][startY];
        state[startX][startY] = 0;
        
        boolean[][] visited = new boolean[rows][cols];
        visited[startX][startY] = true;
        int territoryCount = floodFill(state, startX, startY, visited);
        
        state[startX][startY] = temp;
        
        double normalizedScore = 100.0 * ((double) territoryCount / (rows * cols));
        return normalizedScore;
    }
    
    /**
     * Flood fill using queen moves (in 8 directions) to count reachable empty cells.
     */
    private int floodFill(int[][] board, int x, int y, boolean[][] visited) {
        int count = 0;
        int rows = board.length;
        int cols = board[0].length;
        // All eight directions for queen moves.
        int[][] directions = {
            {-1, 0}, {1, 0}, {0, -1}, {0, 1},
            {-1, -1}, {-1, 1}, {1, -1}, {1, 1}
        };
        
        for (int[] dir : directions) {
            int newX = x;
            int newY = y;
            // Move step by step in this direction.
            while (true) {
                newX += dir[0];
                newY += dir[1];
                if (newX < 0 || newX >= rows || newY < 0 || newY >= cols) {
                    break;
                }
                // Stop if an obstacle is encountered.
                if (board[newX][newY] != 0) {
                    break;
                }
                if (!visited[newX][newY]) {
                    visited[newX][newY] = true;
                    count++;
                    count += floodFill(board, newX, newY, visited);
                } else {
                    // Already visited: no need to continue further in this direction.
                    break;
                }
            }
        }
        
        return count;
    }
    
    /*
     * This function calculates the combined heuristic score for a given move.
     * The combined heuristic score is a weighted sum of the queen position heuristic,
     * arrow position heuristic, queen mobility, and territory control.
     */
    private double calculateCombinedHeuristic(Map<String, Object> moveMap, LocalBoard board) {
        double queenScore = optimalPositionHeuristic(moveMap, board);
        double arrowScore = arrowHeuristic(moveMap, board);
        double mobilityScore = queenMobilityHeuristic(moveMap, board);
        double territoryScore = territoryControlHeuristic(moveMap, board);
    
        double queenWeight = QUEEN_WEIGHT;
        double arrowWeight = ARROW_WEIGHT;
        double mobilityWeight = MOBILITY_WEIGHT;
        double territoryWeight = TERRITORY_WEIGHT;
    
        double totalWeight = queenWeight + arrowWeight + mobilityWeight + territoryWeight;
        queenWeight /= totalWeight;
        arrowWeight /= totalWeight;
        mobilityWeight /= totalWeight;
        territoryWeight /= totalWeight;
    
        return queenScore * queenWeight 
             + arrowScore * arrowWeight 
             + mobilityScore * mobilityWeight 
             + territoryScore * territoryWeight;
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
        int currentResult = result;
        int ourPlayer = localBoard.getLocalPlayer();
        
        while (current != null) {
            current.visits++;
            boolean isOurPlayerTurn = (current.board.getLocalPlayer() == ourPlayer);
            if (isOurPlayerTurn) {
                current.wins += currentResult;
            } else {
                current.wins += (1 - currentResult);
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
                moveMap.put(AmazonsGameMessage.QUEEN_POS_NEXT, move.getQueenTarget());
                moveMap.put(AmazonsGameMessage.ARROW_POS, move.getArrowTarget());

                double queenHeuristicValue = optimalPositionHeuristic(moveMap, child.board) * QUEEN_WEIGHT;
                double arrowHeuristicValue = arrowHeuristic(moveMap, child.board) * ARROW_WEIGHT;
                double mobilityHeuristicValue = queenMobilityHeuristic(moveMap, child.board) * MOBILITY_WEIGHT;
                double territoryHeuristicValue = territoryControlHeuristic(moveMap, child.board) * TERRITORY_WEIGHT;
                double totalHeuristicValue = queenHeuristicValue + arrowHeuristicValue + mobilityHeuristicValue + territoryHeuristicValue;

                System.out.print((i + 1) + ". Move:");
                System.out.print("  Q:(" + queenXCurrent + "," + queenYCurrent + ")");
                System.out.print("  to (" + queenXTarget + "," + queenYTarget + ")");
                System.out.print("  A:(" + arrowXTarget + "," + arrowYTarget + ")");
                System.out.print("  Visits: " + child.visits);
                System.out.print("  Win rate: " + formattedWinRate);
                System.out.print("  Q: " + queenHeuristicValue);
                System.out.print("  A: " + arrowHeuristicValue);
                System.out.print("  M: " + mobilityHeuristicValue);
                System.out.print("  T: " + territoryHeuristicValue);
                System.out.print("  Total Heuristic: " + totalHeuristicValue);
                System.out.println();
            }
            System.out.println("Total Moves Considered: " + rootNode.children.size());
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

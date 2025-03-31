package ubc.cosc322;

import java.util.Map;
import java.util.Queue;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedList;
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
    private static final int MAX_DEPTH = 1;
    private static final long MAX_TIME = 10 * 500;
    private static final long MAX_MEMORY = 4L * 1024 * 1024 * 1024;
    private static int MOVE_CHOICES = 20;
    private static int INCREASE_MOVE_CHOICES = 3;

    // Heuristic weights:
    // Higher values mean the bot will prioritize that heuristic more.
    private static final double MOBILITY_WEIGHT = 0.3;
    private static final double BLOCKING_WEIGHT = 1.0;
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
            // Stops when memory exeeded
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
    * This heuristic evaluates the mobility of the queen after it moves to the target position.
    * It returns the amount of moves a queen has after it moves to the target position.
    * This trys to keep queens in positions where they have more moves.
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

        if (validMoves.size() > 0) {
            score = validMoves.size() * 3;
        }

        return score;
    }

    /*
    * This heuristic evaluates how effectively a move blocks the opponent's queens.
    * It calculates the reduction in opponent's mobility after our move compared to before our move.
    * Higher values indicate more effective blocking.
    */
    private double opponentBlockingHeuristic(Map<String, Object> moveMap, LocalBoard board) {
        int opponentPlayer = (board.getLocalPlayer() == LocalBoard.QUEEN_PLAYER_1) ? LocalBoard.QUEEN_PLAYER_2 : LocalBoard.QUEEN_PLAYER_1;
        
        // This part gets all the queen moves BEFORE the move
        MoveActionFactory factory = new MoveActionFactory(board.getState(), opponentPlayer);
        List<List<Integer>> opponentQueens = factory.getAllQueenCurrents();
    
        Map<List<Integer>, List<List<Integer>>> queenToMovesBeforeMap = new HashMap<>();
        int mobilityBefore = 0;
        
        for (List<Integer> queen : opponentQueens) {
            int x = queen.get(0);
            int y = queen.get(1);
            List<List<Integer>> validMoves = factory.getValidMoves(x, y);
            queenToMovesBeforeMap.put(queen, validMoves);
            mobilityBefore += validMoves.size();
        }
    
        List<Integer> queenCurrent = (List<Integer>) moveMap.get(AmazonsGameMessage.QUEEN_POS_CURR);
        List<Integer> queenTarget = (List<Integer>) moveMap.get(AmazonsGameMessage.QUEEN_POS_NEXT);
        List<Integer> arrowTarget = (List<Integer>) moveMap.get(AmazonsGameMessage.ARROW_POS);
    
        LocalBoard simulationBoard = board.copy();
        MoveAction moveAction = new MoveAction(queenCurrent, queenTarget, arrowTarget);
        simulationBoard.updateState(moveAction);
        
        // This part gets all the queen moves AFTER the move
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
 * Evaluates the territory controlled by our queens vs. the opponent's queens.
 * It uses a flood-fill algorithm starting from each queen's position to count
 * the number of reachable empty squares.
 */
    private double territoryControlHeuristic(Map<String, Object> moveMap, LocalBoard board) {
    // Simulate the move
     LocalBoard simulatedBoard = board.copy();
     List<Integer> queenCurrent = (List<Integer>) moveMap.get(AmazonsGameMessage.QUEEN_POS_CURR);
        List<Integer> queenTarget = (List<Integer>) moveMap.get(AmazonsGameMessage.QUEEN_POS_NEXT);
     List<Integer> arrowTarget = (List<Integer>) moveMap.get(AmazonsGameMessage.ARROW_POS);
     MoveAction moveAction = new MoveAction(queenCurrent, queenTarget, arrowTarget);
     simulatedBoard.updateState(moveAction);
    
     // Compute accessible territory for both players using a flood-fill algorithm.
        int ourTerritory = floodFillTerritory(simulatedBoard, board.getLocalPlayer());
     int opponentTerritory = floodFillTerritory(simulatedBoard, board.getLocalPlayer() == LocalBoard.QUEEN_PLAYER_1 
                                                 ? LocalBoard.QUEEN_PLAYER_2 : LocalBoard.QUEEN_PLAYER_1);
    
    // Territory advantage: higher is better for us.
     return ourTerritory - opponentTerritory;
    }

    /**
 * Computes the territory (i.e. the number of reachable empty squares)
 * for the given player by performing a queen-move flood-fill.
 * Assumes that the LocalBoard class provides:
 *   - getWidth() and getHeight() methods.
 *   - getQueenPositions(int player): returns a List<Point> of queen positions for the player.
 *   - isEmpty(int x, int y): returns true if the cell at (x, y) is empty.
 *
 * @param board The board to evaluate.
 * @param player The player whose territory to compute.
 * @return The total count of empty squares reachable by any queen of the player.
 */
private int floodFillTerritory(LocalBoard board, int player) {
    int width = board.getWidth();
    int height = board.getHeight();
    boolean[][] visited = new boolean[width][height];
    Queue<Point> queue = new LinkedList<>();
    
    // Initialize the queue with all queen positions for the given player.
    // Assume board.getQueenPositions(player) returns a List<Point> where Point has x and y fields.
    List<Point> queenPositions = board.getQueenPositions(player);
    for (Point p : queenPositions) {
        if (!visited[p.x][p.y]) {
            visited[p.x][p.y] = true;
            queue.add(p);
        }
    }
    
    int territoryCount = 0;
    // Directions for queen-like moves (8 directions)
    int[] dx = {-1, -1, -1, 0, 0, 1, 1, 1};
    int[] dy = {-1, 0, 1, -1, 1, -1, 0, 1};
    
    // Perform flood-fill along queen paths.
    while (!queue.isEmpty()) {
        Point current = queue.poll();
        
        // Explore each of the eight directions.
        for (int i = 0; i < 8; i++) {
            int nx = current.x + dx[i];
            int ny = current.y + dy[i];
            // Continue in this direction until hitting a boundary or a non-empty cell.
            while (nx >= 0 && nx < width && ny >= 0 && ny < height && board.isEmpty(nx, ny)) {
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


    

    /*
     * This function calculates the combined heuristic score for a given move.
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
                moveMap.put(AmazonsGameMessage.QUEEN_POS_CURR, move.getQueenCurrent());
                moveMap.put(AmazonsGameMessage.QUEEN_POS_NEXT, move.getQueenTarget());
                moveMap.put(AmazonsGameMessage.ARROW_POS, move.getArrowTarget());

                double mobilityHeuristicValue = Math.round(queenMobilityHeuristic(moveMap, child.board) * MOBILITY_WEIGHT * 100.0) / 100.0;
                double blockingHeuristicValue = -Math.round(opponentBlockingHeuristic(moveMap, child.board) * BLOCKING_WEIGHT * 100.0) / 100.0;
                double totalHeuristicValue = (int)blockingHeuristicValue + mobilityHeuristicValue ;

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

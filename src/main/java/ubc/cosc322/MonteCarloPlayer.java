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
 * - ITERATIONS: The number of iterations the MCTS algorithm will run. Higher = better moves, but slower and might run out of memory.
 * - ITERATIONS_MULTIPLIER: Since there is many possible moves early game, we can increase the number of iterations as the game progresses.
 * - MAX_DEPTH: The maximum depth the MCTS algorithm will go. Higher = better moves, but slower. Memory might also be an issue.
 * - PRINT_ITERATIONS: Print the number of iterations every 10000 iterations. DO NOT enable this if we are playing in tournament.
 * 
 *
 * TODO:
 * - Fine tune the bot so it picks better moves early game 
 * - Add parallelization to the MCTS algorithm
 * - Possibly add a better heuristic
 */
public class MonteCarloPlayer extends BasePlayer {
    private int ITERATIONS = 4000;
    private static final double ITERATIONS_MULTIPLIER = 1.14;
    private static final int MAX_DEPTH = 20;
    private static final boolean PRINT_ITERATIONS = false;

    private Random random = new Random();

    public MonteCarloPlayer(String userName, String passwd) {
        super(userName, passwd);
    }
    
    @Override
    protected void processMove(Map<String, Object> msgDetails) {
        LocalBoard rootBoard = localBoard.copy();
        int ourPlayer = localBoard.getLocalPlayer();
        System.out.println(ourPlayer);
        

        TreeNode rootNode = new TreeNode(rootBoard, null, null);
        System.out.println("Starting MCTS with " + ITERATIONS + " iterations");
        
        for (int i = 0; i < ITERATIONS; i++) {
            // Step 1: Selection
            TreeNode selectedNode = treePolicy(rootNode);
            
            // Step 2: Simulation
            LocalBoard simulationBoard = selectedNode.board.copy();
            boolean simulationResult = simulatePlayout(simulationBoard, ourPlayer);
            int result = simulationResult ? 1 : 0;
            
            // Step 3: Backpropagation
            backpropagate(selectedNode, result);
            
            if(PRINT_ITERATIONS && i % 10000 == 0) {
                System.out.println("Iteration: " + i + " out of " + ITERATIONS);
            }
        }
        ITERATIONS *= ITERATIONS_MULTIPLIER;
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
    
        int earlyGameMovesLimit = 200;
        boolean isEarlyGame = node.parent == null || node.visits < earlyGameMovesLimit;
    
        if (isEarlyGame) {
            node.untriedMoves.sort((move1, move2) -> {
                int score1 = centerDistHeuristic(move1, node.board);
                int score2 = centerDistHeuristic(move2, node.board);
                return Integer.compare(score1, score2);
            });
    
            if (node.untriedMoves.size() > earlyGameMovesLimit) {
                node.untriedMoves = node.untriedMoves.subList(0, earlyGameMovesLimit);
            }
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
    

    private int centerDistHeuristic(Map<String, Object> moveMap, LocalBoard board) {
        List<Integer> queenTarget = (List<Integer>) moveMap.get(AmazonsGameMessage.QUEEN_POS_NEXT);
        int centerX = 10 / 2;
        int centerY = 10 / 2;
        
        int distance = Math.abs(queenTarget.get(0) - centerX) + Math.abs(queenTarget.get(1) - centerY);
        return distance;
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

                System.out.print((i + 1) + ". Move:");
                System.out.print("  Q:(" + queenXCurrent + "," + queenYCurrent + ")");
                System.out.print("  to (" + queenXTarget + "," + queenYTarget + ")");
                System.out.print("  A:(" + arrowXTarget + "," + arrowYTarget + ")");
                System.out.print("  Visits: " + child.visits);
                System.out.print("  Win rate: " + formattedWinRate);
                System.out.println();

            }
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

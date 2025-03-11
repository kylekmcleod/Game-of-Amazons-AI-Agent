package ubc.cosc322;

import java.util.Map;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;

import ygraph.ai.smartfox.games.amazons.AmazonsGameMessage;

/* MonteCarloPlayer.java (incomplete)
 *
 * A monte carlo player that makes moves using the monte carlo tree search algorithm. It extends the BasePlayer class.
 * The processMove method is overridden to make moves using the monte carlo tree search algorithm.
 * 
 * Complete the random player first before implementing the monte carlo tree search algorithm.
 */
public class MonteCarloPlayer extends BasePlayer {
    private TreeNode root;

    private final long MAX_RUNTIME = 5000;
    private final double EXPLORATION_FACTOR = Math.sqrt(2);

    public MonteCarloPlayer(String userName, String passwd) {
        super(userName, passwd);
    }
    
    @Override
    protected void processMove(Map<String, Object> msgDetails) {
        root = new TreeNode(localBoard);

    }

    private class TreeNode {
        LocalBoard board;
        TreeNode parent;
        List<TreeNode> children = new ArrayList<>();
        MoveAction action;
        int wins = 0;
        int visits = 0;
    
        public TreeNode(LocalBoard board, TreeNode parent, MoveAction action) {
            this.board = board.copy();
            this.parent = parent;
            this.action = action;
        }
        public TreeNode(LocalBoard board) {
			this(board, null, null);
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

        public MoveAction getAction() {
            return action;
        }

        private double getUCB() {
            if (visits == 0) {
                return Double.MAX_VALUE;
            }

            double uct = (double) wins / visits;
            if (parent != null) {
                uct += EXPLORATION_FACTOR * Math.sqrt(Math.log(parent.visits) / visits);
            }

            return uct;
        }

        public TreeNode expand() {
            MoveActionFactory actionFactory = new MoveActionFactory(board.getState(), 2);
            List<Map<String, Object>> possibleMoves = actionFactory.getActions();

            if (possibleMoves.isEmpty() || possibleMoves.size() == children.size()) {
                return null;
            }
        
            for (Map<String, Object> move : possibleMoves) {
                boolean alreadyExists = false;
                List<Integer> queenCurrent = (List<Integer>) move.get(AmazonsGameMessage.QUEEN_POS_CURR);
                List<Integer> queenTarget = (List<Integer>) move.get(AmazonsGameMessage.QUEEN_POS_NEXT);
                List<Integer> arrowTarget = (List<Integer>) move.get(AmazonsGameMessage.ARROW_POS);
                MoveAction moveAction = new MoveAction(queenCurrent, queenTarget, arrowTarget);

                for (TreeNode child : children) {
                    if (child.action.equals(moveAction)) {
                        alreadyExists = true;
                        break;
                    }
                }
        
                if (!alreadyExists) {
                    LocalBoard newBoard = board.copy();
                    newBoard.updateState(moveAction);
                    newBoard.localPlayer = (board.localPlayer == 1) ? 2 : 1;
        
                    TreeNode newChild = new TreeNode(newBoard, this, moveAction);
                    children.add(newChild);
                    
                    return newChild;
                }
            }
        
            return null;
        }
        
    }

    public static void testTreeNode() {
        /*
         * 
         * The tree structure looks like this:
         *
         *                 Initial Board           (Root Node)
         *                /     |      \
         *           Move1    Move2    Move(n)     (Find all moves and create a child node for each move)
         *         /   |   \ 
         *    Move1  Move2 Move(n)                 (Find all moves FROM THE PARENT and create a child node for each move. Do for all parent nodes)
         *
         * 
         * Here, the root board is the starting point of the game.
         * The root node has num of possible moves children, each representing a move action.
         *
         * Each child node is independent, with its own board state reflecting the result
         * of the move applied to its parent board.
         */
        System.out.println("Testing TreeNode Structure...");
        
        // Create initial board
        LocalBoard rootBoard = new LocalBoard();
        TreeNode rootNode = new MonteCarloPlayer("test", "test").new TreeNode(rootBoard, null, null);

        // Sample move action
        MoveAction move1 = new MoveAction(Arrays.asList(10, 4), Arrays.asList(9, 4), Arrays.asList(9, 5));
        MoveAction move2 = new MoveAction(Arrays.asList(1, 4), Arrays.asList(2, 4), Arrays.asList(2, 5));
        MoveAction move3 = new MoveAction(Arrays.asList(7, 10), Arrays.asList(6, 10), Arrays.asList(6, 9));
        MoveAction move4 = new MoveAction(Arrays.asList(1, 7), Arrays.asList(2, 7), Arrays.asList(2, 6));

        // Add children to first level (moves from base state)
        LocalBoard childBoard1 = rootBoard.copy();
        childBoard1.updateState(move1);
        rootNode.addChild(childBoard1, move1);

        LocalBoard childBoard2 = rootBoard.copy();
        childBoard2.updateState(move2);
        rootNode.addChild(childBoard2, move2);

        LocalBoard childBoard3 = rootBoard.copy();
        childBoard3.updateState(move3);
        rootNode.addChild(childBoard3, move3);

        // Add children to second level (moves from child 1)
        LocalBoard childBoard4 = childBoard1.copy();
        childBoard4.updateState(move4);
        rootNode.children.get(0).addChild(childBoard4, move4);

        // Print the board states
        System.out.println("Root Board:");
        rootBoard.printState();

        System.out.println("Child Board 1:");
        rootNode.children.get(0).board.printState();

        System.out.println("Child Board 2:");
        rootNode.children.get(1).board.printState();

        System.out.println("Child Board 3:");
        rootNode.children.get(2).board.printState();

        System.out.println("Child Board 4 (from Board 1):");
        rootNode.children.get(0).children.get(0).board.printState();

        // Check if the root board is unchanged
        if (Arrays.deepEquals(rootBoard.getState(), childBoard1.getState())) {
            System.out.println("ERROR: Root board was modified!");
        } else {
            System.out.println("SUCCESS: Root board remains unchanged.");
        }
    }

    public static void testExpandMethod() {
        System.out.println("Testing expand() method...");
    
        // Create initial board state
        LocalBoard rootBoard = new LocalBoard();
        TreeNode rootNode = new MonteCarloPlayer("test", "test").new TreeNode(rootBoard, null, null);
    
        // Expand once
        TreeNode newChild = rootNode.expand();
        
        if (newChild == null) {
            System.out.println("ERROR: expand() returned null when it should have expanded a move.");
        } else {
            System.out.println("SUCCESS: expand() created a new child node.");
            System.out.println("MoveAction: " + newChild.getAction());
        }
    
        // Expand multiple times
        List<TreeNode> createdChildren = new ArrayList<>();
        while (true) {
            TreeNode nextChild = rootNode.expand();
            if (nextChild == null) {
                break;
            }
            createdChildren.add(nextChild);
        }
    
        System.out.println("Total children created: " + createdChildren.size());
        
        if (createdChildren.isEmpty()) {
            System.out.println("ERROR: No children were created by expand().");
        } else {
            System.out.println("SUCCESS: expand() created multiple valid child nodes.");
        }
    
        // Try expanding again (should return null now)
        if (rootNode.expand() == null) {
            System.out.println("SUCCESS: expand() correctly stops when all moves are explored.");
        } else {
            System.out.println("ERROR: expand() is creating duplicate children.");
        }
    }
    

    // Main method for testing
    public static void main(String[] args) {
        testExpandMethod();
    }
}


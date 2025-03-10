package ubc.cosc322;

import java.util.Map;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Random;
import ygraph.ai.smartfox.games.amazons.AmazonsGameMessage;

/* MonteCarloPlayer.java (incomplete)
 *
 * A monte carlo player that makes moves using the monte carlo tree search algorithm. It extends the BasePlayer class.
 * The processMove method is overridden to make moves using the monte carlo tree search algorithm.
 * 
 * Complete the random player first before implementing the monte carlo tree search algorithm.
 */
public class MonteCarloPlayer extends BasePlayer {

    public MonteCarloPlayer(String userName, String passwd) {
        super(userName, passwd);
    }
    
    @Override
    protected void processMove(Map<String, Object> msgDetails) {
        // Do process move here
        // Do not complete this until we have a working random player
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

    // Main method for testing
    public static void main(String[] args) {
        testTreeNode();
    }
}


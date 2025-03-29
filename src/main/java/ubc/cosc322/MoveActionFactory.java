package ubc.cosc322;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import ygraph.ai.smartfox.games.amazons.AmazonsGameMessage;

/* MoveActionFactory.java
 * 
 * This class generates all possible moves for the current player. It takes the current board state as a 2D array and the current player as input.
 * 
 * The board generated in AmazonsLocalBoard is an 11x11 2D array because the moves are 1 indexed. Therefore, this algorithm ignores
 * the 0th row and 0th column.
 * 
 */
public class MoveActionFactory {

    private int[][] board;
    private int currentPlayer;

    public MoveActionFactory(int[][] board, int currentPlayer) {
        this.board = board;
        this.currentPlayer = currentPlayer;
    }

    public List<Map<String, Object>> getActions() {
        List<Map<String, Object>> actions = new ArrayList<>();
        List<List<Integer>> allQueens = getAllQueenCurrents();

        for (List<Integer> queenCurrent : allQueens) {
            List<List<Integer>> queenTargets = getValidMoves(queenCurrent.get(0), queenCurrent.get(1));

            for (List<Integer> queenTarget : queenTargets) {
                List<List<Integer>> arrowTargets = getValidMoves(queenTarget.get(0), queenTarget.get(1));
                arrowTargets.add(queenCurrent);

                for (List<Integer> arrowTarget : arrowTargets) {
                    Map<String, Object> move = new HashMap<>();
                    move.put(AmazonsGameMessage.QUEEN_POS_CURR, new ArrayList<>(queenCurrent));
                    move.put(AmazonsGameMessage.QUEEN_POS_NEXT, new ArrayList<>(queenTarget));
                    move.put(AmazonsGameMessage.ARROW_POS, new ArrayList<>(arrowTarget));

                    actions.add(move);
                }
            }
        }
        return actions;
    }

    // Get all queen positions for the current player
    public List<List<Integer>> getAllQueenCurrents() {
        List<List<Integer>> queenPositions = new ArrayList<>();
        for (int row = 1; row <= 10; row++) {
            for (int col = 1; col <= 10; col++) {
                if (board[row][col] == currentPlayer) { 
                    queenPositions.add(List.of(row, col));
                }
            }
        }
        return queenPositions;
    }

    // Get all queen positions for the opposing player
    public List<List<Integer>> getAllOpponentQueenCurrents() {
        List<List<Integer>> opponentQueenPositions = new ArrayList<>();
        for (int row = 1; row <= 10; row++) {
            for (int col = 1; col <= 10; col++) {
                if (board[row][col] != currentPlayer) { 
                    opponentQueenPositions.add(List.of(row, col));
                }
            }
        }
        return opponentQueenPositions;
    }

    
    // Get all valid moves for a queen at a given position
    public List<List<Integer>> getValidMoves(int row, int col) {
        List<List<Integer>> moves = new ArrayList<>();
        int[][] directions = {
            {0, 1}, {0, -1}, {1, 0}, {-1, 0}, 
            {1, 1}, {1, -1}, {-1, 1}, {-1, -1}
        };

        for (int[] dir : directions) {
            int newRow = row, newCol = col;
            while (isValidMove(newRow + dir[0], newCol + dir[1])) {
                newRow += dir[0];
                newCol += dir[1];
                moves.add(List.of(newRow, newCol));
            }
        }
        return moves;
    }
    
    // Checks if the move is on the board and the position is empty
    private boolean isValidMove(int row, int col) {
        return (row >= 1 && row <= 10 && col >= 1 && col <= 10 && board[row][col] == 0);
    }

    // Main method for testing
    public static void main(String[] args) {
        int[][] board = new int[11][11];
        for (int i = 1; i <= 10; i++) {
            for (int j = 1; j <= 10; j++) {
                board[i][j] = 0;
            }
        }
        // Test positions
        board[5][5] = 1;
        board[4][5] = -1;
        board[4][6] = -1;
        board[5][4] = -1;
        board[5][6] = -1;
        board[6][4] = -1;
        board[6][5] = -1;
        board[6][6] = -1;

        MoveActionFactory factory = new MoveActionFactory(board, 1);

        // Print queen positions
        List<List<Integer>> queenPositions = factory.getAllQueenCurrents();
        System.out.println("Queen positions for player 1:");
        for (List<Integer> queenPos : queenPositions) {
            System.out.println("Queen at: " + queenPos);
        }

        List<Map<String, Object>> actions = factory.getActions();

        System.out.println("Possible moves for player 1:");
        for (Map<String, Object> move : actions) {
            System.out.println(move);
        }
    }
}

package ubc.cosc322;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import ygraph.ai.smartfox.games.amazons.AmazonsGameMessage;

/* MoveActionFactory.java
 * 
 * This class generates all possible moves for the current player. It takes the current board state as a 2D array and the current player as input.
 * We must find a way to get the current board state and the current player from the game client. Right now, it doesn't seem like the server
 * sends an updated board after each move, so we may have to keep track of the board state ourselves.
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

    private List<List<Integer>> getAllQueenCurrents() {
        List<List<Integer>> queenPositions = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            for (int j = 0; j < 10; j++) {
                if (board[9 - i][j] == currentPlayer) {
                    queenPositions.add(List.of(i + 1, j + 1));
                }
            }
        }
        return queenPositions;
    }

    private List<List<Integer>> getValidMoves(int x, int y) {
        List<List<Integer>> moves = new ArrayList<>();
        int[][] directions = {{0, 1}, {0, -1}, {1, 0}, {-1, 0}, {1, 1}, {1, -1}, {-1, 1}, {-1, -1}};

        for (int[] dir : directions) {
            int newX = x, newY = y;
            while (isValidMove(newX + dir[0], newY + dir[1])) {
                newX += dir[0];
                newY += dir[1];
                moves.add(List.of(newX, newY));
            }
        }
        return moves;
    }

    private boolean isValidMove(int x, int y) {
        return x >= 1 && x <= 10 && y >= 1 && y <= 10 && board[9 - (x - 1)][y - 1] == 0;
    }

    // Main method for testing
    public static void main(String[] args) {
        int[][] board = {
            {1, 0, 0, -1, 0, 0, 0, 0, 0, 0},
            {-1, -1, -1, -1, 0, 0, 0, 0, 0, 0},
            {0, 0, 0, 0, 0, 0, 0, 0, 0, 0},
            {0, 0, 0, 0, 0, 0, 0, 0, 0, 0},
            {0, 0, 0, 0, 0, 0, 0, 0, 0, 0},
            {0, 0, 0, 0, 0, 0, 0, 0, 0, 0},
            {0, 0, 0, 0, 0, 0, 0, 0, 0, 0},
            {0, 0, 0, 0, 0, 0, 0, 0, 0, 0},
            {0, 0, 0, 0, 0, 0, 0, 0, 0, 0},
            {0, 0, 0, 0, 0, 0, 0, 0, 0, 2}
        };

        MoveActionFactory factory = new MoveActionFactory(board, 1);
        List<Map<String, Object>> actions = factory.getActions();

        System.out.println("Possible moves for player 1:");
        for (Map<String, Object> move : actions) {
            System.out.println(move);
        }
    }
}

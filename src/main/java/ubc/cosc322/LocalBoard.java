package ubc.cosc322;

import java.util.Arrays;
import java.util.List;

/* AmazonsLocalBoard.java
 *
 * This class represents the local board state of the game. It contains the board state as a 2D array and methods to update the board state.
 * Each time a player makes a move, the board state is updated. The board state is then sent to the MoveActionFactory to generate all possible
 * moves for the current player.
 * 
 * WARNING:
 * The game is 1 indexed, so this class creates an 11x11 2D array so we can use 1-based indexing. 
 * This means we MUST ignore the 0th row and 0th column.
 * 
 * EXAMPLES:
 * board[1][1] is the bottom-left corner of the board.
 * board[10][10] is the top-right corner of the board.
 * board[0][0] is still in bounds, but we don't use it.
 * board[0][1] is still in bounds, but we don't use it.
 */
public class LocalBoard {
    private static final int BOARD_SIZE = 10;

    public static final int EMPTY = 0;
    public static final int QUEEN_PLAYER_1 = 1; // White
    public static final int QUEEN_PLAYER_2 = 2; // Black

    public int localPlayer = 2;
    private int[][] state = new int[BOARD_SIZE + 1][BOARD_SIZE + 1];

    public LocalBoard() {
        initializeDefaultBoard();
    }

    private void initializeDefaultBoard() {
        // itialize with all 0
        for (int i = 1; i <= BOARD_SIZE; i++) {
            for (int j = 1; j <= BOARD_SIZE; j++) {
                state[i][j] = EMPTY;
            }
        }
		
        // Place white queens (Player 1)
        state[4][1] = QUEEN_PLAYER_1;
        state[1][4] = QUEEN_PLAYER_1;
		state[1][7] = QUEEN_PLAYER_1;
		state[4][10] = QUEEN_PLAYER_1;

        // Place black queens (Player 2)
        state[7][1] = QUEEN_PLAYER_2;
        state[10][4] = QUEEN_PLAYER_2;
		state[10][7] = QUEEN_PLAYER_2;
		state[7][10] = QUEEN_PLAYER_2;
    }

    public int[][] getState() {
        return state;
    }

    public void setState(int[][] newState) {
        for (int i = 1; i <= BOARD_SIZE; i++) {
            System.arraycopy(newState[i], 1, state[i], 1, BOARD_SIZE);
        }
    }

	public void updateState(MoveAction action) {
		List<Integer> queenCurrent = action.getQueenCurrent();
		List<Integer> queenTarget = action.getQueenTarget();
		List<Integer> arrowTarget = action.getArrowTarget();
	
		int playerColor = getPositionValue(queenCurrent);
	
		setPositionValue(queenCurrent, EMPTY);
		setPositionValue(queenTarget, playerColor);

		setPositionValue(arrowTarget, -1);
	}

    public int getPositionValue(List<Integer> position) {
        return state[position.get(0)][position.get(1)];
    }

    public int getPositionValue(int x, int y) {
        return state[x][y];
    }

    public void setPositionValue(List<Integer> position, int value) {
        state[position.get(0)][position.get(1)] = value;
    }

    public void printState() {
		System.out.println("LOCAL BOARD STATE AFTER MOVE:");
        for (int i = BOARD_SIZE; i >= 1; i--) {
            System.out.println(Arrays.toString(Arrays.copyOfRange(state[i], 1, BOARD_SIZE + 1)));
        }
        System.out.println();
    }

    public LocalBoard copy() {
        LocalBoard copy = new LocalBoard();
        copy.localPlayer = localPlayer;
        for (int i = 1; i <= BOARD_SIZE; i++) {
            System.arraycopy(state[i], 1, copy.state[i], 1, BOARD_SIZE);
        }
        return copy;
    }

    public int getOpponent() {
        return localPlayer == 2 ? 1 : 2;
    }

    public void setLocalPlayer(int localPlayer) {
        this.localPlayer = localPlayer;
    }

	// Main method for testing
	public static void main(String[] args) {
		LocalBoard board = new LocalBoard();
		System.out.println("\nInitial Board State:");
		board.printState();

		List<Integer> queenCurrent = Arrays.asList(10, 4);
		List<Integer> queenTarget = Arrays.asList(9, 4);
		List<Integer> arrowTarget = Arrays.asList(9, 5);
	
		MoveAction move = new MoveAction(queenCurrent, queenTarget, arrowTarget);
	
		board.updateState(move);
	
		System.out.println("Updated Board State:");
		board.printState();
	}
	
}

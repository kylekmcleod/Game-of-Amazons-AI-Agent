package ubc.cosc322;

import java.util.Map;
import java.util.List;
import java.util.Random;
import ygraph.ai.smartfox.games.amazons.AmazonsGameMessage;

/* RandomPlayer.java
 *
 * A random player that makes random moves in the game. It extends the BasePlayer class.
 * The processMove method is overridden to make random moves.
 * 
 * We must get the valid moves for the current player and then randomly select one of them. This is our first milestone
 * before implementing the Monte Carlo Tree Search algorithm.
 * 
 */
public class RandomPlayer extends BasePlayer {

    private Random random;

    public RandomPlayer(String userName, String passwd) {
        super(userName, passwd);
        this.random = new Random();
    }

    @Override
    protected void processMove(Map<String, Object> msgDetails) {
        int[][] boardState = localBoard.getState();
    
        MoveActionFactory actionFactory = new MoveActionFactory(boardState, localBoard.localPlayer);
        List<Map<String, Object>> possibleMoves = actionFactory.getActions();
    
        if (possibleMoves.isEmpty()) {
            System.out.println("No valid moves available!");
            return;
        }
    
        Map<String, Object> selectedMove = possibleMoves.get(random.nextInt(possibleMoves.size()));
    
        List<Integer> queenCurrent = (List<Integer>) selectedMove.get(AmazonsGameMessage.QUEEN_POS_CURR);
        List<Integer> queenTarget = (List<Integer>) selectedMove.get(AmazonsGameMessage.QUEEN_POS_NEXT);
        List<Integer> arrowTarget = (List<Integer>) selectedMove.get(AmazonsGameMessage.ARROW_POS);
    
        MoveAction moveAction = new MoveAction(queenCurrent, queenTarget, arrowTarget);
        localBoard.updateState(moveAction);
    
        gamegui.updateGameState(selectedMove);
        gameClient.sendMoveMessage(selectedMove);
    }
}

package ubc.cosc322;

import java.util.Map;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Random;

import ygraph.ai.smartfox.games.amazons.AmazonsGameMessage;

public class RandomPlayer extends BasePlayer {

    private Random random;

    public RandomPlayer(String userName, String passwd) {
        super(userName, passwd);
        this.random = new Random();
    }

    @Override
    protected void processMove(Map<String, Object> msgDetails) {
        Map<String, Object> move = new HashMap<>();
        move.put(AmazonsGameMessage.QUEEN_POS_CURR, new ArrayList<>(List.of(10, 4)));
        move.put(AmazonsGameMessage.QUEEN_POS_NEXT, new ArrayList<>(List.of(9, 4)));
        move.put(AmazonsGameMessage.ARROW_POS, new ArrayList<>(List.of(9, 5)));
        
        gamegui.updateGameState(move);
        gameClient.sendMoveMessage(move);
    }
}

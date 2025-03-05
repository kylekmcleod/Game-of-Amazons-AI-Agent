package ubc.cosc322;

import java.util.Map;
import java.util.ArrayList;
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
}

package ubc.cosc322;

import ygraph.ai.smartfox.games.BaseGameGUI;
import ygraph.ai.smartfox.games.amazons.HumanPlayer;

/* Main.java
 *
 * The main class that runs the game. It creates a random player and starts the game.
 * Uncomment the line that creates a HumanPlayer object to play the game as a human.
 */
public class Main {
    public static void main(String[] args) {
        //HumanPlayer player = new HumanPlayer();
        //RandomPlayer player = new RandomPlayer("Player" + (int) (Math.random() * 10000), "2");
        //MonteCarloPlayer player = new MonteCarloPlayer("Player" + (int) (Math.random() * 10000), "2");
        Stockfish player = new Stockfish("Player" + (int) (Math.random() * 10000), "2");

        if (player.getGameGUI() == null) {
            player.Go();
        } else {
            BaseGameGUI.sys_setup();
            java.awt.EventQueue.invokeLater(player::Go);
        }
    }
}

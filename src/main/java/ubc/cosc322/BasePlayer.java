package ubc.cosc322;

import java.util.ArrayList;
import java.util.Map;

import ygraph.ai.smartfox.games.BaseGameGUI;
import ygraph.ai.smartfox.games.GameClient;
import ygraph.ai.smartfox.games.GameMessage;
import ygraph.ai.smartfox.games.GamePlayer;
import ygraph.ai.smartfox.games.amazons.AmazonsGameMessage;

/* BasePlayer.java
 *
 * This is the BasePlayer class which extends the GamePlayer class. It is an abstract class that provides the basic 
 * functionality for a player in the game. With this class, we can create a random player & a monte carlo player.
 * 
 * For exmaple, the RandomPlayer class extends the BasePlayer class and overrides the processMove method to make a random move.
 * The monte carlo player class extends the BasePlayer class and overrides the processMove method to make a move using the monte carlo algorithm.
 */
public abstract class BasePlayer extends GamePlayer {
    protected GameClient gameClient = null;
    protected BaseGameGUI gamegui = null;
    protected String userName;
    protected String passwd;

    public BasePlayer(String userName, String passwd) {
        this.userName = userName;
        this.passwd = passwd;
        this.gamegui = new BaseGameGUI(this);
    }

    protected abstract void processMove(Map<String, Object> msgDetails);

    protected void handleGameStart(Map<String, Object> msgDetails) {
        if (gamegui == null) {
            gamegui.updateGameState(msgDetails);
        }
        String whitePlayer = (String) msgDetails.get(AmazonsGameMessage.PLAYER_WHITE);
        int localPlayer = whitePlayer.equals(userName) ? 1 : 2;

        System.out.println("***** PLAYER INFO: " + userName + " (Player " + localPlayer + ") *****");
        if (localPlayer == 2) {
            processMove(msgDetails);
        } else {
            System.out.println("Waiting for the other player to make a move...");
        }
    }

    @Override
    public void onLogin() {
        System.out.println("Login successful! Logged in as: " + userName);
        userName = gameClient.getUserName();
        if (gamegui != null) {
            gamegui.setRoomInformation(gameClient.getRoomList());
        }
    }

    @Override
    public boolean handleGameMessage(String messageType, Map<String, Object> msgDetails) {
        System.out.println("Received game message: " + messageType);
        System.out.println("Details: " + msgDetails);

        switch (messageType) {
            case GameMessage.GAME_STATE_BOARD:
                gamegui.setGameState((ArrayList<Integer>) msgDetails.get(AmazonsGameMessage.GAME_STATE));
                break;
            case GameMessage.GAME_ACTION_MOVE:
                processMove(msgDetails);
                break;
            case GameMessage.GAME_ACTION_START:
                handleGameStart(msgDetails);
                break;
            default:
                System.out.println("Unhandled game message type: " + messageType);
        }
        return true;
    }
    

    @Override
    public String userName() {
        return userName;
    }

    @Override
    public GameClient getGameClient() {
        return this.gameClient;
    }

    @Override
    public BaseGameGUI getGameGUI() {
        return this.gamegui;
    }

    @Override
    public void connect() {
        gameClient = new GameClient(userName, passwd, this);
    }
}

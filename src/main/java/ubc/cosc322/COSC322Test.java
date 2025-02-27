package ubc.cosc322;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import sfs2x.client.entities.Room;
import ygraph.ai.smartfox.games.BaseGameGUI;
import ygraph.ai.smartfox.games.GameClient;
import ygraph.ai.smartfox.games.GameMessage;
import ygraph.ai.smartfox.games.GamePlayer;
import ygraph.ai.smartfox.games.amazons.AmazonsGameMessage;

/**
 * An example illustrating how to implement a GamePlayer
 * @author Yong Gao (yong.gao@ubc.ca)
 */
public class COSC322Test extends GamePlayer {

    private GameClient gameClient = null;
    private BaseGameGUI gamegui = null;

    private String userName = null;
    private String passwd = null;

    /**
     * The main method
     * @param args for name and passwd (current, any string would work)
     */
    public static void main(String[] args) {
        COSC322Test player = new COSC322Test("Player" + (int)Math.random() * 10000, "1");

        if (player.getGameGUI() == null) {
            player.Go();
        } else {
            BaseGameGUI.sys_setup();
            java.awt.EventQueue.invokeLater(() -> player.Go());
        }
    }

    /**
     * Constructor for COSC322Test
     * @param userName - Username for the game
     * @param passwd - Password for the game
     */
    public COSC322Test(String userName, String passwd) {
        this.userName = userName;
        this.passwd = passwd;
        this.gamegui = new BaseGameGUI(this);
    }

    @Override
    public void onLogin() {
        System.out.println("Login successful! Finding a room to join...");

        userName = gameClient.getUserName();
        if (gamegui != null) {
            gamegui.setRoomInformation(gameClient.getRoomList());
        }

        List<Room> rooms = gameClient.getRoomList();
        System.out.println("Available Rooms:");
        for (Room room : rooms) {
            System.out.println("- " + room.getName());
        }

        if (!rooms.isEmpty()) {
            Room roomToJoin = rooms.get(0);
            System.out.println("Joining room: " + roomToJoin.getName());
            gameClient.joinRoom(roomToJoin.getName());
        }
    }

    @Override
    public boolean handleGameMessage(String messageType, Map<String, Object> msgDetails) {
        System.out.println("Received game message: " + messageType);
        System.out.println("Details: " + msgDetails);

        switch (messageType) {
            case GameMessage.GAME_STATE_BOARD:
                if (gamegui != null) {
                    gamegui.setGameState((ArrayList<Integer>) msgDetails.get(AmazonsGameMessage.GAME_STATE));
                }
                break;

            case GameMessage.GAME_ACTION_MOVE:
                if (gamegui != null) {
                    gamegui.updateGameState(msgDetails);
                }

                // Here you can calculate your move based on the game state and send it to the server
                // Example:
                // Map<String, Object> myMove = calculateMyMove(msgDetails);
                // gameClient.sendMoveMessage(myMove);
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

    /**
     * Example method to calculate your move (placeholder for implementation)
     * @param msgDetails - Details of the current game state
     * @return A map representing your move
     */
    private Map<String, Object> calculateMyMove(Map<String, Object> msgDetails) {
        // Implement your move calculation logic here
        return null;
    }
}

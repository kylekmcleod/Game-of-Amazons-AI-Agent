package ubc.cosc322;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import sfs2x.client.entities.Room;
import ygraph.ai.smartfox.games.BaseGameGUI;
import ygraph.ai.smartfox.games.GameClient;
import ygraph.ai.smartfox.games.GameMessage;
import ygraph.ai.smartfox.games.GamePlayer;
import ygraph.ai.smartfox.games.amazons.AmazonsGameMessage;
import ygraph.ai.smartfox.games.amazons.HumanPlayer;

public class COSC322Test extends GamePlayer {

    private GameClient gameClient = null;
    private BaseGameGUI gamegui = null;
    private String userName;
    private String passwd;

    public static void main(String[] args) {
        // Uncomment to play as an AI bot
        COSC322Test player = new COSC322Test("Player" + (int) (Math.random() * 10000), "2");
        
        //HumanPlayer player = new HumanPlayer();
        
        if (player.getGameGUI() == null) {
            player.Go();
        } else {
            BaseGameGUI.sys_setup();
            java.awt.EventQueue.invokeLater(player::Go);
        }
    }

    public COSC322Test(String userName, String passwd) {
        this.userName = userName;
        this.passwd = passwd;
        this.gamegui = new BaseGameGUI(this);
    }

    @Override
    public void onLogin() {
        System.out.println("Login successful! Logged in as: " + userName);
        userName = gameClient.getUserName();
        if (gamegui != null) {
            gamegui.setRoomInformation(gameClient.getRoomList());
        }
        joinFirstAvailableRoom();
    }

    private void joinFirstAvailableRoom() {
        List<Room> rooms = gameClient.getRoomList();
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

    private void processMove(Map<String, Object> msgDetails) {
        if (gamegui != null) {
            gamegui.updateGameState(msgDetails);
        }
        System.out.println("========= YOUR TURN TO MOVE =========");
        Map<String, Object> myMove = calculateMove();
        if(gamegui != null){
            gamegui.updateGameState(myMove);
        }
        gameClient.sendMoveMessage(myMove);
        System.out.println("========= TURN DONE, PLEASE WAIT FOR YOUR TURN =========");
    }

    private void handleGameStart(Map<String, Object> msgDetails) {
        if (gamegui != null) {
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

    private Map<String, Object> calculateMove() {
        Map<String, Object> move = new HashMap<>();
        move.put("queen-position-current", new ArrayList<>(List.of(10, 4)));
        move.put("queen-position-next", new ArrayList<>(List.of(9, 4)));
        move.put("arrow-position", new ArrayList<>(List.of(9, 5)));
        return move;
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

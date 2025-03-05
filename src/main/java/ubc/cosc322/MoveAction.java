package ubc.cosc322;

import java.util.List;

/* MoveAction.java
 * 
 * This class represents a move action in the game. It contains the current queen position, the target queen position, and the target arrow position.
 * Use this to update the local board state and send the move to the game server.
 */
public class MoveAction {
    List<Integer> queenCurrent;
    private List<Integer> queenTarget;
    private List<Integer> arrowTarget;

    public MoveAction(List<Integer> queenCurrent, List<Integer> queenTarget, List<Integer> arrowTarget) {
        this.queenCurrent = queenCurrent;
        this.queenTarget = queenTarget;
        this.arrowTarget = arrowTarget;
    }

    public List<Integer> getQueenCurrent() {
        return queenCurrent;
    }

    public List<Integer> getQueenTarget() {
        return queenTarget;
    }

    public List<Integer> getArrowTarget() {
        return arrowTarget;
    }

    @Override
    public String toString() {
        return String.format("Move Queen from %s to %s, Shoot Arrow at %s", queenCurrent, queenTarget, arrowTarget);
    }
}

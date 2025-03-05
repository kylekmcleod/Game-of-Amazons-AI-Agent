package ubc.cosc322;

import java.util.List;

public class MoveAction {
    private List<Integer> queenCurrent;
    private List<Integer> queenTarget;
    private List<Integer> arrowTarget;

    public MoveAction(List<Integer> queenCurrent, List<Integer> queenTarget, List<Integer> arrowTarget) {
        this.queenCurrent = queenCurrent;
        this.queenTarget = queenTarget;
        this.arrowTarget = arrowTarget;
    }

    @Override
    public String toString() {
        return String.format("Move Queen from %s to %s, Shoot Arrow at %s", queenCurrent, queenTarget, arrowTarget);
    }
}

package ubc.cosc322;

import java.util.List;

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

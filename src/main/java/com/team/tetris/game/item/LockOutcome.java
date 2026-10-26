package com.team.tetris.game.item;

import java.util.Objects;
import java.util.Set;

/**
 * {@link FallingPiece#lock}의 결과. 드라이버는 이 값만 보고 줄 삭제·아이템 기회 집계를 진행하거나 건너뛴다.
 *
 * @param clearsLines 완성 행 삭제와 줄 수 집계를 진행하는지
 * @param forcedRows 꽉 차 있지 않아도 함께 지울 행 (줄 삭제 아이템). 삭제된 줄은 기존 방식대로 점수에 든다
 */
public record LockOutcome(boolean clearsLines, Set<Integer> forcedRows) {
    public LockOutcome {
        forcedRows = Set.copyOf(Objects.requireNonNull(forcedRows, "forcedRows"));
        if (!clearsLines && !forcedRows.isEmpty()) {
            throw new IllegalArgumentException("Forced rows need line clearing");
        }
    }

    /** 보통의 줄 삭제를 진행한다. */
    public static LockOutcome clearLines() {
        return new LockOutcome(true, Set.of());
    }

    /** 보통의 줄 삭제에 더해 주어진 행을 강제로 지운다. */
    public static LockOutcome clearLines(Set<Integer> forcedRows) {
        return new LockOutcome(true, forcedRows);
    }

    /** 조각이 사라지기만 하고 줄 삭제·집계를 하지 않는다. 바닥에 닿은 무게추가 이에 해당한다. */
    public static LockOutcome consumed() {
        return new LockOutcome(false, Set.of());
    }
}

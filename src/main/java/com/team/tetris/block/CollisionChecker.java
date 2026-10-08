package com.team.tetris.block;

import java.util.List;
import java.util.Objects;

/** 경계·점유 여부를 계산하는 순수 판정기. 보드나 후보를 변경하지 않는다. */
public final class CollisionChecker {
    private CollisionChecker() {
    }

    /**
     * 네 점유 셀이 모두 보드 안에 있고 비어 있는지 확인한다.
     * 기준점이 음수여도 실제 점유 셀이 모두 안쪽이면 유효하다. 숨김 행은 지원하지 않는다.
     *
     * @param board 고정 셀을 조회할 보드
     * @param candidate 검사할 블록 후보
     * @return 배치 가능하면 true, 벽·바닥·위쪽 경계 또는 고정 셀과 충돌하면 false
     * @throws NullPointerException 보드 또는 후보가 null인 경우
     */
    public static boolean canPlace(BoardView board, Tetromino candidate) {
        Objects.requireNonNull(board, "board");
        Objects.requireNonNull(candidate, "candidate");
        return canPlaceCells(board, candidate.cells());
    }

    /**
     * 이미 계산한 셀 목록을 재사용하는 내부 경로.
     * Board는 검사한 목록과 실제 기록하는 목록을 동일하게 유지한다.
     */
    static boolean canPlaceCells(BoardView board, List<Cell> cells) {
        for (Cell cell : cells) {
            // 조회 전에 경계를 확인해 정상적인 이동 거절이 조회 예외로 바뀌지 않게 한다.
            if (cell.row() < 0 || cell.row() >= board.rows()
                    || cell.col() < 0 || cell.col() >= board.cols()) {
                return false;
            }
            if (board.cellAt(cell.row(), cell.col()).isPresent()) {
                return false;
            }
        }
        return true;
    }
}

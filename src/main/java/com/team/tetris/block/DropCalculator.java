package com.team.tetris.block;

import java.util.Objects;

/** 보드와 블록을 변경하지 않고 하드드롭 거리를 계산한다. */
public final class DropCalculator {
    private DropCalculator() {
    }

    /**
     * 유효한 시작 블록에서 연속으로 내려갈 수 있는 최대 행 수를 계산한다.
     *
     * 중간 위치를 모두 검사하므로 적층 블록 아래의 빈 공간으로 순간 이동하지 않는다.
     * 엔진은 반환 거리만큼 이동한 후보를 고정하고 실제 낙하 거리의 점수를 반영한다.
     * 시간 복잡도는 네 셀 기준 O(rows)이다.
     *
     * @param board 고정 셀을 조회할 보드
     * @param piece 현재 배치 가능한 블록
     * @return 최대 낙하 거리. 이미 착지했으면 0
     * @throws NullPointerException 보드 또는 블록이 null인 경우
     * @throws InvalidPlacementException 시작 위치 자체가 경계·점유 충돌인 경우
     */
    public static int dropDistance(BoardView board, Tetromino piece) {
        Objects.requireNonNull(board, "board");
        Objects.requireNonNull(piece, "piece");
        if (!CollisionChecker.canPlace(board, piece)) {
            throw new InvalidPlacementException(piece, board.rows(), board.cols());
        }
        int distance = 0;
        Tetromino current = piece;
        while (true) {
            Tetromino next = current.moveBy(1, 0);
            if (!CollisionChecker.canPlace(board, next)) {
                return distance;
            }
            current = next;
            distance++;
        }
    }
}

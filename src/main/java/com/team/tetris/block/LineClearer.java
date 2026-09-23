package com.team.tetris.block;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** 완성 행을 찾는 내부 계산기. 배열 변경은 Board에만 맡긴다. */
final class LineClearer {
    private LineClearer() {
    }

    /**
     * 위에서 아래 순서로 완성 행을 찾는다. 반환 목록은 중복 없는 오름차순이며 불변이다.
     * 이 순서 계약 덕분에 Board는 추가 정렬 없이 아래쪽부터 행을 압축할 수 있다.
     */
    static List<Integer> findFullRows(BoardView board) {
        Objects.requireNonNull(board, "board");
        List<Integer> fullRows = new ArrayList<>();
        for (int row = 0; row < board.rows(); row++) {
            boolean full = true;
            for (int col = 0; col < board.cols(); col++) {
                if (board.cellAt(row, col).isEmpty()) {
                    full = false;
                    break;
                }
            }
            if (full) {
                fullRows.add(row);
            }
        }
        return List.copyOf(fullRows);
    }
}

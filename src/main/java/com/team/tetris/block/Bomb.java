package com.team.tetris.block;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 3×3 폭탄 아이템의 폭발 범위 계산기. 셀 삭제는 {@link Board#eraseCells(List)}에 맡긴다.
 *
 * 폭탄 칸을 중심으로 한 3×3 중 보드 안의 좌표를 고른다. 고정 순간 이미 완성된 행은 범위에서 빼서,
 * 그 행이 이어지는 줄 삭제에서 그대로 지워지고 기존 규칙대로 점수에 반영되게 한다.
 */
public final class Bomb {
    /** 폭발 범위의 한 변 길이. 중심에서 상하좌우로 한 칸씩 퍼진다. */
    public static final int SIZE = 3;
    private static final int RADIUS = SIZE / 2;

    private Bomb() {
    }

    /**
     * @param board 폭탄 블록이 이미 기록된 보드
     * @param center 폭탄 문자가 붙어 있던 칸
     * @return 지울 좌표의 불변 목록. 위에서 아래, 왼쪽에서 오른쪽 순서이며 빈 칸도 포함한다
     * @throws IndexOutOfBoundsException 중심이 보드 밖인 경우
     */
    public static List<Cell> blastCells(BoardView board, Cell center) {
        Objects.requireNonNull(board, "board");
        Objects.requireNonNull(center, "center");
        Objects.checkIndex(center.row(), board.rows());
        Objects.checkIndex(center.col(), board.cols());

        List<Integer> fullRows = LineClearer.findFullRows(board);
        List<Cell> cells = new ArrayList<>();
        for (int row = center.row() - RADIUS; row <= center.row() + RADIUS; row++) {
            if (row < 0 || row >= board.rows() || fullRows.contains(row)) {
                continue;
            }
            for (int col = center.col() - RADIUS; col <= center.col() + RADIUS; col++) {
                if (col >= 0 && col < board.cols()) {
                    cells.add(new Cell(row, col));
                }
            }
        }
        return List.copyOf(cells);
    }
}

/*
 * 3×3 폭탄 아이템(B)의 정의. 포함형(EmbeddedItem)으로, 블록이 고정되면 B 칸을 중심으로 한 3×3을 지운다.
 * 보드 밖은 잘라 내고, 고정 순간 이미 꽉 찬 행은 이어지는 줄 삭제에 넘긴다. 명세: docs/items/bomb.md
 */
package com.team.tetris.game.item;

import com.team.tetris.block.Board;
import com.team.tetris.block.BoardView;
import com.team.tetris.block.Cell;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * 3×3 폭탄 아이템 {@code B}. 블록이 고정되면 {@code B} 칸을 중심으로 한 3×3 칸을 지운다.
 * 지운 칸 위의 블록은 내려오지 않으며 지운 칸은 점수·줄 수에 들지 않는다.
 * 고정 순간 이미 꽉 찬 행은 폭발에서 빼서, 이어지는 줄 삭제가 기존 규칙대로 지우고 점수를 준다.
 * 명세: docs/items/bomb.md (2차 요구사항 4.5)
 */
public final class BombItem implements EmbeddedItem {
    public static final char SYMBOL = 'B';
    /** 중심에서 상하좌우로 퍼지는 칸 수. 한 변은 {@code 2 × RADIUS + 1 = 3}칸이다. */
    private static final int RADIUS = 1;

    @Override
    public String name() {
        return "BOMB";
    }

    @Override
    public char symbol() {
        return SYMBOL;
    }

    /** 폭발은 보드에 직접 적용하고, 강제로 지울 행은 없다. */
    @Override
    public Set<Integer> onLock(Board board, Cell center) {
        board.eraseCells(blastCells(board, center));
        return Set.of();
    }

    /** 보드 안의 3×3 좌표 중 꽉 찬 행을 뺀 것. 빈 칸도 포함하며, 지우기는 {@link Board#eraseCells}가 한 번에 한다. */
    private static List<Cell> blastCells(BoardView board, Cell center) {
        List<Cell> cells = new ArrayList<>();
        for (int row = Math.max(0, center.row() - RADIUS);
             row <= Math.min(board.rows() - 1, center.row() + RADIUS); row++) {
            if (isFull(board, row)) continue;
            for (int col = Math.max(0, center.col() - RADIUS);
                 col <= Math.min(board.cols() - 1, center.col() + RADIUS); col++) {
                cells.add(new Cell(row, col));
            }
        }
        return cells;
    }

    private static boolean isFull(BoardView board, int row) {
        for (int col = 0; col < board.cols(); col++) {
            if (board.cellAt(row, col).isEmpty()) return false;
        }
        return true;
    }
}

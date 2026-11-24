/*
 * 화면에 넘기는 보드 칸 좌표. block.Cell과 같은 값이지만, common이 block을 import하지 않도록 따로 둔다.
 */
package com.team.tetris.common.events;

/**
 * 보드의 칸 하나. 행 0이 맨 위, 열 0이 맨 왼쪽이다 ({@link BoardSnapshot}의 배열 순서와 같다).
 *
 * @param row 행 (0~19)
 * @param col 열 (0~9)
 */
public record CellPosition(int row, int col) {
    public CellPosition {
        if (row < 0 || col < 0) throw new IllegalArgumentException("Negative cell position: " + row + "," + col);
    }
}

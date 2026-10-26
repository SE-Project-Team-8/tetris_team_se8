/*
 * 무게추의 불변 좌표와 6칸 형태(.WW. / WWWW). 낙하 규칙은 AnvilPiece, 아이템 정의는 AnvilItem에 있다.
 */
package com.team.tetris.game.item;

import com.team.tetris.block.Cell;

import java.util.List;

/** 고정 방향의 너비 4칸 무게추 형태: .WW. / WWWW. 낙하 규칙은 {@link AnvilPiece}가 가진다. */
public record Anvil(int row, int col) {
    public static final int WIDTH = 4;
    public static final int HEIGHT = 2;
    private static final List<Cell> OFFSETS = List.of(
            new Cell(0, 1), new Cell(0, 2),
            new Cell(1, 0), new Cell(1, 1), new Cell(1, 2), new Cell(1, 3));

    public Anvil {
        Math.addExact(row, HEIGHT - 1);
        Math.addExact(col, WIDTH - 1);
    }

    public List<Cell> cells() {
        return OFFSETS.stream().map(cell -> new Cell(row + cell.row(), col + cell.col())).toList();
    }

    public Anvil moveBy(int deltaRow, int deltaCol) {
        return new Anvil(Math.addExact(row, deltaRow), Math.addExact(col, deltaCol));
    }
}

package com.team.tetris.game.item;

import com.team.tetris.block.Board;
import com.team.tetris.block.Cell;
import com.team.tetris.block.TetrominoType;

import java.util.List;

/**
 * 무게추 아이템 {@code W}. 너비 4칸 블록으로 떨어지며 아래의 블록을 모두 지운다.
 * 낙하 규칙은 {@link AnvilPiece}에 있다. 명세: docs/items/anvil.md (2차 요구사항 4.4)
 */
public final class AnvilItem implements StandaloneItem {
    public static final char SYMBOL = 'W';

    @Override
    public String name() {
        return "ANVIL";
    }

    @Override
    public char symbol() {
        return SYMBOL;
    }

    @Override
    public TetrominoType paletteType() {
        return TetrominoType.J;
    }

    @Override
    public List<Cell> previewShape() {
        return new Anvil(0, 0).cells();
    }

    @Override
    public FallingPiece spawn(Board board) {
        return AnvilPiece.spawn(board, this);
    }
}

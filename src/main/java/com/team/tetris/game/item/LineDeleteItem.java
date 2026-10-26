package com.team.tetris.game.item;

import com.team.tetris.block.Board;
import com.team.tetris.block.Cell;

import java.util.Set;

/**
 * 줄 삭제 아이템 {@code L}. 블록이 고정되면 {@code L}이 있는 행을 꽉 차 있지 않아도 지운다.
 * 지운 줄은 기존 방식대로 점수에 든다. 명세: docs/items/line-delete.md (2차 요구사항 4.3)
 */
public final class LineDeleteItem implements EmbeddedItem {
    public static final char SYMBOL = 'L';

    @Override
    public String name() {
        return "LINE_DELETE";
    }

    @Override
    public char symbol() {
        return SYMBOL;
    }

    @Override
    public Set<Integer> onLock(Board board, Cell itemCell) {
        return Set.of(itemCell.row());
    }
}

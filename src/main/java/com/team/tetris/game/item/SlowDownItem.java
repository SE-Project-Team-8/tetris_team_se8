/*
 * 속도 감소 아이템(S)의 정의. 포함형(EmbeddedItem)으로, 블록이 고정되면 속도 단계(레벨)를 영구히 1 낮춘다.
 * 보드는 바꾸지 않고 onActivated에서 GameEffects.lowerLevel만 요청한다. 명세: docs/items/slow-down.md
 */
package com.team.tetris.game.item;

import com.team.tetris.block.Board;
import com.team.tetris.block.Cell;

import java.util.Set;

/**
 * 속도 감소 아이템 {@code S}. 블록이 고정되면 현재 속도 단계를 {@value #STEPS}단계 낮추고, 낮춘 단계는
 * 게임이 끝날 때까지 유지된다. 레벨 1에서 얻으면 효과가 없고 나중을 위해 쌓아 두지도 않는다(엔진 규칙).
 * 명세: docs/items/slow-down.md (2차 요구사항 4.5)
 */
public final class SlowDownItem implements EmbeddedItem {
    public static final char SYMBOL = 'S';
    public static final int STEPS = 1;

    @Override
    public String name() {
        return "SLOW_DOWN";
    }

    @Override
    public char symbol() {
        return SYMBOL;
    }

    /** 속도 감소는 보드를 바꾸지 않는다. */
    @Override
    public Set<Integer> onLock(Board board, Cell itemCell) {
        return Set.of();
    }

    @Override
    public void onActivated(GameEffects effects) {
        effects.lowerLevel(STEPS);
    }
}

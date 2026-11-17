/*
 * 피버 타임 아이템(F)의 정의. 포함형(EmbeddedItem)으로, 블록이 고정되면 10초 동안 모든 점수를 2배로 만든다.
 * 보드는 바꾸지 않고 onActivated에서 GameEffects.scoreMultiplier만 요청한다. 명세: docs/items/fever.md
 */
package com.team.tetris.game.item;

import com.team.tetris.block.Board;
import com.team.tetris.block.Cell;

import java.util.Set;

/**
 * 피버 타임 아이템 {@code F}. 블록이 고정되면 그때부터 {@value #SECONDS}초 동안 얻는 모든 점수가
 * {@value #MULTIPLIER}배가 된다. 다시 얻으면 남은 시간을 더하지 않고 {@value #SECONDS}초로 갱신한다.
 * {@code F} 블록 자신의 점수는 고정 전에 계산되므로 배율이 붙지 않는다.
 * 명세: docs/items/fever.md (2차 요구사항 4.5)
 */
public final class FeverItem implements EmbeddedItem {
    public static final char SYMBOL = 'F';
    public static final int SECONDS = 10;
    public static final int MULTIPLIER = 2;

    @Override
    public String name() {
        return "FEVER";
    }

    @Override
    public char symbol() {
        return SYMBOL;
    }

    /** 피버는 보드를 바꾸지 않는다. */
    @Override
    public Set<Integer> onLock(Board board, Cell itemCell) {
        return Set.of();
    }

    @Override
    public void onActivated(GameEffects effects) {
        effects.scoreMultiplier(MULTIPLIER, SECONDS);
    }
}

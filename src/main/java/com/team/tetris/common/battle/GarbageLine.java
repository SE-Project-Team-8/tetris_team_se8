/*
 * 대전 모드의 공격 줄 한 줄. 로컬 대전은 메모리로, P2P 대전은 직렬화해 그대로 넘긴다.
 */
package com.team.tetris.common.battle;

import com.team.tetris.common.constants.GameConstants;

import java.io.Serializable;
import java.util.Objects;

/**
 * 상대에게 넘어가는 줄 한 줄의 모양. 비트 c가 1이면 c열(화면 왼쪽이 0)이 차 있다.
 *
 * 색은 담지 않는다. 받은 쪽에서 항상 회색으로 그린다. 칸이 하나도 없는 줄은 넘길 것이 없고, 꽉 찬 줄은
 * 보드에 들어가자마자 지워질 줄이므로 둘 다 만들 수 없다.
 *
 * @param cells 열별 채움 여부를 담은 비트 묶음
 */
public record GarbageLine(int cells) implements Serializable {
    public static final int COLS = GameConstants.BOARD_COLS;
    private static final int FULL = (1 << COLS) - 1;

    public GarbageLine {
        if (!isSendable(cells)) {
            throw new IllegalArgumentException("A garbage line needs at least one cell and one hole: " + cells);
        }
    }

    /** 공격 줄로 만들 수 있는 모양인지 확인한다. 빈 줄과 꽉 찬 줄, 보드 폭을 넘는 값은 false다. */
    public static boolean isSendable(int cells) {
        return cells > 0 && cells < FULL;
    }

    /** 열 순서대로 채움 여부를 받아 만든다. */
    public static GarbageLine of(boolean... filled) {
        Objects.requireNonNull(filled, "filled");
        if (filled.length != COLS) {
            throw new IllegalArgumentException("A garbage line has " + COLS + " columns: " + filled.length);
        }
        int cells = 0;
        for (int col = 0; col < COLS; col++) {
            if (filled[col]) cells |= 1 << col;
        }
        return new GarbageLine(cells);
    }

    public boolean filled(int col) {
        Objects.checkIndex(col, COLS);
        return (cells >> col & 1) != 0;
    }
}

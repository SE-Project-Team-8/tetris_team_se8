package com.team.tetris.game;

import com.team.tetris.common.events.CellCodes;

/** NONE is a regular block; the other values consume an earned item opportunity. */
public enum ItemType {
    NONE(CellCodes.NO_SYMBOL),
    LINE_DELETE('L'),
    ANVIL('W');

    private final char symbol;

    ItemType(char symbol) {
        this.symbol = symbol;
    }

    /** Letter drawn on the item's cells and encoded into cell codes; no two items share one. */
    public char symbol() {
        return symbol;
    }
}

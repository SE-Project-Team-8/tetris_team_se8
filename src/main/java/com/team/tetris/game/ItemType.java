package com.team.tetris.game;

import com.team.tetris.common.events.LineDeleteIds;

import java.util.Arrays;
import java.util.List;

/**
 * NONE is a regular block; the other values consume an earned item opportunity.
 * IDs are stable transfer codes for snapshots and previews, never ordinals.
 */
public enum ItemType {
    NONE(0, ' ', false),
    LINE_DELETE(LineDeleteIds.ITEM_ID, 'L', true),
    ANVIL(2, 'W', false);

    private static final List<ItemType> EARNABLE = Arrays.stream(values())
            .filter(item -> item != NONE).toList();

    private final int id;
    private final char letter;
    private final boolean attachedToBlock;

    ItemType(int id, char letter, boolean attachedToBlock) {
        this.id = id;
        this.letter = letter;
        this.attachedToBlock = attachedToBlock;
    }

    public int id() { return id; }

    /** Every item uses its own letter on the board and in the preview. */
    public char letter() { return letter; }

    /** True when the item marks one cell of a regular tetromino instead of being its own block. */
    public boolean isAttachedToBlock() { return attachedToBlock; }

    /** Items an earned ten-line opportunity can turn into, in declaration order. */
    public static List<ItemType> earnable() { return EARNABLE; }

    public static ItemType fromId(int id) {
        for (ItemType item : values()) {
            if (item.id == id) return item;
        }
        throw new IllegalArgumentException("Unknown item ID: " + id);
    }
}

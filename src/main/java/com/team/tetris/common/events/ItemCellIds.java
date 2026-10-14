package com.team.tetris.common.events;

/**
 * IDs for a tetromino cell that carries an attached item (L, F, ...).
 * Regular tetromino IDs remain 0..6 and the anvil keeps {@link AnvilIds#ANVIL_ID}.
 * The item part is the game's stable item ID, so adding an item needs no new ID range.
 */
public final class ItemCellIds {
    private static final int BOARD_BASE = 100;    // 100 + item * 10 + type
    private static final int PREVIEW_BASE = 1000; // 1000 + item * 100 + type * 4 + cell index
    private static final int MAX_ITEM_ID = 89;

    private ItemCellIds() { }

    public static int boardCell(int itemId, int typeId) {
        requireItem(itemId);
        requireType(typeId);
        return BOARD_BASE + itemId * 10 + typeId;
    }

    public static boolean isBoardCell(int id) { return id >= BOARD_BASE && id < PREVIEW_BASE; }
    public static int boardItem(int id) { return (id - BOARD_BASE) / 10; }
    public static int boardType(int id) { return (id - BOARD_BASE) % 10; }

    public static int preview(int itemId, int typeId, int cellIndex) {
        requireItem(itemId);
        requireType(typeId);
        if (cellIndex < 0 || cellIndex > 3) throw new IllegalArgumentException("cellIndex must be 0..3");
        return PREVIEW_BASE + itemId * 100 + typeId * 4 + cellIndex;
    }

    public static boolean isPreview(int id) { return id >= PREVIEW_BASE; }
    public static int previewItem(int id) { return (id - PREVIEW_BASE) / 100; }
    public static int previewType(int id) { return (id - PREVIEW_BASE) % 100 / 4; }
    public static int previewCellIndex(int id) { return (id - PREVIEW_BASE) % 4; }

    private static void requireItem(int itemId) {
        if (itemId < 1 || itemId > MAX_ITEM_ID) throw new IllegalArgumentException("Invalid item ID: " + itemId);
    }

    private static void requireType(int typeId) {
        if (typeId < 0 || typeId > 6) throw new IllegalArgumentException("Invalid block type ID: " + typeId);
    }
}

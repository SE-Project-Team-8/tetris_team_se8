package com.team.tetris.common.events;

/** IDs for the L item. Ordinary tetromino IDs remain 0..6. */
public final class LineDeleteIds {
    private static final int FIRST_ITEM_ID = 7;

    private LineDeleteIds() { }

    public static int boardCell(int typeId) { return FIRST_ITEM_ID + typeId; }
    public static boolean isItemCell(int id) { return id >= 7 && id <= 13; }
    public static int boardType(int id) { return id - FIRST_ITEM_ID; }
    public static int preview(int typeId, int cellIndex) { return FIRST_ITEM_ID + typeId * 4 + cellIndex; }
    public static boolean isItemPreview(int id) { return id >= FIRST_ITEM_ID && id <= 34; }
    public static int previewType(int id) { return (id - FIRST_ITEM_ID) / 4; }
    public static int previewCellIndex(int id) { return (id - FIRST_ITEM_ID) % 4; }
}

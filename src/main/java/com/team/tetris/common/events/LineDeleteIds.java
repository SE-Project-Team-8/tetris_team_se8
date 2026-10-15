package com.team.tetris.common.events;

/**
 * L-item shorthand for the shared {@link ItemCellIds} rules; ordinary tetromino IDs remain 0..6.
 * Kept for screens that still decode only the L item; new attached items use ItemCellIds directly.
 */
public final class LineDeleteIds {
    /** The game's ItemType.LINE_DELETE uses this item ID. */
    public static final int ITEM_ID = 1;

    private LineDeleteIds() { }

    public static int boardCell(int typeId) { return ItemCellIds.boardCell(ITEM_ID, typeId); }
    public static boolean isItemCell(int id) {
        return ItemCellIds.isBoardCell(id) && ItemCellIds.boardItem(id) == ITEM_ID;
    }
    public static int boardType(int id) { return ItemCellIds.boardType(id); }
    public static int preview(int typeId, int cellIndex) { return ItemCellIds.preview(ITEM_ID, typeId, cellIndex); }
    public static boolean isItemPreview(int id) {
        return ItemCellIds.isPreview(id) && ItemCellIds.previewItem(id) == ITEM_ID;
    }
    public static int previewType(int id) { return ItemCellIds.previewType(id); }
    public static int previewCellIndex(int id) { return ItemCellIds.previewCellIndex(id); }
}

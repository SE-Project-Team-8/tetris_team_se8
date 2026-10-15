package com.team.tetris.game;

import com.team.tetris.block.TetrominoType;
import com.team.tetris.common.events.AnvilIds;
import com.team.tetris.common.events.ItemCellIds;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class ItemTypeTest {
    @Test
    void everyItemUsesItsOwnIdAndLetter() {
        Set<Integer> ids = new HashSet<>();
        Set<Character> letters = new HashSet<>();
        for (ItemType item : ItemType.values()) {
            assertTrue(ids.add(item.id()), "Duplicate ID for " + item);
            assertEquals(item, ItemType.fromId(item.id()));
            if (item != ItemType.NONE) assertTrue(letters.add(item.letter()), "Duplicate letter for " + item);
        }
        assertThrows(IllegalArgumentException.class, () -> ItemType.fromId(-1));
    }

    @Test
    void everyItemExceptTheRegularBlockCanBeEarned() {
        assertEquals(Arrays.stream(ItemType.values()).filter(item -> item != ItemType.NONE).toList(),
                ItemType.earnable());
    }

    @ParameterizedTest
    @EnumSource(value = ItemType.class, names = {"NONE", "ANVIL"}, mode = EnumSource.Mode.EXCLUDE)
    void attachedItemCellsRoundTripWithoutCollidingWithOtherIds(ItemType item) {
        assertTrue(item.isAttachedToBlock());
        for (TetrominoType type : TetrominoType.values()) {
            int board = ItemCellIds.boardCell(item.id(), type.id());
            assertTrue(ItemCellIds.isBoardCell(board));
            assertEquals(item.id(), ItemCellIds.boardItem(board));
            assertEquals(type.id(), ItemCellIds.boardType(board));
            assertNotEquals(AnvilIds.ANVIL_ID, board);
            assertTrue(board > 6, "Regular block IDs stay 0..6");
            for (int cell = 0; cell < 4; cell++) {
                int preview = ItemCellIds.preview(item.id(), type.id(), cell);
                assertTrue(ItemCellIds.isPreview(preview));
                assertFalse(ItemCellIds.isBoardCell(preview));
                assertEquals(item.id(), ItemCellIds.previewItem(preview));
                assertEquals(type.id(), ItemCellIds.previewType(preview));
                assertEquals(cell, ItemCellIds.previewCellIndex(preview));
            }
        }
        assertFalse(ItemCellIds.isBoardCell(AnvilIds.ANVIL_ID));
        assertFalse(ItemCellIds.isPreview(AnvilIds.ANVIL_ID));
    }
}

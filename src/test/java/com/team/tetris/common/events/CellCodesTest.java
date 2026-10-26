package com.team.tetris.common.events;

import com.team.tetris.block.TetrominoType;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class CellCodesTest {
    @Test
    void plainBlocksKeepTheirAgreedIdsAndEmptyStaysOutside() {
        for (TetrominoType type : TetrominoType.values()) {
            int code = CellCodes.cell(type.id(), CellCodes.NO_SYMBOL);
            assertEquals(type.id(), code, "Screens that only know 0..6 must keep working");
            assertTrue(CellCodes.isValid(code));
            assertFalse(CellCodes.hasItem(code));
            assertEquals(CellCodes.NO_INDEX, CellCodes.itemCellIndex(code));
            assertEquals(code, CellCodes.preview(type.id(), CellCodes.NO_SYMBOL, CellCodes.NO_INDEX));
        }
        assertFalse(CellCodes.isValid(BoardSnapshot.EMPTY));
        assertThrows(IllegalArgumentException.class, () -> CellCodes.symbol(BoardSnapshot.EMPTY));
    }

    @Test
    void itemCodesRoundTripAndNeverCollide() {
        Set<Integer> seen = new HashSet<>();
        char[] symbols = {'L', 'W', 'B', '★', 'ㄱ'};
        for (TetrominoType type : TetrominoType.values()) {
            assertTrue(seen.add(type.id()));
            for (char symbol : symbols) {
                for (int index = CellCodes.NO_INDEX; index <= 3; index++) {
                    int code = CellCodes.preview(type.id(), symbol, index);
                    assertTrue(seen.add(code), "duplicate code " + code);
                    assertTrue(CellCodes.isValid(code));
                    assertEquals(type.id(), CellCodes.baseType(code));
                    assertEquals(symbol, CellCodes.symbol(code));
                    assertTrue(CellCodes.hasItem(code));
                    assertEquals(index, CellCodes.itemCellIndex(code));
                }
                assertEquals(CellCodes.preview(type.id(), symbol, CellCodes.NO_INDEX),
                        CellCodes.cell(type.id(), symbol), "A board cell is a preview entry without an index");
            }
        }
    }

    @Test
    void legacyIdsAndGarbageAreNotValidCodes() {
        for (int legacy = 7; legacy <= 35; legacy++) {
            assertFalse(CellCodes.isValid(legacy), "old L/anvil ID " + legacy);
        }
        assertFalse(CellCodes.isValid(-2));
        assertFalse(CellCodes.isValid(Integer.MAX_VALUE));
        assertFalse(CellCodes.isValid(CellCodes.cell(0, 'L') + 8 * 65536 * 5), "cell index above 3");
        assertFalse(CellCodes.isValid(8 * 65536), "a cell index without a symbol");
        assertThrows(IllegalArgumentException.class, () -> CellCodes.baseType(7));
    }

    @Test
    void encodingRejectsImpossibleInputs() {
        assertThrows(IllegalArgumentException.class, () -> CellCodes.cell(7, CellCodes.NO_SYMBOL));
        assertThrows(IllegalArgumentException.class, () -> CellCodes.cell(-1, 'L'));
        assertThrows(IllegalArgumentException.class, () -> CellCodes.cell(0, ' '));
        assertThrows(IllegalArgumentException.class, () -> CellCodes.cell(0, '\n'));
        assertThrows(IllegalArgumentException.class, () -> CellCodes.preview(0, 'L', 4));
        assertThrows(IllegalArgumentException.class, () -> CellCodes.preview(0, 'L', -2));
        assertThrows(IllegalArgumentException.class, () -> CellCodes.preview(0, CellCodes.NO_SYMBOL, 0));
        assertFalse(CellCodes.isSymbol(' '));
        assertFalse(CellCodes.isSymbol('\t'));
        assertTrue(CellCodes.isSymbol('L'));
    }
}

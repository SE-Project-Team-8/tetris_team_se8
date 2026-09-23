package com.team.tetris.block;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class LineClearerTest {
    @Test
    void detectsOnlyFullRowsInAscendingOrderWithoutMutation() {
        BoardView board = BoardFixtures.readOnly("IOJS", "I..I", "ZZZZ", ".OO.", "TTTT");
        var before = BoardFixtures.picture(board);
        var rows = LineClearer.findFullRows(board);
        assertEquals(List.of(0, 2, 4), rows);
        assertEquals(before, BoardFixtures.picture(board));
        assertThrows(UnsupportedOperationException.class, () -> rows.add(1));
    }

    @Test
    void noFullRowsAndTinyBoardsAreHandled() {
        assertTrue(LineClearer.findFullRows(BoardFixtures.readOnly("..", "O.")).isEmpty());
        assertEquals(List.of(0), LineClearer.findFullRows(BoardFixtures.readOnly("I")));
        assertTrue(LineClearer.findFullRows(BoardFixtures.readOnly(".")).isEmpty());
        assertThrows(NullPointerException.class, () -> LineClearer.findFullRows(null));
    }
}

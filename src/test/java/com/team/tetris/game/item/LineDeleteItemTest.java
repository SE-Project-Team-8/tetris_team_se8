package com.team.tetris.game.item;

import com.team.tetris.block.Board;
import com.team.tetris.block.Cell;
import com.team.tetris.block.Rotation;
import com.team.tetris.block.Tetromino;
import com.team.tetris.block.TetrominoType;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class LineDeleteItemTest {
    @Test
    void forcesOnlyTheRowOfItsCellAndLeavesTheBoardToTheDriver() {
        Board board = new Board();
        assertTrue(board.tryPlace(new Tetromino(TetrominoType.O, Rotation.SPAWN, 18, 4)));
        LineDeleteItem item = new LineDeleteItem();
        assertEquals(Set.of(19), item.onLock(board, new Cell(19, 5)));
        assertEquals(TetrominoType.O, board.cellAt(19, 5).orElseThrow(), "The item itself erases nothing");
        assertEquals('L', item.symbol());
        assertEquals("LINE_DELETE", item.name());
    }
}

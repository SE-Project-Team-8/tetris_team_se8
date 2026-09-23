package com.team.tetris.block;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.junit.jupiter.api.Assertions.*;

class DropCalculatorTest {
    @ParameterizedTest
    @EnumSource(TetrominoType.class)
    void everyOrientationLandsExactlyOnFloorWithoutChangingInputs(TetrominoType type) {
        for (Rotation rotation : Rotation.values()) {
            var board = new Board(10, 8);
            var piece = new Tetromino(type, rotation, 0, 2);
            var before = BoardFixtures.picture(board);
            int bottom = piece.cells().stream().mapToInt(Cell::row).max().orElseThrow();
            int distance = DropCalculator.dropDistance(board, piece);
            assertEquals(9 - bottom, distance);
            var landing = piece.moveBy(distance, 0);
            assertTrue(CollisionChecker.canPlace(board, landing));
            assertFalse(CollisionChecker.canPlace(board, landing.moveBy(1, 0)));
            assertEquals(0, DropCalculator.dropDistance(board, landing));
            assertEquals(before, BoardFixtures.picture(board));
            assertEquals(0, piece.row());
        }
    }

    @Test
    void stopsAtFirstObstacleEvenWhenThereIsEmptySpaceBelowIt() {
        BoardView board = BoardFixtures.readOnly(
                "......", "......", "...T..", "......",
                ".J....", "......", "......", "......");
        var before = BoardFixtures.picture(board);
        assertEquals(2, DropCalculator.dropDistance(board,
                new Tetromino(TetrominoType.O, Rotation.SPAWN, 0, 0)));
        assertEquals(1, DropCalculator.dropDistance(board,
                new Tetromino(TetrominoType.I, Rotation.SPAWN, -1, 0)));
        assertEquals(0, DropCalculator.dropDistance(board,
                new Tetromino(TetrominoType.O, Rotation.SPAWN, 2, 0)));
        assertEquals(before, BoardFixtures.picture(board));
    }

    @Test
    void rejectsInvalidStartingPlacementWithContextAndPreservesBoard() {
        var board = new Board(4, 4);
        BoardFixtures.place(board, TetrominoType.O, Rotation.SPAWN, 0, 0);
        var before = BoardFixtures.picture(board);
        var overlap = new Tetromino(TetrominoType.O, Rotation.SPAWN, 0, 0);
        var error = assertThrows(InvalidPlacementException.class,
                () -> DropCalculator.dropDistance(board, overlap));
        assertEquals(overlap, error.piece());
        assertEquals(4, error.rows());
        assertEquals(4, error.cols());
        assertThrows(InvalidPlacementException.class, () -> DropCalculator.dropDistance(board,
                new Tetromino(TetrominoType.O, Rotation.SPAWN, -1, 2)));
        assertThrows(NullPointerException.class, () -> DropCalculator.dropDistance(null, overlap));
        assertThrows(NullPointerException.class, () -> DropCalculator.dropDistance(board, null));
        assertEquals(before, BoardFixtures.picture(board));
    }
}

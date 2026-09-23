package com.team.tetris.block;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;

import static org.junit.jupiter.api.Assertions.*;

class CollisionCheckerTest {
    @ParameterizedTest
    @CsvSource({"-1,0", "0,-1", "3,0", "0,3"})
    void boundaryCollisionIsFalseAndNeverLeaksQueryExceptions(int row, int col) {
        BoardView board = BoardFixtures.readOnly("....", "....", "....", "....");
        var piece = new Tetromino(TetrominoType.O, Rotation.SPAWN, row, col);
        assertFalse(CollisionChecker.canPlace(board, piece));
    }

    @ParameterizedTest
    @EnumSource(TetrominoType.class)
    void allRotationsFitAndCollideWithTheirOwnLockedCells(TetrominoType type) {
        for (Rotation rotation : Rotation.values()) {
            var board = new Board(6, 6);
            var piece = new Tetromino(type, rotation, 1, 1);
            assertTrue(CollisionChecker.canPlace(board, piece));
            assertTrue(board.tryPlace(piece));
            var before = BoardFixtures.picture(board);
            assertFalse(CollisionChecker.canPlace(board, piece));
            assertEquals(before, BoardFixtures.picture(board));
        }
    }

    @Test
    void usesActualCellsInsteadOfTheBoundingSquare() {
        var board = new Board(4, 4);
        // I의 격자 기준 행은 -1이지만 실제 점유 행은 0이므로 유효하다.
        assertTrue(CollisionChecker.canPlace(board,
                new Tetromino(TetrominoType.I, Rotation.SPAWN, -1, 0)));
        assertFalse(CollisionChecker.canPlace(board,
                new Tetromino(TetrominoType.I, Rotation.SPAWN, -2, 0)));
        assertTrue(CollisionChecker.canPlace(board,
                new Tetromino(TetrominoType.O, Rotation.SPAWN, 2, 2)));
    }

    @Test
    void readonlyImplementationWorksAndNullIsNotARegularCollision() {
        BoardView view = BoardFixtures.readOnly("....", "....", "..T.", "....");
        var piece = new Tetromino(TetrominoType.O, Rotation.SPAWN, 1, 1);
        assertFalse(CollisionChecker.canPlace(view, piece));
        assertTrue(CollisionChecker.canPlace(view, piece.moveBy(-1, 0)));
        assertThrows(NullPointerException.class, () -> CollisionChecker.canPlace(null, piece));
        assertThrows(NullPointerException.class, () -> CollisionChecker.canPlace(view, null));
    }
}

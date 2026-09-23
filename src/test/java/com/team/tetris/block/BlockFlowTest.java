package com.team.tetris.block;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** 엔진 없이 실제 도메인 객체의 연결을 확인한다. 점수·이벤트·입력 연동은 엔진 테스트 범위다. */
class BlockFlowTest {
    @Test
    void spawnMoveDropLockAndClearComposeWithoutWritingTheActivePieceEarly() {
        var board = new Board(6, 4);
        // 이미 고정된 왼쪽 O 옆으로 다음 O를 내려 두 줄을 완성한다.
        BoardFixtures.place(board, TetrominoType.O, Rotation.SPAWN, 4, 0);
        TetrominoGenerator generator = () -> TetrominoType.O;
        var current = new Tetromino(generator.next(), Rotation.SPAWN, 0, 0);
        var before = BoardFixtures.picture(board);
        var moved = current.moveBy(0, 2).rotateClockwise();
        assertTrue(CollisionChecker.canPlace(board, moved));
        int distance = DropCalculator.dropDistance(board, moved);
        assertEquals(4, distance);
        assertEquals(before, BoardFixtures.picture(board));

        var landed = moved.moveBy(distance, 0);
        assertTrue(board.tryPlace(landed));
        assertEquals(List.of("....", "....", "....", "....", "OOOO", "OOOO"),
                BoardFixtures.picture(board));
        assertEquals(2, board.clearFullLines());
        assertEquals(List.of("....", "....", "....", "....", "....", "...."),
                BoardFixtures.picture(board));
        assertTrue(CollisionChecker.canPlace(board, current));
    }

    @Test
    void blockedSpawnAndRejectedRotationAreNormalResults() {
        var board = new Board(4, 4);
        BoardFixtures.place(board, TetrominoType.O, Rotation.SPAWN, 0, 0);
        var spawn = new Tetromino(TetrominoType.O, Rotation.SPAWN, 0, 0);
        assertFalse(CollisionChecker.canPlace(board, spawn));
        var before = BoardFixtures.picture(board);
        assertFalse(board.tryPlace(spawn));
        assertEquals(before, BoardFixtures.picture(board));

        // I의 세로 상태는 왼쪽 벽에 닿지만 기준점은 -2열이다. 회전 후보는 벽을 넘는다.
        var empty = new Board(4, 4);
        var current = new Tetromino(TetrominoType.I, Rotation.RIGHT, 0, -2);
        assertTrue(CollisionChecker.canPlace(empty, current));
        assertFalse(CollisionChecker.canPlace(empty, current.rotateClockwise()));
        assertEquals(Rotation.RIGHT, current.rotation());
        assertEquals(-2, current.col());
    }
}

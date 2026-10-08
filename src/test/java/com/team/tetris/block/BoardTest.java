package com.team.tetris.block;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class BoardTest {
    @Test
    void defaultBoardHasRequiredDimensionsAndOnlyEmptyCells() {
        var board = new Board();
        assertEquals(20, board.rows());
        assertEquals(10, board.cols());
        assertEquals(Collections.nCopies(20, ".........."), BoardFixtures.picture(board));
        assertEquals(List.of("."), BoardFixtures.picture(new Board(1, 1)));
    }

    @ParameterizedTest
    @CsvSource({"0,4", "4,0", "-1,4", "4,-1"})
    void rejectsNonpositiveDimensions(int rows, int cols) {
        assertThrows(IllegalArgumentException.class, () -> new Board(rows, cols));
    }

    @ParameterizedTest
    @CsvSource({"-1,0", "0,-1", "4,0", "0,4"})
    void directQueriesRejectOutsideCoordinates(int row, int col) {
        var board = new Board(4, 4);
        assertThrows(IndexOutOfBoundsException.class, () -> board.cellAt(row, col));
        assertTrue(board.cellAt(3, 3).isEmpty());
    }

    @Test
    void placementIsAtomicAndKeepsTheOriginalTypes() {
        var board = new Board(4, 4);
        BoardFixtures.place(board, TetrominoType.O, Rotation.SPAWN, 2, 2);
        var before = BoardFixtures.picture(board);
        assertEquals(List.of("....", "....", "..OO", "..OO"), before);

        // 처음 두 셀은 비어 있고 세 번째에서 겹친다. 앞의 셀도 기록되면 안 된다.
        assertFalse(board.tryPlace(new Tetromino(TetrominoType.T, Rotation.SPAWN, 1, 1)));
        assertEquals(before, BoardFixtures.picture(board));
        assertFalse(board.tryPlace(new Tetromino(TetrominoType.O, Rotation.SPAWN, -1, 0)));
        assertEquals(before, BoardFixtures.picture(board));
        assertThrows(NullPointerException.class, () -> board.tryPlace(null));
        assertEquals(before, BoardFixtures.picture(board));
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 2, 3, 4, 6})
    void clearsAllConsecutiveFullRowsIncludingMoreThanFour(int count) {
        var board = new Board(6, 4);
        for (int row = 6 - count; row < 6; row++) {
            BoardFixtures.fillRow(board, row);
        }
        // 배치는 자동으로 줄을 삭제하지 않아 엔진이 삭제 수를 별도로 받을 수 있다.
        assertEquals(count * 4, BoardFixtures.picture(board).stream()
                .mapToLong(line -> line.chars().filter(value -> value != '.').count()).sum());
        assertEquals(count, board.clearFullLines());
        assertEquals(Collections.nCopies(6, "...."), BoardFixtures.picture(board));
        assertEquals(0, board.clearFullLines());
    }

    @Test
    void compactsNonadjacentRowsWithoutChangingSurvivorOrderOrSharingRows() {
        var board = new Board(8, 4);
        BoardFixtures.place(board, TetrominoType.O, Rotation.SPAWN, 0, 0);
        BoardFixtures.place(board, TetrominoType.T, Rotation.SPAWN, 2, 0);
        BoardFixtures.fillRow(board, 5);
        BoardFixtures.fillRow(board, 7);

        assertEquals(2, board.clearFullLines());
        assertEquals(List.of("....", "....", "OO..", "OO..", ".T..", "TTT.", "....", "...."),
                BoardFixtures.picture(board));
        // 압축 후 위의 두 빈 행이 같은 배열을 참조하면 이 배치가 1행도 오염시킨다.
        BoardFixtures.fillRow(board, 0);
        assertEquals("IIII", BoardFixtures.picture(board).get(0));
        assertEquals("....", BoardFixtures.picture(board).get(1));
        assertEquals("OO..", BoardFixtures.picture(board).get(2));
    }

    @Test
    void deletingTopRowLeavesLowerRowsInPlace() {
        var board = new Board(5, 4);
        BoardFixtures.fillRow(board, 0);
        BoardFixtures.place(board, TetrominoType.O, Rotation.SPAWN, 2, 0);
        assertEquals(1, board.clearFullLines());
        assertEquals(List.of("....", "....", "OO..", "OO..", "...."), BoardFixtures.picture(board));
    }

    @Test
    void clearingAndIndependentBoardsDoNotLeakState() {
        var board = new Board(4, 4);
        var other = new Board(4, 4);
        BoardFixtures.place(board, TetrominoType.O, Rotation.SPAWN, 0, 0);
        assertEquals(Collections.nCopies(4, "...."), BoardFixtures.picture(other));
        board.clear();
        board.clear();
        assertEquals(4, board.rows());
        assertEquals(4, board.cols());
        assertEquals(Collections.nCopies(4, "...."), BoardFixtures.picture(board));
        assertTrue(board.tryPlace(new Tetromino(TetrominoType.O, Rotation.SPAWN, 0, 0)));
    }
}

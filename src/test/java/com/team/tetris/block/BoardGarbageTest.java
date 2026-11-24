/*
 * Board의 회색 줄 테스트: 아래 삽입과 위로 밀기, 넘침 판정, 줄 삭제·셀 삭제·초기화가 회색 표시를 함께 처리하는지,
 * 회색 칸과의 충돌.
 */
package com.team.tetris.block;

import com.team.tetris.common.battle.GarbageLine;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class BoardGarbageTest {
    /** 0~8열이 차고 9열이 빈 줄. */
    private static final GarbageLine HOLE_AT_9 = new GarbageLine(0b0111111111);
    /** 0열만 빈 줄. */
    private static final GarbageLine HOLE_AT_0 = new GarbageLine(0b1111111110);
    /** 8·9열이 빈 줄. */
    private static final GarbageLine HOLES_AT_8_9 = new GarbageLine(0b0011111111);

    @Test
    void linesEnterAtTheBottomInOrderAndAreGrey() {
        Board board = new Board();
        assertFalse(board.insertRowsAtBottom(List.of(HOLE_AT_9, HOLE_AT_0)));
        assertEquals(List.of(
                "..........",
                "GGGGGGGGG.",
                ".GGGGGGGGG"), bottom(board, 3));
        assertTrue(board.cellAt(19, 5).isPresent(), "grey cells are occupied cells");
        assertTrue(board.isGarbage(18, 0));
        assertFalse(board.isGarbage(18, 9), "the hole stays empty");
    }

    @Test
    void existingBlocksMoveUpAndKeepTheirKind() {
        Board board = new Board();
        BoardFixtures.place(board, TetrominoType.O, Rotation.SPAWN, 18, 0);
        assertFalse(board.insertRowsAtBottom(List.of(HOLE_AT_9, HOLE_AT_0)));
        assertEquals(List.of(
                "OO........",
                "OO........",
                "GGGGGGGGG.",
                ".GGGGGGGGG"), bottom(board, 4));
        assertEquals(TetrominoType.O, board.cellAt(16, 0).orElseThrow());
        assertFalse(board.isGarbage(16, 0));
    }

    @Test
    void reportsWhetherBlocksWerePushedAboveTheBoard() {
        Board nearTop = new Board();
        BoardFixtures.place(nearTop, TetrominoType.O, Rotation.SPAWN, 1, 0);
        assertFalse(nearTop.insertRowsAtBottom(List.of(HOLE_AT_9)), "row 0 was empty, nothing is lost");
        assertEquals(TetrominoType.O, nearTop.cellAt(0, 0).orElseThrow());

        Board atTop = new Board();
        BoardFixtures.place(atTop, TetrominoType.O, Rotation.SPAWN, 0, 0);
        assertTrue(atTop.insertRowsAtBottom(List.of(HOLE_AT_9)), "the top row of the O left the board");
        assertEquals(TetrominoType.O, atTop.cellAt(0, 0).orElseThrow(), "the rest still moved up");
    }

    @Test
    void aFilledGreyLineClearsLikeAnyLineAndGreyMarksFollowTheRows() {
        Board board = new Board();
        board.insertRowsAtBottom(List.of(HOLE_AT_0, HOLES_AT_8_9, HOLES_AT_8_9));
        BoardFixtures.place(board, TetrominoType.O, Rotation.SPAWN, 18, 8);
        assertEquals(List.of(18, 19), board.clearFullLinesAndGetRows());
        assertEquals(List.of(
                "..........",
                ".GGGGGGGGG"), bottom(board, 2), "the line above dropped and is still grey");
        assertFalse(board.isGarbage(17, 1), "no grey mark is left where the row used to be");
    }

    @Test
    void forcedRowClearingMovesGreyMarksToo() {
        Board board = new Board();
        board.insertRowsAtBottom(List.of(HOLE_AT_0, HOLE_AT_9));
        assertEquals(List.of(19), board.clearLinesIncludingAndGetRows(19), "a line-delete item row");
        assertEquals(List.of(
                "..........",
                ".GGGGGGGGG"), bottom(board, 2));
    }

    @Test
    void erasingAndClearingRemoveGreyMarks() {
        Board board = new Board();
        board.insertRowsAtBottom(List.of(HOLE_AT_9));
        assertEquals(1, board.eraseCells(List.of(new Cell(19, 0))));
        assertFalse(board.isGarbage(19, 0));
        assertTrue(board.cellAt(19, 0).isEmpty());
        assertTrue(board.isGarbage(19, 1));

        board.clear();
        for (int col = 0; col < board.cols(); col++) {
            assertFalse(board.isGarbage(19, col));
        }
    }

    @Test
    void fallingBlocksCollideWithGreyCells() {
        Board board = new Board();
        board.insertRowsAtBottom(List.of(HOLE_AT_9));
        assertFalse(CollisionChecker.canPlace(board, new Tetromino(TetrominoType.O, Rotation.SPAWN, 18, 0)));
        assertTrue(CollisionChecker.canPlace(board, new Tetromino(TetrominoType.O, Rotation.SPAWN, 17, 0)));
    }

    @Test
    void anEmptyListChangesNothing() {
        Board board = new Board();
        BoardFixtures.place(board, TetrominoType.O, Rotation.SPAWN, 18, 0);
        List<String> before = bottom(board, 20);
        assertFalse(board.insertRowsAtBottom(List.of()));
        assertEquals(before, bottom(board, 20));
    }

    @Test
    void invalidRequestsLeaveTheBoardUnchanged() {
        Board board = new Board();
        BoardFixtures.place(board, TetrominoType.O, Rotation.SPAWN, 18, 0);
        List<String> before = bottom(board, 20);
        List<GarbageLine> tooMany = new ArrayList<>(Collections.nCopies(21, HOLE_AT_9));
        assertThrows(IllegalArgumentException.class, () -> board.insertRowsAtBottom(tooMany));
        assertThrows(NullPointerException.class, () -> board.insertRowsAtBottom(null));
        assertEquals(before, bottom(board, 20));

        Board narrow = new Board(4, 4);
        assertThrows(IllegalStateException.class, () -> narrow.insertRowsAtBottom(List.of(HOLE_AT_9)));
        assertFalse(narrow.insertRowsAtBottom(List.of()), "nothing to insert needs no width check");
    }

    @Test
    void greyQueriesCheckTheCoordinates() {
        Board board = new Board();
        assertThrows(IndexOutOfBoundsException.class, () -> board.isGarbage(20, 0));
        assertThrows(IndexOutOfBoundsException.class, () -> board.isGarbage(0, -1));
    }

    /** 아래쪽 n줄을 문자로. 점은 빈 칸, G는 회색 칸, 그 밖은 블록 종류. */
    private static List<String> bottom(Board board, int count) {
        List<String> rows = new ArrayList<>();
        for (int row = board.rows() - count; row < board.rows(); row++) {
            StringBuilder line = new StringBuilder();
            for (int col = 0; col < board.cols(); col++) {
                int r = row, c = col;
                line.append(board.isGarbage(r, c) ? "G"
                        : board.cellAt(r, c).map(Enum::name).orElse("."));
            }
            rows.add(line.toString());
        }
        return List.copyOf(rows);
    }
}

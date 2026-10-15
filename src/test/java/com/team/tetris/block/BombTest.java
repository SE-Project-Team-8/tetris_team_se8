package com.team.tetris.block;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class BombTest {
    @Test
    void blastCoversTheThreeByThreeAroundTheBombInReadingOrder() {
        BoardView board = BoardFixtures.readOnly(".....", ".....", ".....", ".....", ".....");
        assertEquals(List.of(
                new Cell(1, 1), new Cell(1, 2), new Cell(1, 3),
                new Cell(2, 1), new Cell(2, 2), new Cell(2, 3),
                new Cell(3, 1), new Cell(3, 2), new Cell(3, 3)), Bomb.blastCells(board, new Cell(2, 2)));
    }

    @Test
    void blastIsClippedByWallsFloorAndCeiling() {
        BoardView board = BoardFixtures.readOnly("...", "...", "...", "...");
        assertEquals(List.of(new Cell(2, 0), new Cell(2, 1), new Cell(3, 0), new Cell(3, 1)),
                Bomb.blastCells(board, new Cell(3, 0)));
        assertEquals(List.of(new Cell(0, 1), new Cell(0, 2), new Cell(1, 1), new Cell(1, 2)),
                Bomb.blastCells(board, new Cell(0, 2)));
    }

    @Test
    void rowsCompletedAtLandingAreLeftForTheLineClear() {
        // 가운데 행이 꽉 찼으므로 폭발은 위·아래 행만 지우고, 가운데 행은 줄 삭제가 처리한다.
        BoardView board = BoardFixtures.readOnly("..O..", "IIIII", "..T..");
        assertEquals(List.of(
                new Cell(0, 1), new Cell(0, 2), new Cell(0, 3),
                new Cell(2, 1), new Cell(2, 2), new Cell(2, 3)), Bomb.blastCells(board, new Cell(1, 2)));
    }

    @Test
    void erasingTheBlastLeavesBlocksAboveFloatingWithoutShiftingRows() {
        Board board = new Board(4, 4);
        BoardFixtures.place(board, TetrominoType.O, Rotation.SPAWN, 0, 2);
        // 폭탄이 붙은 블록: (2, 1) 칸에 폭탄이 있다.
        BoardFixtures.place(board, TetrominoType.O, Rotation.SPAWN, 2, 0);
        assertEquals(List.of("..OO", "..OO", "OO..", "OO.."), BoardFixtures.picture(board));

        assertEquals(5, board.eraseCells(Bomb.blastCells(board, new Cell(2, 1))),
                "Only occupied cells inside the blast are counted");
        assertEquals(List.of("..OO", "...O", "....", "...."), BoardFixtures.picture(board),
                "The block above the blast stays where it was");
    }

    @Test
    void centerOutsideTheBoardIsRejected() {
        BoardView board = BoardFixtures.readOnly("...", "...");
        assertThrows(IndexOutOfBoundsException.class, () -> Bomb.blastCells(board, new Cell(2, 0)));
        assertThrows(IndexOutOfBoundsException.class, () -> Bomb.blastCells(board, new Cell(0, -1)));
        assertThrows(NullPointerException.class, () -> Bomb.blastCells(board, null));
        assertThrows(NullPointerException.class, () -> Bomb.blastCells(null, new Cell(0, 0)));
    }
}

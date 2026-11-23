/*
 * 대전 공격 줄 모양 테스트 (BlockBoardDriver.lockAndClearLines): 2줄 이상 삭제 시에만 생김, 마지막 블록 칸을 뺀 모양,
 * 위에서 아래 순서, 회색 줄과 줄 삭제 아이템이 지운 줄도 셈(D-05, D-09), 넘길 칸이 없는 줄은 뺌(D-06).
 * 회색 칸의 스냅샷 코드와 드라이버의 줄 삽입도 함께 본다.
 */
package com.team.tetris.game;

import com.team.tetris.block.Board;
import com.team.tetris.block.Rotation;
import com.team.tetris.block.Tetromino;
import com.team.tetris.block.TetrominoGenerator;
import com.team.tetris.block.TetrominoType;
import com.team.tetris.common.battle.GarbageLine;
import com.team.tetris.common.events.BoardSnapshot;
import com.team.tetris.common.events.CellCodes;
import com.team.tetris.game.item.Item;
import com.team.tetris.game.item.ItemPolicy;
import com.team.tetris.game.item.LineDeleteItem;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

class AttackLinesTest {
    /** 4·5열만 빈 줄. 가운데에 떨어지는 O가 채운다. */
    private static final GarbageLine HOLES_AT_4_5 = new GarbageLine(0b1111001111);

    @Test
    void aSingleLineIsNotAnAttack() {
        Board board = new Board();
        BlockBoardDriver driver = driver(board, TetrominoType.O);
        placeI(board, 18, 0);
        placeI(board, 18, 6);
        GameEngine.LockResult result = dropCurrent(driver);
        assertEquals(1, result.clearedLines());
        assertEquals(List.of(), result.attackLines());
    }

    @Test
    void twoLinesAreSentWithoutTheCellsOfThePieceThatClearedThem() {
        Board board = new Board();
        BlockBoardDriver driver = driver(board, TetrominoType.O);
        for (int row : new int[]{17, 18}) {
            placeI(board, row, 0);
            placeI(board, row, 6);
        }
        GameEngine.LockResult result = dropCurrent(driver);
        assertEquals(2, result.clearedLines());
        assertEquals(List.of(HOLES_AT_4_5, HOLES_AT_4_5), result.attackLines(),
                "colours do not matter, the O's own cells become holes");
    }

    @Test
    void linesKeepTheirTopToBottomOrderAndTheShapeOfTheLastPiece() {
        // docs/battle/rules.md 2.2: an L standing up clears three lines and leaves its own shape as holes.
        Board board = new Board();
        BlockBoardDriver driver = driver(board, TetrominoType.L);
        board.insertRowsAtBottom(List.of(
                new GarbageLine(0b1111001111),   // row 17: the L's top covers columns 4 and 5
                new GarbageLine(0b1111011111),   // row 18: the L's stem in column 5
                new GarbageLine(0b1111011111))); // row 19
        assertTrue(driver.spawnNextBlock());
        assertTrue(driver.rotateClockwise());
        driver.hardDrop();
        assertEquals(List.of("17,4", "17,5", "18,5", "19,5"), occupied(driver.fallingSnapshot()),
                "the L landed where the holes are");

        GameEngine.LockResult result = driver.lockAndClearLines();
        assertEquals(List.of(17, 18, 19), result.clearedRows());
        assertEquals(List.of(
                new GarbageLine(0b1111001111),
                new GarbageLine(0b1111011111),
                new GarbageLine(0b1111011111)), result.attackLines(),
                "grey lines that are cleared are sent on too (D-09)");
    }

    @Test
    void aRowClearedByTheLineDeleteItemCountsTowardTheAttack() {
        Board board = new Board();
        BlockBoardDriver driver = itemDriver(board);
        board.insertRowsAtBottom(List.of(HOLES_AT_4_5));
        placeI(board, 17, 0);                       // row 18: columns 0-3 only, far from full
        GameEngine.LockResult result = dropCurrent(driver);
        assertEquals(List.of(18, 19), result.clearedRows(), "the L on the O's top row deletes row 18 (D-05)");
        assertEquals(List.of(new GarbageLine(0b0000001111), HOLES_AT_4_5), result.attackLines());
    }

    @Test
    void aClearedRowWithNothingLeftToSendIsDropped() {
        Board board = new Board();
        BlockBoardDriver driver = itemDriver(board);
        board.insertRowsAtBottom(List.of(HOLES_AT_4_5));
        GameEngine.LockResult result = dropCurrent(driver);
        assertEquals(2, result.clearedLines(), "two lines cleared, so this is an attack");
        assertEquals(List.of(HOLES_AT_4_5), result.attackLines(),
                "row 18 held only the O's own cells, so there is nothing to send from it (D-06)");
    }

    @Test
    void greyCellsUseTheGarbageCodeInSnapshots() {
        Board board = new Board();
        BlockBoardDriver driver = driver(board, TetrominoType.O);
        assertFalse(driver.insertGarbage(List.of(HOLES_AT_4_5)));
        int[] bottom = driver.snapshot().cells()[19];
        assertEquals(CellCodes.GARBAGE, bottom[0]);
        assertTrue(CellCodes.isGarbage(bottom[9]));
        assertEquals(BoardSnapshot.EMPTY, bottom[4]);
        assertFalse(CellCodes.isValid(CellCodes.GARBAGE), "screens check isGarbage before decoding a block");
        assertFalse(CellCodes.isGarbage(TetrominoType.I.id()));
    }

    @Test
    void linesCanOnlyBeInsertedWhileNoPieceIsFalling() {
        Board board = new Board();
        BlockBoardDriver driver = driver(board, TetrominoType.O);
        assertTrue(driver.spawnNextBlock());
        assertThrows(IllegalStateException.class, () -> driver.insertGarbage(List.of(HOLES_AT_4_5)));
        driver.hardDrop();
        driver.lockAndClearLines();
        assertFalse(driver.insertGarbage(List.of(HOLES_AT_4_5)));
    }

    @Test
    void lockResultsWithoutAttackLinesKeepTheirOldMeaning() {
        assertEquals(List.of(), new GameEngine.LockResult(0, false).attackLines());
        assertThrows(NullPointerException.class,
                () -> new GameEngine.LockResult(0, false, List.of(), null, null));
    }

    /** 보드를 비우고 미리보기를 채운 드라이버. reset()이 보드를 비우므로 보드는 이 뒤에 채운다. */
    private static BlockBoardDriver driver(Board board, TetrominoType type) {
        BlockBoardDriver driver = new BlockBoardDriver(board, repeat(type));
        driver.reset();
        return driver;
    }

    /** 첫 블록부터 줄 삭제 아이템이 왼쪽 위 칸에 붙은 O를 내는 드라이버. */
    private static BlockBoardDriver itemDriver(Board board) {
        ItemPolicy always = new ItemPolicy() {
            @Override public void onLinesCleared(int lines) { }
            @Override public Optional<Item> nextItem() { return Optional.of(new LineDeleteItem()); }
            @Override public void reset() { }
        };
        Random firstCell = new Random() {
            @Override public int nextInt(int bound) { return 0; }
        };
        BlockBoardDriver driver = new BlockBoardDriver(board, repeat(TetrominoType.O), firstCell, always);
        driver.setItemMode(true);
        driver.reset();
        return driver;
    }

    /** 가운데(4·5열)에 생기는 첫 블록을 바로 떨어뜨려 고정한다. */
    private static GameEngine.LockResult dropCurrent(BlockBoardDriver driver) {
        assertTrue(driver.spawnNextBlock());
        driver.hardDrop();
        return driver.lockAndClearLines();
    }

    /** row+1 행의 col..col+3 열에 가로 I를 놓는다. */
    private static void placeI(Board board, int row, int col) {
        assertTrue(board.tryPlace(new Tetromino(TetrominoType.I, Rotation.SPAWN, row, col)));
    }

    /** 스냅샷에서 차 있는 칸을 "행,열"로, 위에서 아래·왼쪽에서 오른쪽 순서로. */
    private static List<String> occupied(BoardSnapshot snapshot) {
        List<String> cells = new ArrayList<>();
        for (int row = 0; row < snapshot.rows(); row++) {
            for (int col = 0; col < snapshot.cols(); col++) {
                if (snapshot.cells()[row][col] != BoardSnapshot.EMPTY) cells.add(row + "," + col);
            }
        }
        return cells;
    }

    private static TetrominoGenerator repeat(TetrominoType type) {
        return () -> type;
    }
}

/*
 * BombItem(B) 테스트. onLock의 3×3 범위·보드 경계 잘림·꽉 찬 행 제외·행 압축 없음과,
 * 실제 BlockBoardDriver·GameEngine 경로의 표시, 폭발, 폭탄 블록이 완성한 줄 삭제, 지운 칸 점수 없음을 검증한다.
 */
package com.team.tetris.game.item;

import com.team.tetris.block.Board;
import com.team.tetris.block.Cell;
import com.team.tetris.block.Rotation;
import com.team.tetris.block.Tetromino;
import com.team.tetris.block.TetrominoType;
import com.team.tetris.common.Difficulty;
import com.team.tetris.common.events.BoardSnapshot;
import com.team.tetris.common.events.CellCodes;
import com.team.tetris.common.events.GameEventListener;
import com.team.tetris.game.BlockBoardDriver;
import com.team.tetris.game.DefaultSpeedPolicy;
import com.team.tetris.game.GameAction;
import com.team.tetris.game.GameEngine;
import com.team.tetris.game.ScoreCalculator;
import org.junit.jupiter.api.Test;

import javax.swing.SwingUtilities;
import java.util.Random;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class BombItemTest {
    /** O block cell order is (0,0), (0,1), (1,0), (1,1); index 3 is the bottom-right cell. */
    private static final int BOTTOM_RIGHT = 3;
    private static final int TOP_RIGHT = 1;
    private static final BombItem BOMB = new BombItem();

    @Test
    void namesItselfWithItsOwnSymbol() {
        assertEquals('B', BOMB.symbol());
        assertEquals("BOMB", BOMB.name());
    }

    @Test
    void erasesTheThreeByThreeAroundTheBombAndForcesNoRows() {
        Board board = new Board();
        for (int row : new int[]{14, 16}) {
            for (int col : new int[]{2, 4, 6}) placeSquare(board, row, col); // rows 14-17, cols 2-7
        }
        assertEquals(Set.of(), BOMB.onLock(board, new Cell(15, 4)));
        for (int row = 14; row <= 17; row++) {
            for (int col = 2; col <= 7; col++) {
                boolean blasted = row <= 16 && col >= 3 && col <= 5;
                assertEquals(!blasted, board.cellAt(row, col).isPresent(), row + "," + col);
            }
        }
    }

    @Test
    void blastIsClippedByWallsFloorAndCeiling() {
        Board board = new Board();
        placeSquare(board, 16, 0);
        placeSquare(board, 18, 0);
        placeSquare(board, 0, 8);
        BOMB.onLock(board, new Cell(19, 0));
        BOMB.onLock(board, new Cell(0, 9));
        for (int row = 18; row <= 19; row++) {
            for (int col = 0; col <= 1; col++) assertTrue(board.cellAt(row, col).isEmpty(), row + "," + col);
        }
        assertTrue(board.cellAt(17, 0).isPresent(), "The row above the clipped blast stays");
        for (int row = 0; row <= 1; row++) {
            for (int col = 8; col <= 9; col++) assertTrue(board.cellAt(row, col).isEmpty(), row + "," + col);
        }
    }

    @Test
    void rowsAlreadyFullAtLockAreLeftForTheLineClearAndBlocksAboveStayFloating() {
        Board board = new Board();
        assertTrue(board.tryPlace(new Tetromino(TetrominoType.I, Rotation.SPAWN, 18, 0))); // row 19, cols 0-3
        assertTrue(board.tryPlace(new Tetromino(TetrominoType.I, Rotation.SPAWN, 18, 4))); // row 19, cols 4-7
        placeSquare(board, 18, 8);
        placeSquare(board, 16, 4);
        placeSquare(board, 14, 4);

        BOMB.onLock(board, new Cell(18, 5));
        for (int col = 0; col < 10; col++) assertTrue(board.cellAt(19, col).isPresent(), "full row, col " + col);
        assertTrue(board.cellAt(17, 4).isEmpty());
        assertTrue(board.cellAt(17, 5).isEmpty());
        assertTrue(board.cellAt(18, 8).isPresent(), "Columns outside the blast are untouched");
        assertTrue(board.cellAt(16, 4).isPresent(), "The block above the blast stays where it was");
        assertTrue(board.cellAt(15, 5).isPresent());
    }

    @Test
    void bombCellIsShownOnTheFallingBlockAndInThePreview() {
        Board board = new Board();
        BlockBoardDriver driver = bombDriver(board, BOTTOM_RIGHT);
        earnBomb(driver, board);
        int preview = driver.nextBlockTypes()[0];
        assertEquals(BombItem.SYMBOL, CellCodes.symbol(preview));
        assertEquals(BOTTOM_RIGHT, CellCodes.itemCellIndex(preview));
        landTheRegularPreview(driver);

        assertTrue(driver.spawnNextBlock());
        assertEquals(CellCodes.cell(TetrominoType.O.id(), BombItem.SYMBOL), driver.snapshot().cells()[1][5]);
    }

    @Test
    void bombErasesTheThreeByThreeAroundItWithoutDroppingTheRest() {
        Board board = new Board();
        BlockBoardDriver driver = bombDriver(board, BOTTOM_RIGHT);
        earnBomb(driver, board);
        landTheRegularPreview(driver); // A regular O now sits at rows 18-19, cols 4-5.
        placeSquare(board, 16, 2);
        placeSquare(board, 16, 6);
        placeSquare(board, 14, 6);

        assertTrue(driver.spawnNextBlock());
        assertEquals(16, driver.hardDrop(), "The bomb block lands on rows 16-17, B at (17, 5)");
        assertEquals(0, driver.lockAndClearLines().clearedLines(), "Erased cells are not cleared lines");

        // Blast: rows 16-18, cols 4-6.
        for (int row = 16; row <= 18; row++) {
            for (int col = 4; col <= 6; col++) assertTrue(board.cellAt(row, col).isEmpty(), row + "," + col);
        }
        assertTrue(board.cellAt(19, 4).isPresent(), "The row below the blast is untouched");
        assertTrue(board.cellAt(16, 3).isPresent(), "Columns outside the blast are untouched");
        assertTrue(board.cellAt(17, 7).isPresent());
        assertTrue(board.cellAt(15, 6).isPresent(), "Blocks above the blast stay floating");
        assertTrue(board.cellAt(14, 7).isPresent());
    }

    @Test
    void aRowCompletedByTheBombBlockIsStillClearedAndCounted() {
        Board board = new Board();
        BlockBoardDriver driver = bombDriver(board, TOP_RIGHT);
        earnBomb(driver, board);
        landTheRegularPreview(driver);
        // Row 17 is full except cols 4-5, where the bomb block will land.
        assertTrue(board.tryPlace(new Tetromino(TetrominoType.I, Rotation.SPAWN, 16, 0)));
        assertTrue(board.tryPlace(new Tetromino(TetrominoType.I, Rotation.SPAWN, 16, 6)));
        placeSquare(board, 15, 6); // Rows 15-16, cols 6-7: half inside the blast.

        assertTrue(driver.spawnNextBlock());
        driver.hardDrop(); // Lands on rows 16-17 with B at (16, 5); blast rows 15-17, cols 4-6.
        assertEquals(1, driver.lockAndClearLines().clearedLines());

        // Row 17 was removed, so everything above it moved down one row.
        for (int col = 0; col < 10; col++) {
            boolean expected = col == 7;
            assertEquals(expected, board.cellAt(17, col).isPresent(), "row 17, col " + col);
            assertEquals(expected, board.cellAt(16, col).isPresent(), "row 16, col " + col);
        }
        assertTrue(board.cellAt(18, 4).isPresent(), "The regular O below the cleared row stays");
        assertTrue(board.cellAt(19, 5).isPresent());
    }

    @Test
    void erasedCellsAwardNoPoints() throws Exception {
        Board board = new Board();
        BlockBoardDriver driver = bombDriver(board, BOTTOM_RIGHT);
        driver.setItemMode(true);
        ScoreCalculator scoring = new ScoreCalculator();
        GameEngine engine = new GameEngine(driver, new Events(), scoring, new DefaultSpeedPolicy(),
                new IdleTimer(), new IdleTimer(), () -> Difficulty.NORMAL);
        SwingUtilities.invokeAndWait(() -> {
            try {
                assertTrue(engine.start());
                for (int round = 0; round < 5; round++) {
                    fillTwoRowsExceptTheCenter(board);
                    assertTrue(engine.handle(GameAction.HARD_DROP));
                }
                assertTrue(engine.handle(GameAction.HARD_DROP)); // The pre-existing regular preview falls first.
                placeSquare(board, 16, 6);

                int before = engine.getScore();
                int level = engine.getLevel();
                assertTrue(engine.handle(GameAction.HARD_DROP));
                assertTrue(board.cellAt(16, 6).isEmpty(), "The bomb went off");
                assertEquals(scoring.dropPoints(16, level), engine.getScore() - before,
                        "Only the bomb block's own drop is scored");
                assertEquals(10, engine.getClearedLines(), "Erased cells are not counted as lines");
            } finally {
                engine.close();
            }
        });
    }

    /** Clears ten lines; the block already queued before the milestone is now falling. */
    private static void earnBomb(BlockBoardDriver driver, Board board) {
        driver.setItemMode(true);
        driver.reset();
        for (int round = 0; round < 5; round++) {
            assertTrue(driver.spawnNextBlock());
            fillTwoRowsExceptTheCenter(board);
            driver.hardDrop();
            assertEquals(2, driver.lockAndClearLines().clearedLines());
        }
        assertTrue(driver.spawnNextBlock());
    }

    private static void landTheRegularPreview(BlockBoardDriver driver) {
        driver.hardDrop();
        assertEquals(0, driver.lockAndClearLines().clearedLines());
    }

    private static BlockBoardDriver bombDriver(Board board, int bombCell) {
        Random fixedCell = new Random() {
            @Override public int nextInt(int bound) { return bombCell; }
        };
        return new BlockBoardDriver(board, () -> TetrominoType.O, fixedCell, () -> BOMB);
    }

    private static void fillTwoRowsExceptTheCenter(Board board) {
        for (int col : new int[]{0, 2, 6, 8}) placeSquare(board, 18, col);
    }

    private static void placeSquare(Board board, int row, int col) {
        assertTrue(board.tryPlace(new Tetromino(TetrominoType.O, Rotation.SPAWN, row, col)));
    }

    private static final class IdleTimer implements GameEngine.DropTimer {
        public void start(int intervalMillis, Runnable tick) { }
        public void stop() { }
    }

    private static final class Events implements GameEventListener {
        public void onBoardUpdated(BoardSnapshot snapshot) { }
        public void onScoreChanged(int score) { }
        public void onNextBlocksChanged(int[] types) { }
        public void onPauseStateChanged(boolean paused) { }
        public void onGameOver(int finalScore) { }
    }
}

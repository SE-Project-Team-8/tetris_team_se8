package com.team.tetris.game;

import com.team.tetris.block.Board;
import com.team.tetris.block.Rotation;
import com.team.tetris.block.Tetromino;
import com.team.tetris.block.TetrominoType;
import com.team.tetris.common.events.BoardSnapshot;
import com.team.tetris.common.events.GameEventListener;
import com.team.tetris.common.events.ItemCellIds;
import org.junit.jupiter.api.Test;

import javax.swing.SwingUtilities;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

class BombItemTest {
    /** O block cell order is (0,0), (0,1), (1,0), (1,1); index 3 is the bottom-right cell. */
    private static final int BOTTOM_RIGHT = 3;
    private static final int TOP_RIGHT = 1;

    @Test
    void bombCellIsShownAndReportedForTheBlockThatCarriedIt() {
        Board board = new Board();
        BlockBoardDriver driver = bombDriver(board, BOTTOM_RIGHT);
        earnBombAndLandTheRegularPreview(driver, board);
        assertEquals(ItemType.NONE, driver.lockedItem());

        assertTrue(driver.spawnNextBlock());
        assertEquals(ItemCellIds.boardCell(ItemType.BOMB.id(), TetrominoType.O.id()),
                driver.snapshot().cells()[1][5]);
        driver.hardDrop();
        driver.lockAndClearLines();
        assertEquals(ItemType.BOMB, driver.lockedItem());
    }

    @Test
    void bombErasesTheThreeByThreeAroundItWithoutDroppingTheRest() {
        Board board = new Board();
        BlockBoardDriver driver = bombDriver(board, BOTTOM_RIGHT);
        earnBombAndLandTheRegularPreview(driver, board); // A regular O now sits at rows 18-19, cols 4-5.
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
        earnBombAndLandTheRegularPreview(driver, board);
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
        GameEngine.DropTimer timer = new GameEngine.DropTimer() {
            public void start(int intervalMillis, Runnable tick) { }
            public void stop() { }
        };
        GameEngine engine = new GameEngine(driver, new Events(), scoring, timer);
        SwingUtilities.invokeAndWait(() -> {
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
            engine.close();
        });
    }

    /** Clears ten lines, then locks the regular block that was already queued before the bomb. */
    private static void earnBombAndLandTheRegularPreview(BlockBoardDriver driver, Board board) {
        driver.setItemMode(true);
        driver.reset();
        for (int round = 0; round < 5; round++) {
            assertTrue(driver.spawnNextBlock());
            fillTwoRowsExceptTheCenter(board);
            driver.hardDrop();
            assertEquals(2, driver.lockAndClearLines().clearedLines());
        }
        assertTrue(driver.spawnNextBlock());
        assertTrue(ItemCellIds.isPreview(driver.nextBlockTypes()[0]));
        assertEquals(ItemType.BOMB.id(), ItemCellIds.previewItem(driver.nextBlockTypes()[0]));
        driver.hardDrop();
        assertEquals(0, driver.lockAndClearLines().clearedLines());
    }

    private static BlockBoardDriver bombDriver(Board board, int bombCell) {
        Random fixedCell = new Random() {
            @Override public int nextInt(int bound) { return bombCell; }
        };
        return new BlockBoardDriver(board, () -> TetrominoType.O, fixedCell, () -> ItemType.BOMB);
    }

    private static void fillTwoRowsExceptTheCenter(Board board) {
        for (int col : new int[]{0, 2, 6, 8}) placeSquare(board, 18, col);
    }

    private static void placeSquare(Board board, int row, int col) {
        assertTrue(board.tryPlace(new Tetromino(TetrominoType.O, Rotation.SPAWN, row, col)));
    }

    private static final class Events implements GameEventListener {
        public void onBoardUpdated(BoardSnapshot snapshot) { }
        public void onScoreChanged(int score) { }
        public void onNextBlocksChanged(int[] types) { }
        public void onPauseStateChanged(boolean paused) { }
        public void onGameOver(int finalScore) { }
    }
}

package com.team.tetris.game;

import com.team.tetris.block.Board;
import com.team.tetris.block.Rotation;
import com.team.tetris.block.Tetromino;
import com.team.tetris.block.TetrominoGenerator;
import com.team.tetris.block.TetrominoType;
import com.team.tetris.common.events.BoardSnapshot;
import com.team.tetris.common.events.GameEventListener;
import com.team.tetris.common.events.LineDeleteIds;
import org.junit.jupiter.api.Test;

import javax.swing.SwingUtilities;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

class BlockBoardDriverTest {
    @Test
    void spawnCentersOccupiedCellsAndRefreshesThePreview() {
        BlockBoardDriver driver = new BlockBoardDriver(new Board(), sequence(TetrominoType.I, TetrominoType.T));
        driver.reset();
        assertArrayEquals(new int[]{TetrominoType.I.id()}, driver.nextBlockTypes());
        assertTrue(driver.spawnNextBlock());
        assertArrayEquals(new int[]{TetrominoType.T.id()}, driver.nextBlockTypes());
        BoardSnapshot snapshot = driver.snapshot();
        assertEquals(20, snapshot.rows());
        assertEquals(10, snapshot.cols());
        for (int col = 0; col < 10; col++) {
            assertEquals(col >= 3 && col <= 6 ? TetrominoType.I.id() : BoardSnapshot.EMPTY,
                    snapshot.cells()[0][col]);
        }
        assertThrows(IllegalStateException.class, driver::spawnNextBlock);
    }

    @Test
    void movementRotationDropAndLockUseRealCollisionRules() {
        BlockBoardDriver driver = new BlockBoardDriver(new Board(), sequence(TetrominoType.I));
        driver.reset();
        assertTrue(driver.spawnNextBlock());
        assertTrue(driver.moveLeft());
        assertTrue(driver.moveLeft());
        assertTrue(driver.moveLeft());
        assertFalse(driver.moveLeft());
        assertFalse(driver.rotateClockwise(), "I rotation would extend above the board");
        assertTrue(driver.moveDown());
        assertTrue(driver.rotateClockwise());
        assertTrue(driver.hardDrop() > 0);
        assertEquals(0, driver.hardDrop(), "Already landed blocks do not move further");
        assertEquals(new GameEngine.LockResult(0, false), driver.lockAndClearLines());
        assertThrows(IllegalStateException.class, driver::moveDown);
        assertTrue(driver.spawnNextBlock());
    }

    @Test
    void rotatesIBlockAgainstEitherSideWall() {
        BlockBoardDriver left = new BlockBoardDriver(new Board(), sequence(TetrominoType.I));
        left.reset();
        assertTrue(left.spawnNextBlock());
        assertTrue(left.moveDown());
        assertTrue(left.rotateClockwise());
        for (int step = 0; step < 5; step++) assertTrue(left.moveLeft());
        assertFalse(left.moveLeft());
        assertTrue(left.rotateClockwise());
        assertArrayEquals(new int[]{0, 0, 0, 0, -1, -1, -1, -1, -1, -1},
                left.snapshot().cells()[2]);

        BlockBoardDriver right = new BlockBoardDriver(new Board(), sequence(TetrominoType.I));
        right.reset();
        assertTrue(right.spawnNextBlock());
        assertTrue(right.moveDown());
        assertTrue(right.rotateClockwise());
        for (int step = 0; step < 4; step++) assertTrue(right.moveRight());
        assertFalse(right.moveRight());
        assertTrue(right.rotateClockwise());
        assertArrayEquals(new int[]{-1, -1, -1, -1, -1, -1, 0, 0, 0, 0},
                right.snapshot().cells()[2]);
    }

    @Test
    void wallCorrectionDoesNotRotateThroughFixedBlocks() {
        Board board = new Board();
        BlockBoardDriver driver = new BlockBoardDriver(board, sequence(TetrominoType.I));
        driver.reset();
        assertTrue(driver.spawnNextBlock());
        assertTrue(driver.moveDown());
        assertTrue(driver.rotateClockwise());
        for (int step = 0; step < 5; step++) assertTrue(driver.moveLeft());
        assertTrue(board.tryPlace(new Tetromino(TetrominoType.O, Rotation.SPAWN, 2, 1)));
        BoardSnapshot before = driver.snapshot();
        assertFalse(driver.rotateClockwise());
        assertArrayEquals(before.cells()[2], driver.snapshot().cells()[2]);
    }

    @Test
    void landingSnapshotShowsTheGhostAtTheCollisionLimitedLandingPosition() {
        Board board = new Board();
        BlockBoardDriver driver = new BlockBoardDriver(board, sequence(TetrominoType.O));
        driver.reset();
        assertTrue(board.tryPlace(new Tetromino(TetrominoType.O, Rotation.SPAWN, 18, 4)));
        assertTrue(driver.spawnNextBlock());

        int[][] landing = driver.landingSnapshot().cells();
        for (int row = 0; row < landing.length; row++) {
            for (int col = 0; col < landing[row].length; col++) {
                int expected = row >= 16 && row <= 17 && col >= 4 && col <= 5
                        ? TetrominoType.O.id() : BoardSnapshot.EMPTY;
                assertEquals(expected, landing[row][col], "cell at row " + row + ", col " + col);
            }
        }
    }

    @Test
    void locksAndClearsTwoLinesWithoutKeepingTheOldFallingBlock() {
        Board board = new Board();
        BlockBoardDriver driver = new BlockBoardDriver(board, sequence(TetrominoType.O));
        driver.reset();
        for (int col : new int[]{0, 2, 6, 8}) {
            assertTrue(board.tryPlace(new Tetromino(TetrominoType.O, Rotation.SPAWN, 18, col)));
        }
        assertTrue(driver.spawnNextBlock());
        assertEquals(18, driver.hardDrop());
        GameEngine.LockResult result = driver.lockAndClearLines();
        assertEquals(2, result.clearedLines());
        assertEquals(List.of(18, 19), result.clearedRows());
        assertNotNull(result.boardBeforeClear());
        assertEquals(TetrominoType.O.id(), result.boardBeforeClear().cells()[18][1]);
        assertEquals(TetrominoType.O.id(), result.boardBeforeClear().cells()[19][1]);
        for (int[] row : driver.snapshot().cells()) {
            for (int cell : row) assertEquals(BoardSnapshot.EMPTY, cell);
        }
    }

    @Test
    void blockedSpawnSignalsGameOverConditionWithoutHidingTheBoard() {
        Board board = new Board();
        BlockBoardDriver driver = new BlockBoardDriver(board, sequence(TetrominoType.O));
        driver.reset();
        assertTrue(board.tryPlace(new Tetromino(TetrominoType.O, Rotation.SPAWN, 0, 4)));
        assertFalse(driver.spawnNextBlock());
        assertEquals(TetrominoType.O.id(), driver.snapshot().cells()[0][4]);
        assertThrows(IllegalStateException.class, driver::hardDrop);
    }

    @Test
    void engineCanPlayWithTheRealBlockAdapter() throws Exception {
        Board board = new Board();
        BlockBoardDriver driver = new BlockBoardDriver(board, sequence(TetrominoType.O));
        Scores listener = new Scores();
        GameEngine.DropTimer timer = new GameEngine.DropTimer() {
            public void start(int intervalMillis, Runnable tick) { }
            public void stop() { }
        };
        GameEngine engine = new GameEngine(driver, listener, new ScoreCalculator(), timer);
        SwingUtilities.invokeAndWait(() -> {
            assertTrue(engine.start());
            assertEquals(TetrominoType.O.id(), listener.board.cells()[0][4]);
            assertTrue(engine.handle(GameAction.HARD_DROP));
            assertEquals(18, engine.getScore());
            assertEquals(18, listener.score);
            assertEquals(TetrominoType.O.id(), listener.board.cells()[19][4]);
            assertEquals(TetrominoType.O.id(), listener.board.cells()[0][4]);
            assertEquals(2, engine.getGeneratedBlocks());
            engine.close();
        });
    }

    @Test
    void itemAppearsAfterTenClearedLinesAndDeletesItsPartialRow() {
        Board board = new Board();
        Random chooseThirdCell = new Random() {
            @Override public int nextInt(int bound) { return 2; }
        };
        BlockBoardDriver driver = new BlockBoardDriver(board, sequence(TetrominoType.O),
                chooseThirdCell, () -> ItemType.LINE_DELETE);
        driver.setItemMode(true);
        driver.reset();
        for (int round = 0; round < 5; round++) {
            assertTrue(driver.spawnNextBlock());
            for (int col : new int[]{0, 2, 6, 8}) {
                assertTrue(board.tryPlace(new Tetromino(TetrominoType.O, Rotation.SPAWN, 18, col)));
            }
            driver.hardDrop();
            assertEquals(2, driver.lockAndClearLines().clearedLines());
        }
        assertArrayEquals(new int[]{TetrominoType.O.id()}, driver.nextBlockTypes());
        assertTrue(driver.spawnNextBlock());
        assertArrayEquals(new int[]{LineDeleteIds.preview(TetrominoType.O.id(), 2)},
                driver.nextBlockTypes());
        driver.hardDrop();
        assertEquals(0, driver.lockAndClearLines().clearedLines());

        assertTrue(driver.spawnNextBlock());
        assertEquals(LineDeleteIds.boardCell(TetrominoType.O.id()), driver.snapshot().cells()[1][4]);
        driver.hardDrop();
        GameEngine.LockResult itemClear = driver.lockAndClearLines();
        assertEquals(1, itemClear.clearedLines(),
                "L removes its row even when only two cells are occupied");
        assertEquals(List.of(17), itemClear.clearedRows(), "The L row is reported for the clear animation");
        assertNotNull(itemClear.boardBeforeClear());
        assertEquals(BoardSnapshot.EMPTY, board.cellAt(16, 4).map(TetrominoType::id)
                .orElse(BoardSnapshot.EMPTY));
        assertEquals(TetrominoType.O, board.cellAt(17, 4).orElseThrow(),
                "The row above the deleted row falls down");
    }

    @Test
    void normalModeNeverAddsAnItemAfterTenLines() {
        Board board = new Board();
        BlockBoardDriver driver = new BlockBoardDriver(board, sequence(TetrominoType.O));
        driver.reset();
        for (int round = 0; round < 5; round++) {
            assertTrue(driver.spawnNextBlock());
            for (int col : new int[]{0, 2, 6, 8}) {
                assertTrue(board.tryPlace(new Tetromino(TetrominoType.O, Rotation.SPAWN, 18, col)));
            }
            driver.hardDrop();
            assertEquals(2, driver.lockAndClearLines().clearedLines());
        }
        assertTrue(driver.spawnNextBlock());
        assertArrayEquals(new int[]{TetrominoType.O.id()}, driver.nextBlockTypes());
    }

    @Test
    void itemLineUsesTheOrdinaryLineScoringRule() throws Exception {
        Board board = new Board();
        BlockBoardDriver driver = new BlockBoardDriver(board, sequence(TetrominoType.O), new Random() {
            @Override public int nextInt(int bound) { return 2; }
        }, () -> ItemType.LINE_DELETE);
        driver.setItemMode(true);
        Scores listener = new Scores();
        GameEngine.DropTimer timer = new GameEngine.DropTimer() {
            public void start(int intervalMillis, Runnable tick) { }
            public void stop() { }
        };
        GameEngine engine = new GameEngine(driver, listener, new ScoreCalculator(), timer);
        SwingUtilities.invokeAndWait(() -> {
            assertTrue(engine.start());
            for (int round = 0; round < 5; round++) {
                for (int col : new int[]{0, 2, 6, 8}) {
                    assertTrue(board.tryPlace(new Tetromino(TetrominoType.O, Rotation.SPAWN, 18, col)));
                }
                assertTrue(engine.handle(GameAction.HARD_DROP));
            }
            assertEquals(10, engine.getClearedLines());
            assertTrue(engine.handle(GameAction.HARD_DROP));
            int scoreBeforeItem = engine.getScore();
            int level = engine.getLevel();
            assertTrue(engine.handle(GameAction.HARD_DROP));
            assertEquals(11, engine.getClearedLines());
            assertEquals(new ScoreCalculator().dropPoints(16, level)
                    + new ScoreCalculator().lineClearPoints(1, level),
                    engine.getScore() - scoreBeforeItem);
            engine.close();
        });
    }

    private static TetrominoGenerator sequence(TetrominoType... types) {
        AtomicInteger index = new AtomicInteger();
        return () -> types[index.getAndIncrement() % types.length];
    }

    private static final class Scores implements GameEventListener {
        BoardSnapshot board;
        int score;
        public void onBoardUpdated(BoardSnapshot snapshot) { board = snapshot; }
        public void onScoreChanged(int value) { score = value; }
        public void onNextBlocksChanged(int[] types) { }
        public void onPauseStateChanged(boolean paused) { }
        public void onGameOver(int finalScore) { }
    }
}

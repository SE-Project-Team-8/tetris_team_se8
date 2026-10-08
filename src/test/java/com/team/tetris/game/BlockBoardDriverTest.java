package com.team.tetris.game;

import com.team.tetris.block.Board;
import com.team.tetris.block.Rotation;
import com.team.tetris.block.Tetromino;
import com.team.tetris.block.TetrominoGenerator;
import com.team.tetris.block.TetrominoType;
import com.team.tetris.common.events.BoardSnapshot;
import com.team.tetris.common.events.GameEventListener;
import org.junit.jupiter.api.Test;

import javax.swing.SwingUtilities;
import java.util.concurrent.atomic.AtomicInteger;

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
    void locksAndClearsTwoLinesWithoutKeepingTheOldFallingBlock() {
        Board board = new Board();
        BlockBoardDriver driver = new BlockBoardDriver(board, sequence(TetrominoType.O));
        driver.reset();
        for (int col : new int[]{0, 2, 6, 8}) {
            assertTrue(board.tryPlace(new Tetromino(TetrominoType.O, Rotation.SPAWN, 18, col)));
        }
        assertTrue(driver.spawnNextBlock());
        assertEquals(18, driver.hardDrop());
        assertEquals(new GameEngine.LockResult(2, false), driver.lockAndClearLines());
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

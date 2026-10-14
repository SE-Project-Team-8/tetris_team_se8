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

class SlowDownItemTest {
    private static final GameEngine.DropTimer IDLE_TIMER = new GameEngine.DropTimer() {
        public void start(int intervalMillis, Runnable tick) { }
        public void stop() { }
    };

    @Test
    void defaultPolicyLowersTheCappedLevelByEachStepButNeverBelowOne() {
        DefaultSpeedPolicy policy = new DefaultSpeedPolicy();
        assertEquals(policy.calculate(30, 0), policy.calculate(30, 0, 0));
        assertEquals(new SpeedPolicy.Speed(3, 800), policy.calculate(30, 0, 1));
        assertEquals(new SpeedPolicy.Speed(2, 900), policy.calculate(30, 25, 2));
        assertEquals(new SpeedPolicy.Speed(1, 1000), policy.calculate(30, 0, 9));
        assertEquals(new SpeedPolicy.Speed(9, 200), policy.calculate(Integer.MAX_VALUE, 0, 1),
                "The top level also drops by exactly one step");
        assertThrows(IllegalArgumentException.class, () -> policy.calculate(0, 0, -1));
    }

    @Test
    void customPoliciesKeepTheirRuleUnlessTheyHandleSlowDown() {
        SpeedPolicy custom = (blocks, lines) -> new SpeedPolicy.Speed(4, 500);
        assertEquals(new SpeedPolicy.Speed(4, 500), custom.calculate(0, 0, 3));
    }

    @Test
    void slowDownCellIsShownAndReportedForTheBlockThatCarriedIt() {
        Board board = new Board();
        BlockBoardDriver driver = slowDownDriver(board);
        driver.setItemMode(true);
        driver.reset();
        for (int round = 0; round < 5; round++) {
            assertTrue(driver.spawnNextBlock());
            fillTwoRowsExceptTheCenter(board);
            driver.hardDrop();
            driver.lockAndClearLines();
        }
        assertTrue(driver.spawnNextBlock());
        assertArrayEquals(new int[]{ItemCellIds.preview(ItemType.SLOW_DOWN.id(), TetrominoType.O.id(), 0)},
                driver.nextBlockTypes());
        driver.hardDrop();
        driver.lockAndClearLines();
        assertTrue(driver.spawnNextBlock());
        assertEquals(ItemCellIds.boardCell(ItemType.SLOW_DOWN.id(), TetrominoType.O.id()),
                driver.snapshot().cells()[0][4]);
        driver.hardDrop();
        assertEquals(0, driver.lockAndClearLines().clearedLines(), "S never deletes a row by itself");
        assertEquals(ItemType.SLOW_DOWN, driver.lockedItem());
    }

    @Test
    void lockingTheSlowDownBlockLowersTheCurrentLevelForTheRestOfTheGame() throws Exception {
        Board board = new Board();
        GameEngine engine = engine(slowDownDriver(board), new DefaultSpeedPolicy());
        SwingUtilities.invokeAndWait(() -> {
            assertTrue(engine.start());
            earnSlowDownBlock(engine, board);
            assertEquals(2, engine.getLevel(), "Ten cleared lines reached level 2");
            assertEquals(900, engine.getDropIntervalMillis());

            assertTrue(engine.handle(GameAction.HARD_DROP));
            assertEquals(1, engine.getLevel());
            assertEquals(1000, engine.getDropIntervalMillis(), "The next block falls at the slower speed");

            earnSlowDownBlock(engine, board);
            assertEquals(20, engine.getClearedLines());
            assertEquals(2, engine.getLevel(), "Level 3 by progress, one step lower by the earlier item");
            assertEquals(900, engine.getDropIntervalMillis());
            engine.close();
        });
    }

    @Test
    void slowDownAtLevelOneIsNotSavedForALaterLevel() throws Exception {
        Board board = new Board();
        // Level 1 until twenty lines, then level 3; slow-down steps lower that level.
        SpeedPolicy policy = new SpeedPolicy() {
            public Speed calculate(int blocks, int lines) { return calculate(blocks, lines, 0); }
            public Speed calculate(int blocks, int lines, int steps) {
                int level = Math.max(1, (lines >= 20 ? 3 : 1) - steps);
                return new Speed(level, 1100 - level * 100);
            }
        };
        GameEngine engine = engine(slowDownDriver(board), policy);
        SwingUtilities.invokeAndWait(() -> {
            assertTrue(engine.start());
            earnSlowDownBlock(engine, board);
            assertEquals(1, engine.getLevel());
            assertTrue(engine.handle(GameAction.HARD_DROP));
            assertEquals(1, engine.getLevel());

            earnSlowDownBlock(engine, board);
            assertEquals(3, engine.getLevel(), "The unused slow-down did not lower the later level");
            engine.close();
        });
    }

    @Test
    void newGameForgetsEarlierSlowDowns() throws Exception {
        Board board = new Board();
        GameEngine engine = engine(slowDownDriver(board), new DefaultSpeedPolicy());
        SwingUtilities.invokeAndWait(() -> {
            assertTrue(engine.start());
            earnSlowDownBlock(engine, board);
            assertTrue(engine.handle(GameAction.HARD_DROP));
            assertEquals(1, engine.getLevel());
            assertTrue(engine.stop());

            assertTrue(engine.start());
            earnSlowDownBlock(engine, board);
            assertEquals(2, engine.getLevel(), "Ten cleared lines reach level 2 again");
            engine.close();
        });
    }

    /** Clears ten lines, lets the regular preview fall, and leaves the S block falling at the top. */
    private static void earnSlowDownBlock(GameEngine engine, Board board) {
        board.clear(); // Isolate the fixture terrain without resetting earned game progress.
        for (int round = 0; round < 5; round++) {
            fillTwoRowsExceptTheCenter(board);
            assertTrue(engine.handle(GameAction.HARD_DROP));
        }
        assertTrue(engine.handle(GameAction.HARD_DROP)); // The pre-existing regular preview falls first.
    }

    private static GameEngine engine(BlockBoardDriver driver, SpeedPolicy policy) {
        driver.setItemMode(true);
        return new GameEngine(driver, new Events(), new ScoreCalculator(), policy, IDLE_TIMER);
    }

    private static BlockBoardDriver slowDownDriver(Board board) {
        Random firstCell = new Random() {
            @Override public int nextInt(int bound) { return 0; }
        };
        return new BlockBoardDriver(board, () -> TetrominoType.O, firstCell, () -> ItemType.SLOW_DOWN);
    }

    private static void fillTwoRowsExceptTheCenter(Board board) {
        for (int col : new int[]{0, 2, 6, 8}) {
            assertTrue(board.tryPlace(new Tetromino(TetrominoType.O, Rotation.SPAWN, 18, col)));
        }
    }

    private static final class Events implements GameEventListener {
        public void onBoardUpdated(BoardSnapshot snapshot) { }
        public void onScoreChanged(int score) { }
        public void onNextBlocksChanged(int[] types) { }
        public void onPauseStateChanged(boolean paused) { }
        public void onGameOver(int finalScore) { }
    }
}

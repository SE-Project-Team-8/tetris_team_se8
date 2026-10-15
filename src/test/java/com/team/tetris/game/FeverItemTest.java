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
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

class FeverItemTest {
    private static final ScoreCalculator SCORING = new ScoreCalculator();

    @Test
    void feverCellIsShownAndReportedOnlyForTheBlockThatCarriedIt() {
        Board board = new Board();
        BlockBoardDriver driver = feverDriver(board);
        driver.setItemMode(true);
        driver.reset();
        for (int round = 0; round < 5; round++) {
            assertTrue(driver.spawnNextBlock());
            fillTwoRowsExceptTheCenter(board);
            driver.hardDrop();
            assertEquals(2, driver.lockAndClearLines().clearedLines());
            assertEquals(ItemType.NONE, driver.lockedItem());
        }
        assertTrue(driver.spawnNextBlock());
        assertArrayEquals(new int[]{ItemCellIds.preview(ItemType.FEVER.id(), TetrominoType.O.id(), 1)},
                driver.nextBlockTypes());
        driver.hardDrop();
        driver.lockAndClearLines();

        assertTrue(driver.spawnNextBlock());
        assertEquals(ItemCellIds.boardCell(ItemType.FEVER.id(), TetrominoType.O.id()),
                driver.snapshot().cells()[0][5], "The second cell of the O carries F");
        driver.hardDrop();
        assertEquals(0, driver.lockAndClearLines().clearedLines(), "F never deletes a row by itself");
        assertEquals(ItemType.FEVER, driver.lockedItem());

        assertTrue(driver.spawnNextBlock());
        driver.hardDrop();
        driver.lockAndClearLines();
        assertEquals(ItemType.NONE, driver.lockedItem());
    }

    @Test
    void feverDoublesEveryPointForTenSecondsAfterItsBlockLocks() throws Exception {
        Board board = new Board();
        Events events = new Events();
        ManualTimer fever = new ManualTimer();
        GameEngine engine = engine(feverDriver(board), events, fever);
        SwingUtilities.invokeAndWait(() -> {
            assertTrue(engine.start());
            earnFeverBlock(engine, board);
            assertEquals(0, engine.getFeverSeconds());
            int beforeFeverBlock = engine.getScore();
            assertTrue(engine.handle(GameAction.SOFT_DROP));
            assertEquals(SCORING.dropPoints(1, engine.getLevel()), engine.getScore() - beforeFeverBlock,
                    "The fever block itself still falls at the normal rate");

            assertTrue(engine.handle(GameAction.HARD_DROP));
            assertEquals(GameEngine.FEVER_SECONDS, engine.getFeverSeconds());
            assertEquals(List.of(GameEngine.FEVER_SECONDS), events.fever);
            assertEquals(1000, fever.intervalMillis);
            assertDoubledSoftDrop(engine, true);

            for (int second = 1; second < GameEngine.FEVER_SECONDS; second++) fever.fire();
            assertEquals(1, engine.getFeverSeconds());
            assertDoubledSoftDrop(engine, true);
            fever.fire();
            assertEquals(0, engine.getFeverSeconds());
            assertEquals(0, events.fever.getLast());
            assertFalse(fever.running, "The countdown stops when the fever ends");
            assertDoubledSoftDrop(engine, false);
            engine.close();
        });
    }

    @Test
    void pausedTimeDoesNotConsumeTheFever() throws Exception {
        Board board = new Board();
        ManualTimer fever = new ManualTimer();
        GameEngine engine = engine(feverDriver(board), new Events(), fever);
        SwingUtilities.invokeAndWait(() -> {
            assertTrue(engine.start());
            earnFeverBlock(engine, board);
            assertTrue(engine.handle(GameAction.HARD_DROP));
            fever.fire();
            fever.fire();
            assertEquals(8, engine.getFeverSeconds());

            assertTrue(engine.pause());
            assertFalse(fever.running);
            Runnable staleTick = fever.tick;
            staleTick.run();
            assertEquals(8, engine.getFeverSeconds(), "A queued tick from before the pause is ignored");

            assertTrue(engine.resume());
            assertTrue(fever.running);
            fever.fire();
            assertEquals(7, engine.getFeverSeconds());
            engine.close();
        });
    }

    @Test
    void anotherFeverRefreshesTheCountdownInsteadOfStacking() throws Exception {
        Board board = new Board();
        ManualTimer fever = new ManualTimer();
        GameEngine engine = engine(feverDriver(board), new Events(), fever);
        SwingUtilities.invokeAndWait(() -> {
            assertTrue(engine.start());
            earnFeverBlock(engine, board);
            assertTrue(engine.handle(GameAction.HARD_DROP));
            for (int second = 0; second < 4; second++) fever.fire();
            assertEquals(6, engine.getFeverSeconds());

            earnFeverBlock(engine, board);
            assertTrue(engine.handle(GameAction.HARD_DROP));
            assertEquals(GameEngine.FEVER_SECONDS, engine.getFeverSeconds());
            engine.close();
        });
    }

    @Test
    void quittingAndStartingANewGameEndsTheFever() throws Exception {
        Board board = new Board();
        Events events = new Events();
        ManualTimer fever = new ManualTimer();
        GameEngine engine = engine(feverDriver(board), events, fever);
        SwingUtilities.invokeAndWait(() -> {
            assertTrue(engine.start());
            earnFeverBlock(engine, board);
            assertTrue(engine.handle(GameAction.HARD_DROP));
            assertTrue(engine.stop());
            assertFalse(fever.running);

            assertTrue(engine.start());
            assertEquals(0, engine.getFeverSeconds());
            assertEquals(0, events.fever.getLast());
            assertDoubledSoftDrop(engine, false);
            engine.close();
        });
    }

    /** Clears ten lines, lets the regular preview fall, and leaves the F block falling at the top. */
    private static void earnFeverBlock(GameEngine engine, Board board) {
        board.clear(); // Isolate the fixture terrain without resetting earned game progress.
        for (int round = 0; round < 5; round++) {
            fillTwoRowsExceptTheCenter(board);
            assertTrue(engine.handle(GameAction.HARD_DROP));
        }
        assertTrue(engine.handle(GameAction.HARD_DROP)); // The pre-existing regular preview falls first.
    }

    private static void assertDoubledSoftDrop(GameEngine engine, boolean doubled) {
        int before = engine.getScore();
        assertTrue(engine.handle(GameAction.SOFT_DROP));
        int expected = SCORING.dropPoints(1, engine.getLevel()) * (doubled ? GameEngine.FEVER_MULTIPLIER : 1);
        assertEquals(expected, engine.getScore() - before);
    }

    private static GameEngine engine(BlockBoardDriver driver, Events events, ManualTimer fever) {
        driver.setItemMode(true);
        return new GameEngine(driver, events, SCORING, new DefaultSpeedPolicy(), new ManualTimer(), fever);
    }

    private static BlockBoardDriver feverDriver(Board board) {
        Random secondCell = new Random() {
            @Override public int nextInt(int bound) { return 1; }
        };
        return new BlockBoardDriver(board, () -> TetrominoType.O, secondCell, () -> ItemType.FEVER);
    }

    private static void fillTwoRowsExceptTheCenter(Board board) {
        for (int col : new int[]{0, 2, 6, 8}) {
            assertTrue(board.tryPlace(new Tetromino(TetrominoType.O, Rotation.SPAWN, 18, col)));
        }
    }

    private static final class ManualTimer implements GameEngine.DropTimer {
        Runnable tick;
        int intervalMillis;
        boolean running;

        public void start(int intervalMillis, Runnable tick) {
            this.intervalMillis = intervalMillis;
            this.tick = tick;
            running = true;
        }

        public void stop() { running = false; }

        void fire() {
            assertTrue(running, "A stopped Swing timer never fires");
            tick.run();
        }
    }

    private static final class Events implements GameEventListener {
        final List<Integer> fever = new ArrayList<>();
        public void onBoardUpdated(BoardSnapshot snapshot) { }
        public void onScoreChanged(int score) { }
        public void onNextBlocksChanged(int[] types) { }
        public void onPauseStateChanged(boolean paused) { }
        public void onGameOver(int finalScore) { }
        @Override public void onFeverChanged(int remainingSeconds) { fever.add(remainingSeconds); }
    }
}

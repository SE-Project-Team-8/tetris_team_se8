package com.team.tetris.game;

import com.team.tetris.common.events.BoardSnapshot;
import com.team.tetris.common.events.GameEventListener;
import com.team.tetris.settings.KeyBindings;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.swing.SwingUtilities;
import java.awt.event.KeyEvent;
import java.lang.reflect.InvocationTargetException;
import java.util.Arrays;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import static com.team.tetris.settings.KeyBindings.Action.*;
import static org.junit.jupiter.api.Assertions.*;

class GameEngineTest {
    private FakeBoard board;
    private ManualTimer timer;
    private Events events;
    private GameEngine engine;

    @BeforeEach
    void setUp() {
        board = new FakeBoard();
        timer = new ManualTimer();
        events = new Events();
        engine = new GameEngine(board, events, new ScoreCalculator(), timer);
    }

    @Test
    void startsWithOneSecondGravityAndPublishesInitialState() throws Exception {
        edt(() -> {
            assertEquals(GameState.READY, engine.getState());
            assertFalse(engine.handle(SOFT_DROP));
            assertTrue(engine.start());
            assertFalse(engine.start());
            assertEquals(GameState.RUNNING, engine.getState());
            assertEquals(1000, timer.interval);
            assertEquals(1, engine.getGeneratedBlocks());
            assertEquals(0, events.score);
            assertEquals(20, events.snapshot.rows());
            assertEquals(10, events.snapshot.cols());
            assertArrayEquals(new int[]{2}, events.preview);
            timer.fire();
            assertEquals(1, engine.getScore());
            assertEquals(1, board.downCalls);
        });
    }

    @Test
    void repeatedMovementKeysAreNotDebouncedAndBlockedMovementDoesNotScore() throws Exception {
        edt(() -> {
            engine.start();
            for (int i = 0; i < 5; i++) assertTrue(engine.handleKey(KeyEvent.VK_LEFT, KeyBindings.defaults()));
            assertEquals(5, board.leftCalls);
            assertTrue(engine.handle(MOVE_RIGHT));
            assertTrue(engine.handle(ROTATE_CLOCKWISE));
            board.canMove = false;
            assertFalse(engine.handle(MOVE_LEFT));
            assertFalse(engine.handleKey(KeyEvent.VK_F1, KeyBindings.defaults()));
            assertEquals(0, engine.getScore());
        });
    }

    @Test
    void automaticManualAndHardDropsReceiveTheSamePerCellScore() throws Exception {
        edt(() -> {
            engine.start();
            timer.fire();
            engine.handle(SOFT_DROP);
            board.dropDistance = 18;
            engine.handle(HARD_DROP);
            assertEquals(20, engine.getScore());
            assertEquals(20, events.score);
            assertEquals(1, board.locks);
            assertEquals(2, engine.getGeneratedBlocks());
            assertEquals(2, events.previews);
        });
    }

    @Test
    void zeroDistanceDropLocksOnceWithoutAwardingFallPoints() throws Exception {
        edt(() -> {
            engine.start();
            board.dropDistance = 0;
            engine.handle(HARD_DROP);
            board.canFall = false;
            timer.fire();
            assertEquals(2, board.locks);
            assertEquals(0, engine.getScore());
        });
    }

    @Test
    void pauseIgnoresInputAndStaleTicksEvenAfterResume() throws Exception {
        edt(() -> {
            engine.start();
            Runnable oldTick = timer.callback;
            assertTrue(engine.handle(PAUSE));
            assertFalse(timer.running);
            assertFalse(engine.start());
            assertFalse(engine.pause());
            for (KeyBindings.Action action : new KeyBindings.Action[]{MOVE_LEFT, MOVE_RIGHT,
                    SOFT_DROP, HARD_DROP, ROTATE_CLOCKWISE}) assertFalse(engine.handle(action));
            oldTick.run();
            assertEquals(0, engine.getScore());
            assertTrue(engine.handle(PAUSE));
            assertFalse(engine.resume());
            oldTick.run();
            assertEquals(0, engine.getScore());
            timer.fire();
            assertEquals(1, engine.getScore());
            assertFalse(events.paused);
        });
    }

    @Test
    void speedIncreasesOnTenthGeneratedPieceAndHasAFloor() throws Exception {
        edt(() -> {
            engine.start();
            board.dropDistance = 0;
            for (int i = 0; i < 8; i++) engine.handle(HARD_DROP);
            assertEquals(1000, timer.interval);
            engine.handle(HARD_DROP);
            assertEquals(10, engine.getGeneratedBlocks());
            assertEquals(2, engine.getLevel());
            assertEquals(900, timer.interval);
            timer.fire();
            assertEquals(2, engine.getScore());
            for (int i = 0; i < 150; i++) engine.handle(HARD_DROP);
            assertEquals(10, engine.getLevel());
            assertEquals(100, timer.interval);
        });
    }

    @Test
    void clearedLinesAlsoIncreaseSpeedAndUseLevelOfTheCompletedPiece() throws Exception {
        edt(() -> {
            engine.start();
            board.dropDistance = 0;
            board.lines = 4;
            for (int i = 0; i < 3; i++) engine.handle(HARD_DROP);
            assertEquals(12, engine.getClearedLines());
            assertEquals(4, engine.getGeneratedBlocks());
            assertEquals(2, engine.getLevel());
            assertEquals(2400, engine.getScore());
            engine.handle(HARD_DROP);
            assertEquals(4000, engine.getScore());
        });
    }

    @Test
    void blockedSpawnEndsExactlyOnceAndRestartDiscardsOldTimer() throws Exception {
        edt(() -> {
            engine.start();
            Runnable oldTick = timer.callback;
            board.canSpawn = false;
            engine.handle(HARD_DROP);
            assertEquals(GameState.GAME_OVER, engine.getState());
            assertFalse(timer.running);
            assertEquals(1, events.gameOvers);
            assertEquals(engine.getScore(), events.finalScore);
            oldTick.run();
            assertFalse(engine.handle(SOFT_DROP));
            assertEquals(1, events.gameOvers);
            board.canSpawn = true;
            engine.start();
            oldTick.run();
            assertEquals(0, engine.getScore());
            assertEquals(1, engine.getLevel());
            assertEquals(0, engine.getClearedLines());
        });
    }

    @Test
    void failedInitialSpawnAndTopOutStopTheGame() throws Exception {
        edt(() -> {
            board.canSpawn = false;
            engine.start();
            assertEquals(GameState.GAME_OVER, engine.getState());
            assertEquals(0, events.finalScore);
            board.canSpawn = true;
            engine.start();
            board.topOut = true;
            engine.handle(HARD_DROP);
            assertEquals(GameState.GAME_OVER, engine.getState());
            assertEquals(1, engine.getGeneratedBlocks());
            assertEquals(2, events.gameOvers);
        });
    }

    @Test
    void quitWorksDuringRunningAndPausedWithoutSubmittingAScore() throws Exception {
        edt(() -> {
            engine.start();
            assertTrue(engine.handle(QUIT));
            assertEquals(GameState.STOPPED, engine.getState());
            assertFalse(engine.stop());
            engine.start();
            engine.pause();
            engine.close();
            assertEquals(GameState.STOPPED, engine.getState());
            assertFalse(timer.running);
            assertFalse(events.paused);
            assertEquals(0, events.gameOvers);
        });
    }

    @Test
    void publishedArraysCannotMutateTheBoardOrPreview() throws Exception {
        edt(() -> {
            engine.start();
            events.snapshot.cells()[0][0] = 999;
            events.preview[0] = 999;
            assertEquals(-1, board.cells[0][0]);
            assertEquals(2, board.preview[0]);
        });
    }

    @Test
    void wrongThreadAndIllegalTransitionsAreRejected() {
        assertThrows(IllegalStateException.class, engine::start);
        assertFalse(GameState.READY.canTransitionTo(GameState.PAUSED));
        assertFalse(GameState.PAUSED.canTransitionTo(GameState.GAME_OVER));
        assertFalse(GameState.RUNNING.canTransitionTo(GameState.RUNNING));
        assertFalse(GameState.RUNNING.canTransitionTo(null));
        assertThrows(IllegalArgumentException.class, () -> new GameEngine.LockResult(5, false));
    }

    @Test
    void realSwingTimerDropsOnEdtAfterTheInitialInterval() throws Exception {
        CountDownLatch dropped = new CountDownLatch(1);
        AtomicLong firstDrop = new AtomicLong();
        AtomicLong started = new AtomicLong();
        GameEngine realEngine = new GameEngine(board, new Events() {
            @Override public void onScoreChanged(int score) {
                super.onScoreChanged(score);
                if (score > 0 && firstDrop.compareAndSet(0, System.nanoTime())) dropped.countDown();
            }
        });
        try {
            edt(() -> {
                started.set(System.nanoTime());
                realEngine.start();
            });
            assertTrue(dropped.await(5, TimeUnit.SECONDS), "Real timer did not fire");
            assertTrue(TimeUnit.NANOSECONDS.toMillis(firstDrop.get() - started.get()) >= 900,
                    "Gravity fired before the initial one-second interval");
        } finally {
            edt(realEngine::close);
        }
    }

    private static void edt(Runnable action) throws Exception {
        try {
            SwingUtilities.invokeAndWait(action);
        } catch (InvocationTargetException failure) {
            if (failure.getCause() instanceof AssertionError assertion) throw assertion;
            if (failure.getCause() instanceof Exception exception) throw exception;
            throw failure;
        }
    }

    private static final class ManualTimer implements GameEngine.DropTimer {
        int interval;
        boolean running;
        Runnable callback;
        public void start(int interval, Runnable callback) {
            this.interval = interval;
            this.callback = callback;
            running = true;
        }
        public void stop() { running = false; }
        void fire() { if (running) callback.run(); }
    }

    private static class Events implements GameEventListener {
        int score;
        int finalScore;
        int gameOvers;
        int previews;
        boolean paused;
        BoardSnapshot snapshot;
        int[] preview;
        public void onBoardUpdated(BoardSnapshot value) {
            assertTrue(SwingUtilities.isEventDispatchThread());
            snapshot = value;
        }
        public void onScoreChanged(int value) {
            assertTrue(SwingUtilities.isEventDispatchThread());
            score = value;
        }
        public void onNextBlocksChanged(int[] value) { preview = value; previews++; }
        public void onPauseStateChanged(boolean value) { paused = value; }
        public void onGameOver(int value) { finalScore = value; gameOvers++; }
    }

    private static final class FakeBoard implements GameEngine.BoardDriver {
        int[][] cells = new int[20][10];
        int[] preview = {2};
        boolean canFall = true;
        boolean canSpawn = true;
        boolean canMove = true;
        boolean topOut;
        int downCalls;
        int leftCalls;
        int locks;
        int lines;
        int dropDistance = 18;
        public void reset() { for (int[] row : cells) Arrays.fill(row, -1); }
        public boolean spawnNextBlock() { return canSpawn; }
        public boolean moveLeft() { leftCalls++; return canMove; }
        public boolean moveRight() { return canMove; }
        public boolean moveDown() { downCalls++; return canFall; }
        public boolean rotateClockwise() { return canMove; }
        public int hardDrop() { return dropDistance; }
        public GameEngine.LockResult lockAndClearLines() {
            locks++;
            return new GameEngine.LockResult(lines, topOut);
        }
        public BoardSnapshot snapshot() { return new BoardSnapshot(cells); }
        public int[] nextBlockTypes() { return preview; }
    }
}

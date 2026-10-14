package com.team.tetris.game;

import com.team.tetris.block.Board;
import com.team.tetris.block.Rotation;
import com.team.tetris.block.Tetromino;
import com.team.tetris.block.TetrominoType;
import com.team.tetris.block.WeightedTetrominoGenerator;
import com.team.tetris.common.Difficulty;
import com.team.tetris.common.events.BoardSnapshot;
import com.team.tetris.common.events.GameEventListener;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import javax.swing.SwingUtilities;
import java.util.Random;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class DifficultyEngineTest {
    @ParameterizedTest
    @EnumSource(Difficulty.class)
    void clearingTenAndTwentyLinesAppliesTheNewDifficultyIntervalsToTheRealTimer(Difficulty selected) throws Exception {
        Board board = new Board();
        BlockBoardDriver driver = new BlockBoardDriver(board, () -> TetrominoType.O);
        ManualTimer gravity = new ManualTimer();
        ManualTimer survival = new ManualTimer();
        GameEngine engine = engine(driver, gravity, survival, new AtomicReference<>(selected));
        SwingUtilities.invokeAndWait(() -> {
            try {
                engine.start();
                for (int placement = 0; placement < 10; placement++) {
                    for (int col : new int[]{0, 2, 6, 8}) {
                        assertTrue(board.tryPlace(new Tetromino(TetrominoType.O, Rotation.SPAWN, 18, col)));
                    }
                    engine.handle(GameAction.HARD_DROP);
                    if (placement == 4) {
                        assertEquals(10, engine.getClearedLines());
                        int expected = switch (selected) {
                            case EASY -> 840;
                            case NORMAL -> 800;
                            case HARD -> 760;
                        };
                        assertEquals(expected, gravity.interval);
                    }
                }
                assertEquals(20, engine.getClearedLines());
                int expected = switch (selected) {
                    case EASY -> 680;
                    case NORMAL -> 600;
                    case HARD -> 520;
                };
                assertEquals(expected, gravity.interval);
                assertEquals(1, survival.starts);
            } finally {
                engine.close();
            }
        });
    }

    @ParameterizedTest
    @EnumSource(Difficulty.class)
    void realDriverUsesFrozenDifficultyAndSurvivalIsOnePointForEveryDifficulty(Difficulty selected) throws Exception {
        AtomicReference<Difficulty> preferences = new AtomicReference<>(selected);
        Board board = new Board();
        BlockBoardDriver driver = weightedDriver(board);
        ManualTimer gravity = new ManualTimer();
        ManualTimer survival = new ManualTimer();
        GameEngine engine = engine(driver, gravity, survival, preferences);
        SwingUtilities.invokeAndWait(() -> {
            try {
                assertEquals(0, engine.getScore());
                assertTrue(engine.start());
                assertEquals(selected, engine.getDifficulty());
                assertArrayEquals(new int[]{selected == Difficulty.HARD ? TetrominoType.O.id() : TetrominoType.I.id()},
                        driver.nextBlockTypes(), "Configure the actual generator before the preview is filled");
                assertEquals(1000, gravity.interval);
                assertEquals(1000, survival.interval);
                for (int second = 0; second < 5; second++) survival.fire();
                int score = 5;
                assertEquals(score, engine.getScore());

                Difficulty nextDifficulty = selected == Difficulty.HARD ? Difficulty.EASY : Difficulty.HARD;
                preferences.set(nextDifficulty);
                survival.fire();
                score++;
                assertEquals(score, engine.getScore(), "Preferences apply to the next game, not mid-game");
                Runnable beforePause = survival.callback;
                engine.pause();
                assertFalse(gravity.running);
                assertFalse(survival.running);
                beforePause.run();
                assertEquals(score, engine.getScore());
                engine.resume();
                beforePause.run();
                assertEquals(score, engine.getScore(), "Queued pre-pause ticks remain invalid after resume");
                survival.fire();
                score++;
                assertEquals(score, engine.getScore());
                assertEquals(selected, engine.getDifficulty());

                Runnable beforeStop = survival.callback;
                engine.stop();
                beforeStop.run();
                assertEquals(score, engine.getScore());
                engine.start();
                assertEquals(0, engine.getScore());
                assertEquals(nextDifficulty, engine.getDifficulty());
                beforeStop.run();
                assertEquals(0, engine.getScore(), "Old-game callbacks cannot score in the new game");
                survival.fire();
                assertEquals(1, engine.getScore());
            } finally {
                engine.close();
            }
            assertFalse(survival.running);
            assertFalse(gravity.running);
        });
    }

    @Test
    void spawningBlocksDoesNotRestartTheIndependentSecondsTimer() throws Exception {
        Board board = new Board();
        BlockBoardDriver driver = weightedDriver(board);
        ManualTimer gravity = new ManualTimer();
        ManualTimer survival = new ManualTimer();
        GameEngine engine = engine(driver, gravity, survival, new AtomicReference<>(Difficulty.NORMAL));
        SwingUtilities.invokeAndWait(() -> {
            try {
                engine.start();
                assertEquals(1, survival.starts);
                engine.handle(GameAction.HARD_DROP);
                assertEquals(2, gravity.starts);
                assertEquals(1, survival.starts);
                int scoreBeforeSecond = engine.getScore();
                survival.fire();
                assertEquals(scoreBeforeSecond + 1, engine.getScore());
            } finally {
                engine.close();
            }
        });
    }

    @Test
    void gameOverStopsSurvivalScoringIncludingAlreadyQueuedCallbacks() throws Exception {
        Board board = new Board();
        BlockBoardDriver driver = weightedDriver(board);
        ManualTimer gravity = new ManualTimer();
        ManualTimer survival = new ManualTimer();
        GameEngine engine = engine(driver, gravity, survival, new AtomicReference<>(Difficulty.NORMAL));
        SwingUtilities.invokeAndWait(() -> {
            try {
                engine.start();
                engine.handle(GameAction.SOFT_DROP);
                engine.handle(GameAction.SOFT_DROP);
                assertTrue(board.tryPlace(new Tetromino(TetrominoType.O, Rotation.SPAWN, 0, 4)));
                Runnable queued = survival.callback;
                engine.handle(GameAction.HARD_DROP);
                assertEquals(GameState.GAME_OVER, engine.getState());
                assertFalse(survival.running);
                int finalScore = engine.getScore();
                queued.run();
                assertEquals(finalScore, engine.getScore());
            } finally {
                engine.close();
            }
        });
    }

    @ParameterizedTest
    @EnumSource(Difficulty.class)
    void fastAndSlowDropsEarnTheSameLineBonusWithoutWaitingForSurvivalTicks(Difficulty selected) throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            for (boolean instant : new boolean[]{true, false}) {
                Board board = new Board();
                BlockBoardDriver driver = new BlockBoardDriver(board, () -> TetrominoType.O);
                ManualTimer gravity = new ManualTimer();
                ManualTimer survival = new ManualTimer();
                AtomicReference<Difficulty> preferences = new AtomicReference<>(selected);
                GameEngine engine = engine(driver, gravity, survival, preferences);
                try {
                    engine.start();
                    // A setting change must not alter scoring in a game already running.
                    preferences.set(selected == Difficulty.HARD ? Difficulty.EASY : Difficulty.HARD);
                    for (int col : new int[]{0, 2, 6, 8}) {
                        assertTrue(board.tryPlace(new Tetromino(TetrominoType.O, Rotation.SPAWN, 18, col)));
                    }
                    // The two-cell-high O piece starts at the top and lands on rows 18/19.
                    int cells = 18;
                    if (instant) {
                        engine.handle(GameAction.HARD_DROP);
                    } else {
                        for (int cell = 0; cell <= cells; cell++) engine.handle(GameAction.SOFT_DROP);
                    }
                    assertEquals(2, engine.getClearedLines());
                    assertEquals(cells + 300 * selected.lineScorePercent() / 100, engine.getScore());
                    survival.fire();
                    assertEquals(cells + 300 * selected.lineScorePercent() / 100 + 1, engine.getScore());
                } finally {
                    engine.close();
                }
            }
        });
    }

    private static BlockBoardDriver weightedDriver(Board board) {
        Random fixed = new Random() {
            @Override public int nextInt(int bound) { return 8; }
        };
        return new BlockBoardDriver(board, new WeightedTetrominoGenerator(fixed, Difficulty.NORMAL));
    }

    private static GameEngine engine(BlockBoardDriver driver, ManualTimer gravity, ManualTimer survival,
                                     AtomicReference<Difficulty> preferences) {
        return new GameEngine(driver, new Events(), new ScoreCalculator(), new DefaultSpeedPolicy(),
                gravity, survival, preferences::get);
    }

    private static final class ManualTimer implements GameEngine.DropTimer {
        int interval;
        int starts;
        boolean running;
        Runnable callback;
        public void start(int intervalMillis, Runnable tick) {
            interval = intervalMillis;
            starts++;
            running = true;
            callback = tick;
        }
        public void stop() { running = false; }
        void fire() { if (running) callback.run(); }
    }

    private static final class Events implements GameEventListener {
        public void onBoardUpdated(BoardSnapshot snapshot) { }
        public void onScoreChanged(int score) { }
        public void onNextBlocksChanged(int[] types) { }
        public void onPauseStateChanged(boolean paused) { }
        public void onGameOver(int score) { }
    }
}

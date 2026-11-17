/*
 * FeverItem(F) 테스트. 효과 요청(scoreMultiplier(2, 10))과 보드 무변경, 실제 BlockBoardDriver·GameEngine 경로의
 * F 칸 표시, 10초 동안 2배(F 블록 자신은 1배), 정확한 10초 시작점, 일시정지 중 정지와 진행분 보존(연속 일시정지로
 * 늘릴 수 없음), 재획득 갱신, 새 게임 종료를 검증한다.
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
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class FeverItemTest {
    private static final ScoreCalculator SCORING = new ScoreCalculator();
    private static final FeverItem FEVER = new FeverItem();
    /** O block cell order is (0,0), (0,1), (1,0), (1,1); index 1 is the top-right cell. */
    private static final int TOP_RIGHT = 1;

    @Test
    void asksForDoubleScoreForTenSecondsAndLeavesTheBoardAlone() {
        assertEquals('F', FEVER.symbol());
        assertEquals("FEVER", FEVER.name());
        Board board = new Board();
        assertTrue(board.tryPlace(new Tetromino(TetrominoType.O, Rotation.SPAWN, 18, 4)));
        assertEquals(Set.of(), FEVER.onLock(board, new Cell(18, 5)));
        assertEquals(TetrominoType.O, board.cellAt(18, 5).orElseThrow(), "F erases nothing");

        List<String> requests = new ArrayList<>();
        FEVER.onActivated(new GameEffects() {
            public void scoreMultiplier(int multiplier, int seconds) { requests.add(multiplier + "x" + seconds); }
            public void lowerLevel(int steps) { requests.add("lower" + steps); }
        });
        assertEquals(List.of("2x10"), requests);
    }

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
            assertNull(driver.lockedItem());
        }
        assertTrue(driver.spawnNextBlock());
        assertArrayEquals(new int[]{CellCodes.preview(TetrominoType.O.id(), FeverItem.SYMBOL, TOP_RIGHT)},
                driver.nextBlockTypes());
        driver.hardDrop();
        driver.lockAndClearLines();

        assertTrue(driver.spawnNextBlock());
        assertEquals(CellCodes.cell(TetrominoType.O.id(), FeverItem.SYMBOL), driver.snapshot().cells()[0][5],
                "The second cell of the O carries F");
        driver.hardDrop();
        assertEquals(0, driver.lockAndClearLines().clearedLines(), "F never deletes a row by itself");
        assertSame(FEVER, driver.lockedItem());

        assertTrue(driver.spawnNextBlock());
        driver.hardDrop();
        driver.lockAndClearLines();
        assertNull(driver.lockedItem());
    }

    @Test
    void feverDoublesEveryPointForTenSecondsAfterItsBlockLocks() throws Exception {
        Board board = new Board();
        ManualTimer survival = new ManualTimer();
        GameEngine engine = engine(feverDriver(board), survival);
        SwingUtilities.invokeAndWait(() -> {
            try {
                assertTrue(engine.start());
                earnFeverBlock(engine, board);
                assertEquals(0, engine.getScoreMultiplierSeconds());
                int beforeFeverBlock = engine.getScore();
                assertTrue(engine.handle(GameAction.SOFT_DROP));
                assertEquals(SCORING.dropPoints(1, engine.getLevel()), engine.getScore() - beforeFeverBlock,
                        "The fever block itself still falls at the normal rate");

                int startsBefore = survival.starts;
                assertTrue(engine.handle(GameAction.HARD_DROP));
                assertEquals(FeverItem.SECONDS, engine.getScoreMultiplierSeconds());
                assertEquals(FeverItem.MULTIPLIER, engine.getScoreMultiplier());
                assertEquals(startsBefore + 1, survival.starts, "The one-second tick restarts when the fever starts");
                assertEquals(1000, survival.interval);
                assertDoubledSoftDrop(engine, true);

                for (int second = 1; second < FeverItem.SECONDS; second++) {
                    int before = engine.getScore();
                    survival.fire();
                    assertEquals(before + FeverItem.MULTIPLIER, engine.getScore(), "survival point is doubled too");
                }
                assertEquals(1, engine.getScoreMultiplierSeconds());
                assertDoubledSoftDrop(engine, true);
                survival.fire();
                assertEquals(0, engine.getScoreMultiplierSeconds());
                assertEquals(1, engine.getScoreMultiplier());
                assertDoubledSoftDrop(engine, false);
            } finally {
                engine.close();
            }
        });
    }

    @Test
    void pausedTimeDoesNotConsumeTheFever() throws Exception {
        Board board = new Board();
        ManualTimer survival = new ManualTimer();
        GameEngine engine = engine(feverDriver(board), survival);
        SwingUtilities.invokeAndWait(() -> {
            try {
                assertTrue(engine.start());
                earnFeverBlock(engine, board);
                assertTrue(engine.handle(GameAction.HARD_DROP));
                survival.fire();
                survival.fire();
                assertEquals(8, engine.getScoreMultiplierSeconds());

                assertTrue(engine.pause());
                assertFalse(survival.running);
                survival.callback.run();
                assertEquals(8, engine.getScoreMultiplierSeconds(), "A queued tick from before the pause is ignored");

                assertTrue(engine.resume());
                assertTrue(survival.running);
                survival.fire();
                assertEquals(7, engine.getScoreMultiplierSeconds());
            } finally {
                engine.close();
            }
        });
    }

    @Test
    void pauseKeepsThePartOfTheSecondAlreadyPlayed() throws Exception {
        Board board = new Board();
        SimulatedTimer survival = new SimulatedTimer();
        GameEngine engine = engine(feverDriver(board), survival);
        SwingUtilities.invokeAndWait(() -> {
            try {
                assertTrue(engine.start());
                earnFeverBlock(engine, board);
                assertTrue(engine.handle(GameAction.HARD_DROP));
                survival.advance(300);
                assertTrue(engine.pause());
                survival.advance(5_000);
                assertTrue(engine.resume());
                assertEquals(700, survival.firstDelay, "Only the unplayed 700 ms remain before the next tick");
                survival.advance(699);
                assertEquals(FeverItem.SECONDS, engine.getScoreMultiplierSeconds());
                survival.advance(1);
                assertEquals(FeverItem.SECONDS - 1, engine.getScoreMultiplierSeconds());
            } finally {
                engine.close();
            }
        });
    }

    @Test
    void pausingRightBeforeEveryTickCannotStretchTheFever() throws Exception {
        Board board = new Board();
        SimulatedTimer survival = new SimulatedTimer();
        GameEngine engine = engine(feverDriver(board), survival);
        SwingUtilities.invokeAndWait(() -> {
            try {
                assertTrue(engine.start());
                earnFeverBlock(engine, board);
                survival.advance(400); // lock part-way through a survival second
                assertTrue(engine.handle(GameAction.HARD_DROP));
                int scoreAtLock = engine.getScore();
                for (int cycle = 0; cycle < 11; cycle++) { // 9.9 s of play, paused 0.9 s into every second
                    survival.advance(900);
                    assertTrue(engine.pause());
                    survival.advance(5_000);
                    assertTrue(engine.resume());
                    assertEquals(FeverItem.MULTIPLIER, engine.getScoreMultiplier());
                }
                assertEquals(1, engine.getScoreMultiplierSeconds(), "Nine of the ten seconds were played");
                survival.advance(99);
                assertEquals(FeverItem.MULTIPLIER, engine.getScoreMultiplier());
                survival.advance(1);
                assertEquals(1, engine.getScoreMultiplier(), "The fever ends after exactly 10 s of play");
                assertEquals(0, engine.getScoreMultiplierSeconds());
                assertEquals(scoreAtLock + 10 * FeverItem.MULTIPLIER, engine.getScore(),
                        "Every played second still earns its doubled survival point");
            } finally {
                engine.close();
            }
        });
    }

    @Test
    void anotherFeverRefreshesTheCountdownInsteadOfStacking() throws Exception {
        Board board = new Board();
        ManualTimer survival = new ManualTimer();
        GameEngine engine = engine(feverDriver(board), survival);
        SwingUtilities.invokeAndWait(() -> {
            try {
                assertTrue(engine.start());
                earnFeverBlock(engine, board);
                assertTrue(engine.handle(GameAction.HARD_DROP));
                for (int second = 0; second < 4; second++) survival.fire();
                assertEquals(6, engine.getScoreMultiplierSeconds());

                earnFeverBlock(engine, board);
                assertTrue(engine.handle(GameAction.HARD_DROP));
                assertEquals(FeverItem.SECONDS, engine.getScoreMultiplierSeconds());
                assertEquals(FeverItem.MULTIPLIER, engine.getScoreMultiplier(), "Fevers do not multiply each other");
            } finally {
                engine.close();
            }
        });
    }

    @Test
    void quittingAndStartingANewGameEndsTheFever() throws Exception {
        Board board = new Board();
        ManualTimer survival = new ManualTimer();
        GameEngine engine = engine(feverDriver(board), survival);
        SwingUtilities.invokeAndWait(() -> {
            try {
                assertTrue(engine.start());
                earnFeverBlock(engine, board);
                assertTrue(engine.handle(GameAction.HARD_DROP));
                assertTrue(engine.stop());
                assertFalse(survival.running);

                assertTrue(engine.start());
                assertEquals(0, engine.getScoreMultiplierSeconds());
                assertEquals(1, engine.getScoreMultiplier());
                assertDoubledSoftDrop(engine, false);
            } finally {
                engine.close();
            }
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
        int expected = SCORING.dropPoints(1, engine.getLevel()) * (doubled ? FeverItem.MULTIPLIER : 1);
        assertEquals(expected, engine.getScore() - before);
    }

    private static GameEngine engine(BlockBoardDriver driver, GameEngine.DropTimer survival) {
        driver.setItemMode(true);
        return new GameEngine(driver, new Events(), SCORING, new DefaultSpeedPolicy(), new ManualTimer(),
                survival, () -> Difficulty.NORMAL);
    }

    private static BlockBoardDriver feverDriver(Board board) {
        Random secondCell = new Random() {
            @Override public int nextInt(int bound) { return TOP_RIGHT; }
        };
        return new BlockBoardDriver(board, () -> TetrominoType.O, secondCell, () -> FEVER);
    }

    private static void fillTwoRowsExceptTheCenter(Board board) {
        for (int col : new int[]{0, 2, 6, 8}) {
            assertTrue(board.tryPlace(new Tetromino(TetrominoType.O, Rotation.SPAWN, 18, col)));
        }
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

        void fire() {
            assertTrue(running, "A stopped Swing timer never fires");
            callback.run();
        }
    }

    /** Survival timer on simulated time: ticks every interval of running time and keeps its progress while stopped. */
    private static final class SimulatedTimer implements GameEngine.DropTimer {
        long now;
        long nextTick;
        long stoppedAt;
        int interval = 1;
        int firstDelay;
        boolean running;
        Runnable callback;

        public void start(int intervalMillis, Runnable tick) {
            start(intervalMillis, intervalMillis, tick);
        }

        public void start(int initialDelayMillis, int intervalMillis, Runnable tick) {
            interval = intervalMillis;
            firstDelay = initialDelayMillis;
            nextTick = now + initialDelayMillis;
            running = true;
            callback = tick;
        }

        public void stop() {
            if (running) stoppedAt = now;
            running = false;
        }

        public int elapsedMillis() {
            long end = running ? now : stoppedAt;
            return (int) Math.max(0, Math.min(interval - 1, end - (nextTick - interval)));
        }

        void advance(long millis) {
            long target = now + millis;
            while (running && nextTick <= target) {
                now = nextTick;
                nextTick += interval;
                callback.run();
            }
            now = target;
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

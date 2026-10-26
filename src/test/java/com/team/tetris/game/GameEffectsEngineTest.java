/*
 * 아이템이 엔진에 요청하는 효과(GameEffects: 점수 배율, 속도 단계 감소)를 실제 BlockBoardDriver 경로로 검증하는 테스트.
 * 활성화 시점, 배율 적용 범위와 카운트다운·일시정지·새 게임, 속도 단계 감소, BoardDriver.lockedItem()을 다룬다.
 */
package com.team.tetris.game;

import com.team.tetris.block.Board;
import com.team.tetris.block.Cell;
import com.team.tetris.block.Rotation;
import com.team.tetris.block.Tetromino;
import com.team.tetris.block.TetrominoType;
import com.team.tetris.common.Difficulty;
import com.team.tetris.common.events.BoardSnapshot;
import com.team.tetris.common.events.CellCodes;
import com.team.tetris.common.events.GameEventListener;
import com.team.tetris.game.item.AnvilItem;
import com.team.tetris.game.item.EmbeddedItem;
import com.team.tetris.game.item.GameEffects;
import com.team.tetris.game.item.Item;
import org.junit.jupiter.api.Test;

import javax.swing.SwingUtilities;
import java.util.Arrays;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;

class GameEffectsEngineTest {
    private static final ScoreCalculator SCORING = new ScoreCalculator();
    private static final Consumer<GameEffects> FEVER = effects -> effects.scoreMultiplier(2, 10);
    private static final Consumer<GameEffects> SLOW_DOWN = effects -> effects.lowerLevel(1);

    private final Board board = new Board();
    private final EffectItem item = new EffectItem();
    private final BlockBoardDriver driver =
            new BlockBoardDriver(board, () -> TetrominoType.O, new Random(1), () -> item);
    private final ManualTimer gravity = new ManualTimer();
    private final ManualTimer survival = new ManualTimer();
    private final GameEngine engine = new GameEngine(driver, new Events(), SCORING, new DefaultSpeedPolicy(),
            gravity, survival, () -> Difficulty.NORMAL);

    GameEffectsEngineTest() {
        driver.setItemMode(true);
    }

    @Test
    void itemActivatesOnlyAfterItsOwnDropAndLinePointsAreAwarded() throws Exception {
        item.effect.set(FEVER);
        AtomicInteger scoreAtActivation = new AtomicInteger(-1);
        item.onActivated = () -> scoreAtActivation.set(engine.getScore());
        SwingUtilities.invokeAndWait(() -> {
            try {
                engine.start();
                earnItem();
                assertEquals(2, engine.getLevel());
                assertEquals(1, engine.getScoreMultiplier(), "Nothing is doubled before the item locks");
                fillTwoRowsExceptTheCenter();
                int before = engine.getScore();
                engine.handle(GameAction.HARD_DROP);

                int ownPoints = SCORING.dropPoints(18, 2) + SCORING.lineClearPoints(2, 2, Difficulty.NORMAL);
                assertEquals(before + ownPoints, engine.getScore(), "The item block's own points are not doubled");
                assertEquals(engine.getScore(), scoreAtActivation.get());
                assertEquals(2, engine.getScoreMultiplier());
                assertEquals(10, engine.getScoreMultiplierSeconds());
            } finally {
                engine.close();
            }
        });
    }

    @Test
    void multiplierDoublesDropsLineClearsAndSurvivalForTenTicks() throws Exception {
        item.effect.set(FEVER);
        SwingUtilities.invokeAndWait(() -> {
            try {
                engine.start();
                earnItem();
                engine.handle(GameAction.HARD_DROP);
                int level = engine.getLevel();

                board.clear();
                fillTwoRowsExceptTheCenter();
                int before = engine.getScore();
                engine.handle(GameAction.HARD_DROP);
                assertEquals(before + 2 * (SCORING.dropPoints(18, level)
                        + SCORING.lineClearPoints(2, level, Difficulty.NORMAL)), engine.getScore());

                before = engine.getScore();
                engine.handle(GameAction.SOFT_DROP);
                assertEquals(before + 2 * SCORING.dropPoints(1, level), engine.getScore());

                for (int second = 1; second <= 10; second++) {
                    before = engine.getScore();
                    survival.fire();
                    assertEquals(before + 2, engine.getScore(), "survival point at second " + second);
                    assertEquals(10 - second, engine.getScoreMultiplierSeconds());
                }
                assertEquals(1, engine.getScoreMultiplier(), "The multiplier ends after ten ticks");
                before = engine.getScore();
                survival.fire();
                assertEquals(before + 1, engine.getScore());
                assertEquals(0, engine.getScoreMultiplierSeconds());

                before = engine.getScore();
                engine.handle(GameAction.HARD_DROP); // One row was already soft-dropped.
                assertEquals(before + SCORING.dropPoints(17, level), engine.getScore());
            } finally {
                engine.close();
            }
        });
    }

    @Test
    void anotherItemRestartsTheCountdownInsteadOfAddingToIt() throws Exception {
        item.effect.set(FEVER);
        SwingUtilities.invokeAndWait(() -> {
            try {
                engine.start();
                earnItem();
                engine.handle(GameAction.HARD_DROP);
                for (int second = 0; second < 4; second++) survival.fire();
                assertEquals(6, engine.getScoreMultiplierSeconds());

                earnItem();
                assertEquals(6, engine.getScoreMultiplierSeconds());
                engine.handle(GameAction.HARD_DROP);
                assertEquals(10, engine.getScoreMultiplierSeconds());
                assertEquals(2, engine.getScoreMultiplier(), "Repeated items do not stack the multiplier");
            } finally {
                engine.close();
            }
        });
    }

    @Test
    void pauseFreezesTheCountdownAndANewGameEndsIt() throws Exception {
        item.effect.set(FEVER);
        SwingUtilities.invokeAndWait(() -> {
            try {
                engine.start();
                earnItem();
                engine.handle(GameAction.HARD_DROP);
                for (int second = 0; second < 3; second++) survival.fire();
                assertEquals(7, engine.getScoreMultiplierSeconds());

                Runnable queuedTick = survival.callback;
                assertTrue(engine.pause());
                int pausedScore = engine.getScore();
                queuedTick.run();
                survival.fire();
                assertEquals(7, engine.getScoreMultiplierSeconds(), "Paused time does not count");
                assertEquals(pausedScore, engine.getScore());

                assertTrue(engine.resume());
                survival.fire();
                assertEquals(6, engine.getScoreMultiplierSeconds());
                assertEquals(pausedScore + 2, engine.getScore());

                assertTrue(engine.stop());
                assertTrue(engine.start());
                assertEquals(1, engine.getScoreMultiplier());
                assertEquals(0, engine.getScoreMultiplierSeconds());
                survival.fire();
                assertEquals(1, engine.getScore());
            } finally {
                engine.close();
            }
        });
    }

    @Test
    void lowerLevelDropsOneLevelAndUsesThatLevelsInterval() throws Exception {
        item.effect.set(SLOW_DOWN);
        SwingUtilities.invokeAndWait(() -> {
            try {
                engine.start();
                earnItem();
                assertEquals(2, engine.getLevel());
                assertEquals(800, engine.getDropIntervalMillis());
                engine.handle(GameAction.HARD_DROP);

                assertEquals(1, engine.getLevel());
                assertEquals(1, engine.getSlowdownSteps());
                assertEquals(1000, engine.getDropIntervalMillis());
                assertEquals(1000, gravity.interval, "The next block falls at the lowered speed");

                for (int round = 0; round < 5; round++) clearTwoLines();
                assertEquals(20, engine.getClearedLines());
                assertEquals(2, engine.getLevel(), "The item keeps the level one below its natural value");
                assertEquals(800, gravity.interval);
            } finally {
                engine.close();
            }
        });
    }

    @Test
    void lowerLevelAtLevelOneChangesNothingAndIsNotSavedForLater() throws Exception {
        // The first call takes level 2 down to 1; the second arrives at level 1 and must be dropped.
        item.effect.set(SLOW_DOWN.andThen(SLOW_DOWN));
        SwingUtilities.invokeAndWait(() -> {
            try {
                engine.start();
                earnItem();
                engine.handle(GameAction.HARD_DROP);
                assertEquals(1, engine.getLevel());
                assertEquals(1, engine.getSlowdownSteps(), "Nothing is stored at level 1");

                item.effect.set(effects -> { });
                for (int round = 0; round < 5; round++) clearTwoLines();
                assertEquals(2, engine.getLevel(), "A level-up later is lowered by the one used step only");
            } finally {
                engine.close();
            }
        });
    }

    @Test
    void lowerLevelAtTheTopLevelGoesToNineAndANewGameForgetsIt() throws Exception {
        item.effect.set(effects -> { });
        SwingUtilities.invokeAndWait(() -> {
            try {
                engine.start();
                for (int milestone = 1; milestone < 9; milestone++) {
                    earnItem();
                    engine.handle(GameAction.HARD_DROP);
                }
                earnItem();
                assertEquals(10, engine.getLevel());
                item.effect.set(SLOW_DOWN);
                engine.handle(GameAction.HARD_DROP);
                assertEquals(9, engine.getLevel());
                assertEquals(new DefaultSpeedPolicy().calculate(0, 80, Difficulty.NORMAL).dropIntervalMillis(),
                        engine.getDropIntervalMillis());

                assertTrue(engine.stop());
                assertTrue(engine.start());
                assertEquals(0, engine.getSlowdownSteps());
                assertEquals(1, engine.getLevel());
                item.effect.set(effects -> { });
                for (int round = 0; round < 5; round++) clearTwoLines();
                assertEquals(2, engine.getLevel(), "The new game's level-up is not lowered");
            } finally {
                engine.close();
            }
        });
    }

    @Test
    void boardDriverReportsNoLockedItemByDefault() {
        GameEngine.BoardDriver minimal = new GameEngine.BoardDriver() {
            public void reset() { }
            public boolean spawnNextBlock() { return true; }
            public boolean moveLeft() { return false; }
            public boolean moveRight() { return false; }
            public boolean moveDown() { return false; }
            public boolean rotateClockwise() { return false; }
            public int hardDrop() { return 0; }
            public GameEngine.LockResult lockAndClearLines() { return new GameEngine.LockResult(0, false); }
            public BoardSnapshot snapshot() { return null; }
            public int[] nextBlockTypes() { return new int[0]; }
        };
        assertNull(minimal.lockedItem());
    }

    @Test
    void blockBoardDriverReportsTheItemItJustLocked() {
        assertNull(driver.lockedItem());
        driver.reset();
        earnItemOnTheDriver(driver, board);
        driver.hardDrop();
        driver.lockAndClearLines();
        assertSame(item, driver.lockedItem());

        assertTrue(driver.spawnNextBlock());
        driver.hardDrop();
        driver.lockAndClearLines();
        assertNull(driver.lockedItem(), "A regular block carries no item");

        earnItemOnTheDriver(driver, board);
        driver.hardDrop();
        driver.lockAndClearLines();
        assertSame(item, driver.lockedItem());
        driver.reset();
        assertNull(driver.lockedItem(), "A new game forgets the last locked item");
    }

    @Test
    void standaloneItemIsReportedWhenItIsConsumed() {
        Item anvil = new AnvilItem();
        Board anvilBoard = new Board();
        BlockBoardDriver anvilDriver = new BlockBoardDriver(anvilBoard, () -> TetrominoType.O, new Random(1),
                () -> anvil);
        anvilDriver.setItemMode(true);
        anvilDriver.reset();
        earnItemOnTheDriver(anvilDriver, anvilBoard);
        anvilDriver.hardDrop();
        anvilDriver.lockAndClearLines();
        assertSame(anvil, anvilDriver.lockedItem());
    }

    /**
     * Five two-line clears earn the item. The regular block queued before the milestone falls next,
     * and the item piece is left falling over an empty board.
     */
    private void earnItem() {
        for (int round = 0; round < 5; round++) clearTwoLines();
        board.clear();
        engine.handle(GameAction.HARD_DROP);
        board.clear();
        assertTrue(Arrays.stream(driver.snapshot().cells()).flatMapToInt(Arrays::stream)
                .anyMatch(code -> code != BoardSnapshot.EMPTY && CellCodes.symbol(code) == item.symbol()),
                "the earned item is falling");
    }

    private void clearTwoLines() {
        board.clear();
        fillTwoRowsExceptTheCenter();
        int lines = engine.getClearedLines();
        engine.handle(GameAction.HARD_DROP);
        assertEquals(lines + 2, engine.getClearedLines());
    }

    /** Driver-only version of {@link #earnItem()}: leaves the earned item piece falling. */
    private static void earnItemOnTheDriver(BlockBoardDriver target, Board targetBoard) {
        for (int round = 0; round < 5; round++) {
            assertTrue(target.spawnNextBlock());
            targetBoard.clear();
            for (int col : new int[]{0, 2, 6, 8}) placeSquare(targetBoard, 18, col);
            target.hardDrop();
            assertEquals(2, target.lockAndClearLines().clearedLines());
        }
        assertTrue(target.spawnNextBlock());
        targetBoard.clear();
        target.hardDrop();
        target.lockAndClearLines();
        targetBoard.clear();
        assertTrue(target.spawnNextBlock());
    }

    private void fillTwoRowsExceptTheCenter() {
        for (int col : new int[]{0, 2, 6, 8}) placeSquare(board, 18, col);
    }

    private static void placeSquare(Board target, int row, int col) {
        assertTrue(target.tryPlace(new Tetromino(TetrominoType.O, Rotation.SPAWN, row, col)));
    }

    /** An embedded item whose engine effect each test chooses. */
    private static final class EffectItem implements EmbeddedItem {
        final AtomicReference<Consumer<GameEffects>> effect = new AtomicReference<>(effects -> { });
        Runnable onActivated = () -> { };

        @Override public String name() { return "TEST_EFFECT"; }
        @Override public char symbol() { return 'E'; }
        @Override public Set<Integer> onLock(Board board, Cell itemCell) { return Set.of(); }

        @Override
        public void onActivated(GameEffects effects) {
            onActivated.run();
            effect.get().accept(effects);
        }
    }

    private static final class ManualTimer implements GameEngine.DropTimer {
        int interval;
        boolean running;
        Runnable callback;
        public void start(int intervalMillis, Runnable tick) {
            interval = intervalMillis;
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
        public void onGameOver(int finalScore) { }
    }
}

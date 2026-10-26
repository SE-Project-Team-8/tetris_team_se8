/*
 * SlowDownItem(S) 테스트. 효과 요청(lowerLevel(1))과 보드 무변경, 실제 BlockBoardDriver·GameEngine 경로의
 * S 칸 표시, 레벨·낙하 간격 감소와 유지, 레벨 1에서 효과 없음(보관 안 함), 새 게임 초기화를 검증한다.
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
import com.team.tetris.game.SpeedPolicy;
import org.junit.jupiter.api.Test;

import javax.swing.SwingUtilities;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class SlowDownItemTest {
    private static final SlowDownItem SLOW_DOWN = new SlowDownItem();
    /** O block cell order is (0,0), (0,1), (1,0), (1,1); index 0 is the top-left cell. */
    private static final int TOP_LEFT = 0;

    @Test
    void asksToLowerOneLevelAndLeavesTheBoardAlone() {
        assertEquals('S', SLOW_DOWN.symbol());
        assertEquals("SLOW_DOWN", SLOW_DOWN.name());
        Board board = new Board();
        assertTrue(board.tryPlace(new Tetromino(TetrominoType.O, Rotation.SPAWN, 18, 4)));
        assertEquals(Set.of(), SLOW_DOWN.onLock(board, new Cell(18, 4)));
        assertEquals(TetrominoType.O, board.cellAt(18, 4).orElseThrow(), "S erases nothing");

        List<String> requests = new ArrayList<>();
        SLOW_DOWN.onActivated(new GameEffects() {
            public void scoreMultiplier(int multiplier, int seconds) { requests.add(multiplier + "x" + seconds); }
            public void lowerLevel(int steps) { requests.add("lower" + steps); }
        });
        assertEquals(List.of("lower1"), requests);
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
        assertArrayEquals(new int[]{CellCodes.preview(TetrominoType.O.id(), SlowDownItem.SYMBOL, TOP_LEFT)},
                driver.nextBlockTypes());
        driver.hardDrop();
        driver.lockAndClearLines();
        assertTrue(driver.spawnNextBlock());
        assertEquals(CellCodes.cell(TetrominoType.O.id(), SlowDownItem.SYMBOL), driver.snapshot().cells()[0][4]);
        driver.hardDrop();
        assertEquals(0, driver.lockAndClearLines().clearedLines(), "S never deletes a row by itself");
        assertSame(SLOW_DOWN, driver.lockedItem());
    }

    @Test
    void lockingTheSlowDownBlockLowersTheCurrentLevelForTheRestOfTheGame() throws Exception {
        Board board = new Board();
        GameEngine engine = engine(slowDownDriver(board), new DefaultSpeedPolicy());
        SwingUtilities.invokeAndWait(() -> {
            try {
                assertTrue(engine.start());
                earnSlowDownBlock(engine, board);
                assertEquals(2, engine.getLevel(), "Ten cleared lines reached level 2");
                assertEquals(800, engine.getDropIntervalMillis());

                int scoreBefore = engine.getScore();
                assertTrue(engine.handle(GameAction.HARD_DROP));
                assertEquals(1, engine.getLevel());
                assertEquals(1000, engine.getDropIntervalMillis(), "The next block falls at the slower speed");
                assertTrue(engine.getScore() > scoreBefore, "S itself still earns its own drop points");

                earnSlowDownBlock(engine, board);
                assertEquals(20, engine.getClearedLines());
                assertEquals(2, engine.getLevel(), "Level 3 by progress, one step lower by the earlier item");
                assertEquals(800, engine.getDropIntervalMillis());

                assertTrue(engine.handle(GameAction.HARD_DROP));
                assertEquals(1, engine.getLevel(), "A second item lowers one more step");
                assertEquals(2, engine.getSlowdownSteps());
            } finally {
                engine.close();
            }
        });
    }

    @Test
    void slowDownAtLevelOneIsNotSavedForALaterLevel() throws Exception {
        Board board = new Board();
        // Level 1 until twenty lines, then level 3; slow-down steps lower that level.
        SpeedPolicy policy = new SpeedPolicy() {
            public Speed calculate(int blocks, int lines) { return calculate(blocks, lines, Difficulty.NORMAL, 0); }
            public Speed calculate(int blocks, int lines, Difficulty difficulty, int steps) {
                int level = Math.max(1, (lines >= 20 ? 3 : 1) - steps);
                return new Speed(level, 1100 - level * 100);
            }
        };
        GameEngine engine = engine(slowDownDriver(board), policy);
        SwingUtilities.invokeAndWait(() -> {
            try {
                assertTrue(engine.start());
                earnSlowDownBlock(engine, board);
                assertEquals(1, engine.getLevel());
                assertTrue(engine.handle(GameAction.HARD_DROP));
                assertEquals(1, engine.getLevel());
                assertEquals(0, engine.getSlowdownSteps());

                earnSlowDownBlock(engine, board);
                assertEquals(3, engine.getLevel(), "The unused slow-down did not lower the later level");
            } finally {
                engine.close();
            }
        });
    }

    @Test
    void newGameForgetsEarlierSlowDowns() throws Exception {
        Board board = new Board();
        GameEngine engine = engine(slowDownDriver(board), new DefaultSpeedPolicy());
        SwingUtilities.invokeAndWait(() -> {
            try {
                assertTrue(engine.start());
                earnSlowDownBlock(engine, board);
                assertTrue(engine.handle(GameAction.HARD_DROP));
                assertEquals(1, engine.getLevel());
                assertTrue(engine.stop());

                assertTrue(engine.start());
                assertEquals(0, engine.getSlowdownSteps());
                earnSlowDownBlock(engine, board);
                assertEquals(2, engine.getLevel(), "Ten cleared lines reach level 2 again");
            } finally {
                engine.close();
            }
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
        return new GameEngine(driver, new Events(), new ScoreCalculator(), policy, new IdleTimer(), new IdleTimer(),
                () -> Difficulty.NORMAL);
    }

    private static BlockBoardDriver slowDownDriver(Board board) {
        Random firstCell = new Random() {
            @Override public int nextInt(int bound) { return TOP_LEFT; }
        };
        return new BlockBoardDriver(board, () -> TetrominoType.O, firstCell, () -> SLOW_DOWN);
    }

    private static void fillTwoRowsExceptTheCenter(Board board) {
        for (int col : new int[]{0, 2, 6, 8}) {
            assertTrue(board.tryPlace(new Tetromino(TetrominoType.O, Rotation.SPAWN, 18, col)));
        }
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

package com.team.tetris.game;

import org.junit.jupiter.api.Test;
import com.team.tetris.common.Difficulty;

import static org.junit.jupiter.api.Assertions.*;

class DefaultSpeedPolicyTest {
    private final DefaultSpeedPolicy policy = new DefaultSpeedPolicy();

    @Test
    void everyTenClearedLinesSpeedUpByTheDifficultysStep() {
        assertEquals(840, policy.calculate(0, 10, Difficulty.EASY).dropIntervalMillis());
        assertEquals(800, policy.calculate(0, 10, Difficulty.NORMAL).dropIntervalMillis());
        assertEquals(760, policy.calculate(0, 10, Difficulty.HARD).dropIntervalMillis());
        assertEquals(680, policy.calculate(0, 20, Difficulty.EASY).dropIntervalMillis());
        assertEquals(600, policy.calculate(0, 20, Difficulty.NORMAL).dropIntervalMillis());
        assertEquals(520, policy.calculate(0, 20, Difficulty.HARD).dropIntervalMillis());
        for (Difficulty difficulty : Difficulty.values()) {
            assertEquals(new SpeedPolicy.Speed(1, 1000), policy.calculate(0, 0, difficulty));
            assertEquals(new SpeedPolicy.Speed(1, 1000), policy.calculate(0, 9, difficulty));
        }
    }

    @Test
    void placedBlocksDoNotChangeTheSpeedSoLineClearsDecideIt() {
        // Requirements 2 (2.1) ties the speed-up to line clears. A block counter used to outweigh the
        // line counter in play, so clearing lines never changed the speed.
        for (Difficulty difficulty : Difficulty.values()) {
            assertEquals(new SpeedPolicy.Speed(1, 1000), policy.calculate(95, 0, difficulty));
            assertEquals(new SpeedPolicy.Speed(1, 1000), policy.calculate(Integer.MAX_VALUE, 0, difficulty));
        }
        assertEquals(new SpeedPolicy.Speed(2, 840), policy.calculate(30, 10, Difficulty.EASY));
        assertEquals(new SpeedPolicy.Speed(2, 800), policy.calculate(30, 10, Difficulty.NORMAL));
        assertEquals(new SpeedPolicy.Speed(2, 760), policy.calculate(30, 10, Difficulty.HARD));
    }

    @Test
    void difficultiesStayApartAtTheFastestSpeed() {
        // With one shared 100ms floor, Hard caught up with Normal at the top level.
        assertEquals(new SpeedPolicy.Speed(10, 120), policy.calculate(0, 90, Difficulty.EASY));
        assertEquals(new SpeedPolicy.Speed(10, 100), policy.calculate(0, 90, Difficulty.NORMAL));
        assertEquals(new SpeedPolicy.Speed(10, 80), policy.calculate(0, 90, Difficulty.HARD));
        assertEquals(new SpeedPolicy.Speed(5, 80), policy.calculate(0, 40, Difficulty.HARD));
        assertEquals(new SpeedPolicy.Speed(6, 100), policy.calculate(0, 50, Difficulty.NORMAL));
        assertEquals(new SpeedPolicy.Speed(7, 120), policy.calculate(0, 60, Difficulty.EASY));
        assertEquals(120, policy.calculate(0, Integer.MAX_VALUE, Difficulty.EASY).dropIntervalMillis());
    }

    @Test
    void defaultDifficultyIsNormal() {
        assertEquals(new SpeedPolicy.Speed(1, 1000), policy.calculate(0, 0));
        assertEquals(new SpeedPolicy.Speed(1, 1000), policy.calculate(10, 9));
        assertEquals(new SpeedPolicy.Speed(2, 800), policy.calculate(0, 10));
        assertEquals(new SpeedPolicy.Speed(10, 100), policy.calculate(0, Integer.MAX_VALUE));
    }

    @Test
    void rejectsInvalidProgressOrSpeed() {
        assertThrows(IllegalArgumentException.class, () -> policy.calculate(-1, 0));
        assertThrows(IllegalArgumentException.class, () -> policy.calculate(0, -1));
        assertThrows(IllegalArgumentException.class, () -> new SpeedPolicy.Speed(0, 100));
        assertThrows(IllegalArgumentException.class, () -> new SpeedPolicy.Speed(1, 0));
    }
}

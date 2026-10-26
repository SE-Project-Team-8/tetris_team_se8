package com.team.tetris.game;

import org.junit.jupiter.api.Test;
import com.team.tetris.common.Difficulty;

import static org.junit.jupiter.api.Assertions.*;

class DefaultSpeedPolicyTest {
    private final DefaultSpeedPolicy policy = new DefaultSpeedPolicy();

    @Test
    void lineAndBlockAccelerationVaryWhileInitialGravityRemainsStable() {
        assertEquals(840, policy.calculate(0, 10, Difficulty.EASY).dropIntervalMillis());
        assertEquals(800, policy.calculate(0, 10, Difficulty.NORMAL).dropIntervalMillis());
        assertEquals(760, policy.calculate(0, 10, Difficulty.HARD).dropIntervalMillis());
        assertEquals(680, policy.calculate(0, 20, Difficulty.EASY).dropIntervalMillis());
        assertEquals(600, policy.calculate(0, 20, Difficulty.NORMAL).dropIntervalMillis());
        assertEquals(520, policy.calculate(0, 20, Difficulty.HARD).dropIntervalMillis());
        assertEquals(80, policy.calculate(0, 40, Difficulty.HARD).dropIntervalMillis());
        assertEquals(120, policy.calculate(0, 60, Difficulty.EASY).dropIntervalMillis());
        for (Difficulty difficulty : Difficulty.values()) {
            assertEquals(new SpeedPolicy.Speed(1, 1000), policy.calculate(0, 0, difficulty));
            int floor = switch (difficulty) {
                case EASY -> 120;
                case NORMAL -> 100;
                case HARD -> 80;
            };
            assertEquals(floor, policy.calculate(Integer.MAX_VALUE, Integer.MAX_VALUE, difficulty).dropIntervalMillis());
        }
        assertEquals(920, policy.calculate(10, 0, Difficulty.EASY).dropIntervalMillis());
        assertEquals(900, policy.calculate(10, 0, Difficulty.NORMAL).dropIntervalMillis());
        assertEquals(880, policy.calculate(10, 0, Difficulty.HARD).dropIntervalMillis());
        assertEquals(120, policy.calculate(0, Integer.MAX_VALUE, Difficulty.EASY).dropIntervalMillis());
    }

    @Test
    void difficultiesStayApartAtTheFastestSpeed() {
        // With one shared 100ms floor, Hard caught up with Normal at the top level.
        assertEquals(new SpeedPolicy.Speed(10, 120), policy.calculate(90, 90, Difficulty.EASY));
        assertEquals(new SpeedPolicy.Speed(10, 100), policy.calculate(90, 90, Difficulty.NORMAL));
        assertEquals(new SpeedPolicy.Speed(10, 80), policy.calculate(90, 90, Difficulty.HARD));
        assertEquals(new SpeedPolicy.Speed(10, 100), policy.calculate(90, 0, Difficulty.NORMAL));
        assertEquals(new SpeedPolicy.Speed(10, 80), policy.calculate(90, 0, Difficulty.HARD));
    }

    @Test
    void difficultiesStayApartWhenTheBlockCounterDecidesTheSpeed() {
        // Clearing a line takes at least 2.5 blocks, so the block counter decides the speed in play.
        // Before block steps used the difficulty, all three difficulties dropped every 700ms here.
        assertEquals(760, policy.calculate(30, 10, Difficulty.EASY).dropIntervalMillis());
        assertEquals(700, policy.calculate(30, 10, Difficulty.NORMAL).dropIntervalMillis());
        assertEquals(640, policy.calculate(30, 10, Difficulty.HARD).dropIntervalMillis());
        assertEquals(600, policy.calculate(50, 20, Difficulty.EASY).dropIntervalMillis());
        assertEquals(500, policy.calculate(50, 20, Difficulty.NORMAL).dropIntervalMillis());
        assertEquals(400, policy.calculate(50, 20, Difficulty.HARD).dropIntervalMillis());
    }

    @Test
    void independentCountersAndSpeedFloor() {
        assertEquals(new SpeedPolicy.Speed(1, 1000), policy.calculate(0, 0));
        assertEquals(new SpeedPolicy.Speed(1, 1000), policy.calculate(9, 9));
        assertEquals(new SpeedPolicy.Speed(2, 900), policy.calculate(10, 0));
        assertEquals(new SpeedPolicy.Speed(2, 800), policy.calculate(0, 10));
        assertEquals(new SpeedPolicy.Speed(2, 800), policy.calculate(10, 10));
        assertEquals(new SpeedPolicy.Speed(10, 100), policy.calculate(Integer.MAX_VALUE, 0));
    }

    @Test
    void rejectsInvalidProgressOrSpeed() {
        assertThrows(IllegalArgumentException.class, () -> policy.calculate(-1, 0));
        assertThrows(IllegalArgumentException.class, () -> policy.calculate(0, -1));
        assertThrows(IllegalArgumentException.class, () -> new SpeedPolicy.Speed(0, 100));
        assertThrows(IllegalArgumentException.class, () -> new SpeedPolicy.Speed(1, 0));
    }
}

package com.team.tetris.game;

import org.junit.jupiter.api.Test;
import com.team.tetris.common.Difficulty;

import static org.junit.jupiter.api.Assertions.*;

class DefaultSpeedPolicyTest {
    private final DefaultSpeedPolicy policy = new DefaultSpeedPolicy();

    @Test
    void lineAccelerationVariesWhileInitialGravityAndBlockAccelerationRemainStable() {
        assertEquals(840, policy.calculate(0, 10, Difficulty.EASY).dropIntervalMillis());
        assertEquals(800, policy.calculate(0, 10, Difficulty.NORMAL).dropIntervalMillis());
        assertEquals(760, policy.calculate(0, 10, Difficulty.HARD).dropIntervalMillis());
        assertEquals(680, policy.calculate(0, 20, Difficulty.EASY).dropIntervalMillis());
        assertEquals(600, policy.calculate(0, 20, Difficulty.NORMAL).dropIntervalMillis());
        assertEquals(520, policy.calculate(0, 20, Difficulty.HARD).dropIntervalMillis());
        assertEquals(100, policy.calculate(0, 40, Difficulty.HARD).dropIntervalMillis());
        assertEquals(100, policy.calculate(0, 60, Difficulty.EASY).dropIntervalMillis());
        for (Difficulty difficulty : Difficulty.values()) {
            assertEquals(new SpeedPolicy.Speed(1, 1000), policy.calculate(0, 0, difficulty));
            assertEquals(900, policy.calculate(10, 0, difficulty).dropIntervalMillis());
            assertEquals(100, policy.calculate(Integer.MAX_VALUE, Integer.MAX_VALUE, difficulty).dropIntervalMillis());
        }
        assertEquals(100, policy.calculate(0, Integer.MAX_VALUE, Difficulty.EASY).dropIntervalMillis());
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

package com.team.tetris.game;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class DefaultSpeedPolicyTest {
    private final DefaultSpeedPolicy policy = new DefaultSpeedPolicy();

    @Test
    void independentCountersAndSpeedFloor() {
        assertEquals(new SpeedPolicy.Speed(1, 1000), policy.calculate(0, 0));
        assertEquals(new SpeedPolicy.Speed(1, 1000), policy.calculate(9, 9));
        assertEquals(new SpeedPolicy.Speed(2, 900), policy.calculate(10, 0));
        assertEquals(new SpeedPolicy.Speed(2, 900), policy.calculate(0, 10));
        assertEquals(new SpeedPolicy.Speed(2, 900), policy.calculate(10, 10));
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

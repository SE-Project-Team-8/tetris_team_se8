package com.team.tetris.game;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.*;

class ScoreCalculatorTest {
    private final ScoreCalculator scoring = new ScoreCalculator();

    @ParameterizedTest
    @CsvSource({"0,1,0", "1,1,1", "18,1,18", "1,2,2", "18,10,180"})
    void everyActualCellEarnsBaseAndSpeedBonus(int cells, int level, int expected) {
        assertEquals(expected, scoring.dropPoints(cells, level));
    }

    @ParameterizedTest
    @CsvSource({"0,1,0", "1,1,100", "2,1,300", "3,1,500", "4,1,800", "4,3,2400"})
    void lineClearingIsAnAdditionalScoringRule(int lines, int level, int expected) {
        assertEquals(expected, scoring.lineClearPoints(lines, level));
    }

    @Test
    void overflowNeverMakesAScoreNegative() {
        assertEquals(Integer.MAX_VALUE, scoring.dropPoints(Integer.MAX_VALUE, 10));
        assertEquals(Integer.MAX_VALUE, scoring.lineClearPoints(4, Integer.MAX_VALUE));
        assertEquals(Integer.MAX_VALUE, scoring.add(Integer.MAX_VALUE - 1, 10));
    }

    @Test
    void rejectsInvalidInputs() {
        assertThrows(IllegalArgumentException.class, () -> scoring.dropPoints(-1, 1));
        assertThrows(IllegalArgumentException.class, () -> scoring.dropPoints(1, 0));
        assertThrows(IllegalArgumentException.class, () -> scoring.lineClearPoints(5, 1));
        assertThrows(IllegalArgumentException.class, () -> scoring.lineClearPoints(-1, 1));
        assertThrows(IllegalArgumentException.class, () -> scoring.lineClearPoints(1, 0));
        assertThrows(IllegalArgumentException.class, () -> scoring.add(-1, 2));
        assertThrows(IllegalArgumentException.class, () -> scoring.add(1, -2));
    }
}

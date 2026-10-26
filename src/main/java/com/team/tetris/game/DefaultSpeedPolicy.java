package com.team.tetris.game;

import com.team.tetris.common.constants.GameConstants;
import com.team.tetris.common.Difficulty;

import java.util.Objects;

/**
 * Faster every ten cleared lines, by the difficulty's step, down to the difficulty's floor.
 * Placed blocks do not count: Requirements 2 (2.1) ties the speed-up to line clears.
 */
public final class DefaultSpeedPolicy implements SpeedPolicy {
    public static final int LINES_PER_LEVEL = 10;
    public static final int MAX_LEVEL = 10;

    @Override
    public Speed calculate(int generatedBlocks, int clearedLines) {
        return calculate(generatedBlocks, clearedLines, Difficulty.NORMAL);
    }

    @Override
    public Speed calculate(int generatedBlocks, int clearedLines, Difficulty difficulty) {
        Objects.requireNonNull(difficulty, "difficulty");
        if (generatedBlocks < 0 || clearedLines < 0) {
            throw new IllegalArgumentException("Progress counters must be non-negative");
        }
        int lineSteps = Math.min(MAX_LEVEL - 1, clearedLines / LINES_PER_LEVEL);
        int level = 1 + lineSteps;
        int reduction = lineSteps * difficulty.lineSpeedStepMillis();
        int interval = Math.max(difficulty.minDropIntervalMillis(),
                (int) GameConstants.INITIAL_DROP_INTERVAL_MS - reduction);
        return new Speed(level, interval);
    }
}

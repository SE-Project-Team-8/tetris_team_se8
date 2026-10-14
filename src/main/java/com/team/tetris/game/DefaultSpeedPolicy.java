package com.team.tetris.game;

import com.team.tetris.common.constants.GameConstants;
import com.team.tetris.common.Difficulty;

import java.util.Objects;

/** First-stage rules: faster every ten generated blocks or cleared lines. */
public final class DefaultSpeedPolicy implements SpeedPolicy {
    public static final int BLOCKS_PER_LEVEL = 10;
    public static final int LINES_PER_LEVEL = 10;
    public static final int MAX_LEVEL = 10;
    public static final int MIN_DROP_INTERVAL_MS = 100;
    public static final int SPEED_STEP_MS = 100;

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
        int blockSteps = Math.min(MAX_LEVEL - 1, generatedBlocks / BLOCKS_PER_LEVEL);
        int lineSteps = Math.min(MAX_LEVEL - 1, clearedLines / LINES_PER_LEVEL);
        int level = 1 + Math.max(blockSteps, lineSteps);
        int reduction = Math.max(blockSteps * SPEED_STEP_MS, lineSteps * difficulty.lineSpeedStepMillis());
        int interval = Math.max(MIN_DROP_INTERVAL_MS,
                (int) GameConstants.INITIAL_DROP_INTERVAL_MS - reduction);
        return new Speed(level, interval);
    }
}

package com.team.tetris.game;

import com.team.tetris.common.constants.GameConstants;

/** First-stage rules: faster every ten generated blocks or cleared lines. */
public final class DefaultSpeedPolicy implements SpeedPolicy {
    public static final int BLOCKS_PER_LEVEL = 10;
    public static final int LINES_PER_LEVEL = 10;
    public static final int MAX_LEVEL = 10;
    public static final int MIN_DROP_INTERVAL_MS = 100;
    public static final int SPEED_STEP_MS = 100;

    @Override
    public Speed calculate(int generatedBlocks, int clearedLines) {
        if (generatedBlocks < 0 || clearedLines < 0) {
            throw new IllegalArgumentException("Progress counters must be non-negative");
        }
        int level = Math.min(MAX_LEVEL, 1 + Math.max(
                generatedBlocks / BLOCKS_PER_LEVEL, clearedLines / LINES_PER_LEVEL));
        int interval = Math.max(MIN_DROP_INTERVAL_MS,
                (int) GameConstants.INITIAL_DROP_INTERVAL_MS - (level - 1) * SPEED_STEP_MS);
        return new Speed(level, interval);
    }
}

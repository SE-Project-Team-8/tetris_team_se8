package com.team.tetris.game;

/** Computes gravity from game progress; it does not own or start a timer. */
@FunctionalInterface
public interface SpeedPolicy {
    Speed calculate(int generatedBlocks, int clearedLines);

    record Speed(int level, int dropIntervalMillis) {
        public Speed {
            if (level < 1 || dropIntervalMillis < 1) {
                throw new IllegalArgumentException("Level and drop interval must be positive");
            }
        }
    }
}

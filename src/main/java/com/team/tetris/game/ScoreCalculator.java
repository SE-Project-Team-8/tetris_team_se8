package com.team.tetris.game;

/** Pure scoring policy. Automatic, soft and hard drops use the same rule. */
public final class ScoreCalculator {
    private static final int[] LINE_POINTS = {0, 100, 300, 500, 800};

    /** One base point per actual cell, plus level - 1 speed bonus per cell. */
    public int dropPoints(int cells, int level) {
        if (cells < 0) throw new IllegalArgumentException("cells must be non-negative");
        requireLevel(level);
        return saturate((long) cells * level);
    }

    /** Extra scoring rule: reward clearing multiple lines in one placement. */
    public int lineClearPoints(int lines, int level) {
        if (lines < 0 || lines >= LINE_POINTS.length) {
            throw new IllegalArgumentException("lines must be between 0 and 4");
        }
        requireLevel(level);
        return saturate((long) LINE_POINTS[lines] * level);
    }

    public int add(int score, int points) {
        if (score < 0 || points < 0) throw new IllegalArgumentException("negative score");
        return saturate((long) score + points);
    }

    private static int saturate(long value) {
        return (int) Math.min(Integer.MAX_VALUE, value);
    }

    private static void requireLevel(int level) {
        if (level < 1) throw new IllegalArgumentException("level must be positive");
    }
}

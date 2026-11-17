package com.team.tetris.game;

import com.team.tetris.common.Difficulty;

/** A scoring rule can be replaced without changing the game loop. */
public interface ScoringPolicy {
    int dropPoints(int cells, int level);

    int lineClearPoints(int lines, int level);

    /** Existing custom policies keep their original rule unless they opt into difficulty. */
    default int lineClearPoints(int lines, int level, Difficulty difficulty) {
        return lineClearPoints(lines, level);
    }

    /** Adds awarded points without overflowing the score exposed to the UI; zero is never awarded. */
    int add(int score, int points);
}

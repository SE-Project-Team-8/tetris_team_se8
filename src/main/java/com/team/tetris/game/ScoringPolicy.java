package com.team.tetris.game;

/** A scoring rule can be replaced without changing the game loop. */
public interface ScoringPolicy {
    int dropPoints(int cells, int level);

    int lineClearPoints(int lines, int level);

    /** Adds awarded points without overflowing the score exposed to the UI; zero is never awarded. */
    int add(int score, int points);
}

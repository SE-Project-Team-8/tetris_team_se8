package com.team.tetris.scoreboard;

import com.team.tetris.common.Difficulty;
import com.team.tetris.common.GameMode;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** The ID identifies the exact row to highlight even when names/scores are equal. */
public record ScoreRecord(UUID id, String name, int score, Instant recordedAt, Difficulty difficulty,
                          GameMode mode) {
    public static final int MAX_NAME_LENGTH = 20;

    public ScoreRecord {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(recordedAt, "recordedAt");
        Objects.requireNonNull(difficulty, "difficulty");
        Objects.requireNonNull(mode, "mode");
        name = name.strip();
        if (name.isEmpty() || name.codePointCount(0, name.length()) > MAX_NAME_LENGTH
                || name.codePoints().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException("Name must contain 1 to 20 characters, without control characters");
        }
        if (score < 0) throw new IllegalArgumentException("score must be non-negative");
    }

    public ScoreRecord(String name, int score) {
        this(name, score, Difficulty.NORMAL);
    }

    public ScoreRecord(UUID id, String name, int score, Instant recordedAt) {
        this(id, name, score, recordedAt, Difficulty.NORMAL);
    }

    /** Records from before item mode existed were all played in the normal mode. */
    public ScoreRecord(UUID id, String name, int score, Instant recordedAt, Difficulty difficulty) {
        this(id, name, score, recordedAt, difficulty, GameMode.NORMAL);
    }

    public ScoreRecord(String name, int score, Difficulty difficulty) {
        this(name, score, difficulty, GameMode.NORMAL);
    }

    public ScoreRecord(String name, int score, Difficulty difficulty, GameMode mode) {
        this(UUID.randomUUID(), name, score, Instant.now(), difficulty, mode);
    }
}

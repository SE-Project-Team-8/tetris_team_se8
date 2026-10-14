package com.team.tetris.scoreboard;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** The ID identifies the exact row to highlight even when names/scores are equal. */
public record ScoreRecord(UUID id, String name, int score, Instant recordedAt, ScoreDifficulty difficulty) {
    public static final int MAX_NAME_LENGTH = 20;

    public ScoreRecord {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(recordedAt, "recordedAt");
        Objects.requireNonNull(difficulty, "difficulty");
        name = name.strip();
        if (name.isEmpty() || name.codePointCount(0, name.length()) > MAX_NAME_LENGTH
                || name.codePoints().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException("Name must contain 1 to 20 characters, without control characters");
        }
        if (score < 0) throw new IllegalArgumentException("score must be non-negative");
    }

    /** Existing records without a difficulty belong to the default Normal ranking. */
    public ScoreRecord(UUID id, String name, int score, Instant recordedAt) {
        this(id, name, score, recordedAt, ScoreDifficulty.NORMAL);
    }

    public ScoreRecord(String name, int score) {
        this(UUID.randomUUID(), name, score, Instant.now(), ScoreDifficulty.NORMAL);
    }

    public ScoreRecord(String name, int score, ScoreDifficulty difficulty) {
        this(UUID.randomUUID(), name, score, Instant.now(), difficulty);
    }
}

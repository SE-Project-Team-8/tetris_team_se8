package com.team.tetris.scoreboard;

import com.team.tetris.common.constants.GameConstants;
import com.team.tetris.common.persistence.PropertiesFile;
import com.team.tetris.common.Difficulty;
import com.team.tetris.common.GameMode;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Properties;
import java.util.UUID;

/**
 * Share one repository per application. Equal scores keep earlier entries first.
 * Each mode and difficulty pair is a separate ranking that keeps its own top entries, because
 * difficulty and items change how scores are earned.
 * File versions: 1 = no difficulty, 2 = difficulty, 3 = difficulty and mode. Older records load
 * as NORMAL difficulty and NORMAL mode and are rewritten as version 3 on the next save.
 */
public final class ScoreboardRepository {
    private static final int VERSION = 3;
    private static final Comparator<ScoreRecord> SCORE_ORDER = Comparator.comparingInt(ScoreRecord::score).reversed();
    private final Path file;
    private final int capacity;

    public ScoreboardRepository() {
        this(Path.of(System.getProperty("user.home"), ".tetris-team-se8", "scores.properties"));
    }

    public ScoreboardRepository(Path file) {
        this(file, GameConstants.SCOREBOARD_MIN_ENTRIES);
    }

    public ScoreboardRepository(Path file, int capacity) {
        this.file = Objects.requireNonNull(file, "file").toAbsolutePath().normalize();
        if (capacity < GameConstants.SCOREBOARD_MIN_ENTRIES) {
            throw new IllegalArgumentException("Scoreboard must retain at least 10 entries");
        }
        this.capacity = capacity;
    }

    /** Every ranking's entries together, highest score first. */
    public synchronized List<ScoreRecord> load() throws IOException {
        Properties properties = PropertiesFile.read(file);
        if (properties.isEmpty()) return List.of();
        try {
            int version = PropertiesFile.requireVersion(properties, 1, 2, VERSION);
            int count = Integer.parseInt(PropertiesFile.required(properties, "count"));
            if (count < 0 || count > properties.size() / 4) {
                throw new IllegalArgumentException("Invalid score count");
            }
            List<ScoreRecord> records = new ArrayList<>();
            HashSet<UUID> ids = new HashSet<>();
            for (int i = 0; i < count; i++) {
                String prefix = "record." + i + ".";
                ScoreRecord record = new ScoreRecord(
                        UUID.fromString(PropertiesFile.required(properties, prefix + "id")),
                        PropertiesFile.required(properties, prefix + "name"),
                        Integer.parseInt(PropertiesFile.required(properties, prefix + "score")),
                        Instant.parse(PropertiesFile.required(properties, prefix + "recordedAt")),
                        version == 1 ? Difficulty.NORMAL
                                : Difficulty.valueOf(PropertiesFile.required(properties, prefix + "difficulty")),
                        version < VERSION ? GameMode.NORMAL
                                : GameMode.valueOf(PropertiesFile.required(properties, prefix + "mode")));
                if (!ids.add(record.id())) throw new IllegalArgumentException("Duplicate record ID");
                records.add(record);
            }
            records.sort(SCORE_ORDER);
            return List.copyOf(keepTopOfEachRanking(records));
        } catch (IllegalArgumentException | DateTimeParseException invalid) {
            throw new IOException("Invalid scoreboard: " + file, invalid);
        }
    }

    /** One ranking: only the records played in the given difficulty and mode, highest score first. */
    public synchronized List<ScoreRecord> load(Difficulty difficulty, GameMode mode) throws IOException {
        return ranking(load(), difficulty, mode);
    }

    public synchronized boolean qualifies(int score) throws IOException {
        return qualifies(score, Difficulty.NORMAL, GameMode.NORMAL);
    }

    public synchronized boolean qualifies(int score, Difficulty difficulty, GameMode mode) throws IOException {
        if (score < 0) throw new IllegalArgumentException("score must be non-negative");
        List<ScoreRecord> ranking = load(difficulty, mode);
        return ranking.size() < capacity || score > ranking.getLast().score();
    }

    /** Save only a qualifying result. The returned ID can be used for UI highlighting. */
    public synchronized Optional<ScoreRecord> add(String name, int score) throws IOException {
        return add(name, score, Difficulty.NORMAL);
    }

    public synchronized Optional<ScoreRecord> add(String name, int score, Difficulty difficulty) throws IOException {
        return add(name, score, difficulty, GameMode.NORMAL);
    }

    public synchronized Optional<ScoreRecord> add(String name, int score, Difficulty difficulty, GameMode mode)
            throws IOException {
        ScoreRecord record = new ScoreRecord(name, score, difficulty, mode);
        List<ScoreRecord> records = new ArrayList<>(load());
        List<ScoreRecord> ranking = ranking(records, difficulty, mode);
        if (ranking.size() == capacity && score <= ranking.getLast().score()) return Optional.empty();
        records.add(record);
        records.sort(SCORE_ORDER);
        save(keepTopOfEachRanking(records));
        return Optional.of(record);
    }

    public synchronized void reset() throws IOException {
        save(List.of());
    }

    private static List<ScoreRecord> ranking(List<ScoreRecord> records, Difficulty difficulty, GameMode mode) {
        Objects.requireNonNull(difficulty, "difficulty");
        Objects.requireNonNull(mode, "mode");
        return records.stream()
                .filter(record -> record.difficulty() == difficulty && record.mode() == mode)
                .toList();
    }

    /** Expects records sorted by score; keeps that order and each ranking's first {@code capacity} entries. */
    private List<ScoreRecord> keepTopOfEachRanking(List<ScoreRecord> sorted) {
        Map<List<Enum<?>>, Integer> kept = new HashMap<>();
        List<ScoreRecord> result = new ArrayList<>();
        for (ScoreRecord record : sorted) {
            if (kept.merge(List.of(record.difficulty(), record.mode()), 1, Integer::sum) <= capacity) {
                result.add(record);
            }
        }
        return result;
    }

    private void save(List<ScoreRecord> records) throws IOException {
        Properties properties = new Properties();
        properties.setProperty("version", Integer.toString(VERSION));
        properties.setProperty("count", Integer.toString(records.size()));
        for (int i = 0; i < records.size(); i++) {
            ScoreRecord record = records.get(i);
            String prefix = "record." + i + ".";
            properties.setProperty(prefix + "id", record.id().toString());
            properties.setProperty(prefix + "name", record.name());
            properties.setProperty(prefix + "score", Integer.toString(record.score()));
            properties.setProperty(prefix + "recordedAt", record.recordedAt().toString());
            properties.setProperty(prefix + "difficulty", record.difficulty().name());
            properties.setProperty(prefix + "mode", record.mode().name());
        }
        PropertiesFile.write(file, properties);
    }
}

package com.team.tetris.scoreboard;

import com.team.tetris.common.constants.GameConstants;
import com.team.tetris.common.persistence.PropertiesFile;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Properties;
import java.util.UUID;

/** Share one repository per application. Equal scores keep earlier entries first. */
public final class ScoreboardRepository {
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

    public synchronized List<ScoreRecord> load() throws IOException {
        Properties properties = PropertiesFile.read(file);
        if (properties.isEmpty()) return List.of();
        try {
            PropertiesFile.requireVersion(properties);
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
                        properties.containsKey(prefix + "difficulty")
                                ? ScoreDifficulty.valueOf(PropertiesFile.required(properties, prefix + "difficulty"))
                                : ScoreDifficulty.NORMAL);
                if (!ids.add(record.id())) throw new IllegalArgumentException("Duplicate record ID");
                records.add(record);
            }
            records.sort(SCORE_ORDER);
            return List.copyOf(records);
        } catch (IllegalArgumentException | DateTimeParseException invalid) {
            throw new IOException("Invalid scoreboard: " + file, invalid);
        }
    }

    public synchronized boolean qualifies(int score) throws IOException {
        return qualifies(score, ScoreDifficulty.NORMAL);
    }

    public synchronized boolean qualifies(int score, ScoreDifficulty difficulty) throws IOException {
        if (score < 0) throw new IllegalArgumentException("score must be non-negative");
        Objects.requireNonNull(difficulty, "difficulty");
        List<ScoreRecord> records = load(difficulty);
        return records.size() < capacity || score > records.getLast().score();
    }

    /** Save only a qualifying result. The returned ID can be used for UI highlighting. */
    public synchronized Optional<ScoreRecord> add(String name, int score) throws IOException {
        return add(name, score, ScoreDifficulty.NORMAL);
    }

    public synchronized Optional<ScoreRecord> add(String name, int score, ScoreDifficulty difficulty)
            throws IOException {
        ScoreRecord record = new ScoreRecord(name, score, difficulty);
        List<ScoreRecord> records = new ArrayList<>(load());
        List<ScoreRecord> ranking = load(difficulty);
        if (ranking.size() == capacity && score <= ranking.getLast().score()) return Optional.empty();
        records.add(record);
        records.sort(SCORE_ORDER);
        save(limitPerDifficulty(records));
        return Optional.of(record);
    }

    /** Returns the top entries for one independent difficulty ranking. */
    public synchronized List<ScoreRecord> load(ScoreDifficulty difficulty) throws IOException {
        Objects.requireNonNull(difficulty, "difficulty");
        return load().stream()
                .filter(record -> record.difficulty() == difficulty)
                .limit(capacity)
                .toList();
    }

    public synchronized void reset() throws IOException {
        save(List.of());
    }

    private List<ScoreRecord> limitPerDifficulty(List<ScoreRecord> records) {
        List<ScoreRecord> limited = new ArrayList<>();
        for (ScoreDifficulty difficulty : ScoreDifficulty.values()) {
            records.stream()
                    .filter(record -> record.difficulty() == difficulty)
                    .limit(capacity)
                    .forEach(limited::add);
        }
        limited.sort(SCORE_ORDER);
        return limited;
    }

    private void save(List<ScoreRecord> records) throws IOException {
        Properties properties = new Properties();
        properties.setProperty("version", "1");
        properties.setProperty("count", Integer.toString(records.size()));
        for (int i = 0; i < records.size(); i++) {
            ScoreRecord record = records.get(i);
            String prefix = "record." + i + ".";
            properties.setProperty(prefix + "id", record.id().toString());
            properties.setProperty(prefix + "name", record.name());
            properties.setProperty(prefix + "score", Integer.toString(record.score()));
            properties.setProperty(prefix + "recordedAt", record.recordedAt().toString());
            properties.setProperty(prefix + "difficulty", record.difficulty().name());
        }
        PropertiesFile.write(file, properties);
    }
}

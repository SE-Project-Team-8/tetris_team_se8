package com.team.tetris.scoreboard;

import org.junit.jupiter.api.Test;
import com.team.tetris.common.Difficulty;
import com.team.tetris.common.GameMode;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ScoreboardRepositoryTest {
    @TempDir Path directory;

    @Test
    void difficultyRoundTripsAndLegacyScoresKeepTheirIdsWhenUpgraded() throws Exception {
        Path file = directory.resolve("scores.properties");
        ScoreboardRepository repository = repository();
        ScoreRecord first = repository.add("기존 기록", 100).orElseThrow();
        String legacy = Files.readString(file).replace("version=3", "version=1")
                .replaceAll("(?m)^record\\.\\d+\\.(difficulty|mode)=.*\\R?", "");
        Files.writeString(file, legacy);
        assertEquals(first, repository.load().getFirst());
        assertEquals(legacy, Files.readString(file));
        ScoreRecord hard = repository.add("어려움", 200, Difficulty.HARD).orElseThrow();
        ScoreRecord easy = repository.add("쉬움", 150, Difficulty.EASY).orElseThrow();
        assertEquals(List.of(hard, easy, first), repository().load());
        assertTrue(Files.readString(file).contains("version=3"));
        assertEquals(Difficulty.NORMAL, repository().load().getLast().difficulty());
    }

    @Test
    void modeRoundTripsSoNormalAndItemScoresStayDistinguishable() throws Exception {
        ScoreboardRepository repository = repository();
        ScoreRecord item = repository.add("아이템", 300, Difficulty.HARD, GameMode.ITEM).orElseThrow();
        ScoreRecord normal = repository.add("일반", 200, Difficulty.EASY, GameMode.NORMAL).orElseThrow();
        ScoreRecord defaulted = repository.add("기본", 100, Difficulty.NORMAL).orElseThrow();
        List<ScoreRecord> loaded = repository().load();
        assertEquals(List.of(item, normal, defaulted), loaded);
        assertEquals(List.of(GameMode.ITEM, GameMode.NORMAL, GameMode.NORMAL),
                loaded.stream().map(ScoreRecord::mode).toList());
        assertEquals(Difficulty.HARD, loaded.getFirst().difficulty());
    }

    @Test
    void versionTwoScoresAreReadAsNormalModeAndUpgradedOnTheNextSave() throws Exception {
        Path file = directory.resolve("scores.properties");
        ScoreboardRepository repository = repository();
        ScoreRecord hard = repository.add("2차 기록", 100, Difficulty.HARD).orElseThrow();
        String versionTwo = Files.readString(file).replace("version=3", "version=2")
                .replaceAll("(?m)^record\\.\\d+\\.mode=.*\\R?", "");
        Files.writeString(file, versionTwo);

        ScoreRecord migrated = repository().load().getFirst();
        assertEquals(hard, migrated);
        assertEquals(GameMode.NORMAL, migrated.mode());
        assertEquals(Difficulty.HARD, migrated.difficulty());
        assertEquals(versionTwo, Files.readString(file), "Loading alone never rewrites the file");

        repository.add("아이템", 50, Difficulty.NORMAL, GameMode.ITEM);
        String upgraded = Files.readString(file);
        assertTrue(upgraded.contains("version=3"));
        assertTrue(upgraded.contains("mode=ITEM") && upgraded.contains("mode=NORMAL"));
    }

    @Test
    void versionThreeRecordsCannotSilentlyLoseTheirMode() throws Exception {
        Path file = directory.resolve("scores.properties");
        repository().add("기록", 100, Difficulty.HARD, GameMode.ITEM);
        String valid = Files.readString(file);
        for (String invalid : new String[]{valid.replace("mode=ITEM", "mode=UNKNOWN"),
                valid.replaceAll("(?m)^record\\.\\d+\\.mode=.*\\R?", ""),
                valid.replace("version=3", "version=4")}) {
            Files.writeString(file, invalid);
            assertThrows(IOException.class, () -> repository().load());
            assertEquals(invalid, Files.readString(file));
        }
    }

    @Test
    void modernRecordsCannotSilentlyLoseDifficulty() throws Exception {
        Path file = directory.resolve("scores.properties");
        repository().add("기록", 100, Difficulty.HARD);
        String valid = Files.readString(file);
        for (String invalid : new String[]{valid.replace("difficulty=HARD", "difficulty=UNKNOWN"),
                valid.replaceAll("(?m)^record\\.\\d+\\.difficulty=.*\\R?", "")}) {
            Files.writeString(file, invalid);
            assertThrows(IOException.class, () -> repository().load());
            assertEquals(invalid, Files.readString(file));
        }
    }

    @Test
    void emptyBoardQualifiesEvenForAZeroScore() throws Exception {
        ScoreboardRepository repository = repository();
        assertTrue(repository.load().isEmpty());
        assertTrue(repository.qualifies(0));
        assertTrue(repository.add("첫 게임", 0).isPresent());
    }

    @Test
    void storesTopTenInDescendingOrderAcrossNewInstances() throws Exception {
        ScoreboardRepository repository = repository();
        for (int i = 0; i < 15; i++) repository.add("참가자" + i, i * 100);
        List<ScoreRecord> records = repository().load();
        assertEquals(10, records.size());
        for (int i = 0; i < 10; i++) assertEquals((14 - i) * 100, records.get(i).score());
        assertFalse(repository.qualifies(500));
        assertTrue(repository.qualifies(501));
        String before = Files.readString(directory.resolve("scores.properties"));
        assertTrue(repository.add("미달", 499).isEmpty());
        assertTrue(repository.add("동점", 500).isEmpty());
        assertEquals(before, Files.readString(directory.resolve("scores.properties")));
        assertThrows(UnsupportedOperationException.class, records::clear);
    }

    @Test
    void equalScoresKeepInsertionOrderAndDistinctIdsForHighlighting() throws Exception {
        ScoreboardRepository repository = repository();
        ScoreRecord first = repository.add("김테트리스", 123).orElseThrow();
        ScoreRecord second = repository.add("김테트리스", 123).orElseThrow();
        assertNotEquals(first.id(), second.id());
        assertEquals(List.of(first, second), repository().load());
    }

    @Test
    void escapedUnicodeNamesRoundTripAndAreTrimmed() throws Exception {
        ScoreRecord record = repository().add("  한글 = : \\ 이름 🎮  ", 42).orElseThrow();
        assertEquals("한글 = : \\ 이름 🎮", record.name());
        assertEquals(record, repository().load().getFirst());
    }

    @Test
    void supportsLargerBoardsButNeverFewerThanTen() throws Exception {
        Path file = directory.resolve("larger.properties");
        assertThrows(IllegalArgumentException.class, () -> new ScoreboardRepository(file, 9));
        ScoreboardRepository repository = new ScoreboardRepository(file, 12);
        for (int i = 0; i < 20; i++) repository.add("이름", i);
        assertEquals(12, new ScoreboardRepository(file, 12).load().size());
    }

    @Test
    void explicitResetPersistsAcrossInstances() throws Exception {
        repository().add("기록", 123);
        repository().reset();
        assertTrue(repository().load().isEmpty());
        assertTrue(repository().qualifies(0));
    }

    @Test
    void corruptDataDoesNotGetSilentlyOverwrittenByAddingAScore() throws Exception {
        Path file = directory.resolve("scores.properties");
        for (String invalid : new String[]{"version=4\ncount=0", "version=1\ncount=-1",
                "version=1\ncount=20", "version=1\ncount=no", "missing=version", "bad=\\uXXXX"}) {
            Files.writeString(file, invalid);
            assertThrows(IOException.class, () -> repository().load());
            assertThrows(IOException.class, () -> repository().add("새 점수", 100));
            assertEquals(invalid, Files.readString(file));
        }
    }

    @Test
    void invalidRecordFieldsAndDuplicateIdsOnDiskAreRejected() throws Exception {
        Path file = directory.resolve("scores.properties");
        repository().add("이름", 123);
        repository().add("다른 이름", 122);
        String valid = Files.readString(file);
        for (String corrupted : new String[]{valid.replace("score=123", "score=-1"),
                valid.replaceFirst("recordedAt=[^\r\n]+", "recordedAt=invalid"),
                valid.replaceFirst("id=[^\r\n]+", "id=invalid")}) {
            Files.writeString(file, corrupted);
            assertThrows(IOException.class, () -> repository().load());
        }
        Files.writeString(file, valid);
        List<ScoreRecord> records = repository().load();
        Files.writeString(file, valid.replace(records.get(1).id().toString(), records.getFirst().id().toString()));
        assertThrows(IOException.class, () -> repository().load());
    }

    @Test
    void validatesNamesScoresAndTimestamp() {
        for (String name : new String[]{"", "  ", "가".repeat(21), "가\n나", "가\t나"}) {
            assertThrows(IllegalArgumentException.class, () -> new ScoreRecord(name, 0));
        }
        assertDoesNotThrow(() -> new ScoreRecord("🎮".repeat(20), 0));
        assertThrows(IllegalArgumentException.class, () -> new ScoreRecord("이름", -1));
        assertThrows(IllegalArgumentException.class, () -> repository().qualifies(-1));
        assertThrows(NullPointerException.class, () -> new ScoreRecord(UUID.randomUUID(), "이름", 0, null));
        assertThrows(NullPointerException.class, () -> new ScoreRecord(null, "이름", 0, Instant.now()));
        assertThrows(NullPointerException.class,
                () -> new ScoreRecord(UUID.randomUUID(), "이름", 0, Instant.now(), Difficulty.NORMAL, null));
        assertEquals(GameMode.NORMAL, new ScoreRecord("이름", 0, Difficulty.HARD).mode());
    }

    private ScoreboardRepository repository() {
        return new ScoreboardRepository(directory.resolve("scores.properties"));
    }
}

package com.team.tetris.settings;

import com.team.tetris.game.GameAction;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.awt.event.KeyEvent;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.Properties;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class SettingsRepositoryTest {
    @TempDir Path directory;

    @Test
    void firstLaunchUsesDefaultsWithoutCreatingAFile() throws Exception {
        Path file = directory.resolve("settings.properties");
        assertEquals(GameSettings.defaults(), new SettingsRepository(file).load());
        assertFalse(Files.exists(file));
    }

    @ParameterizedTest
    @EnumSource(GameSettings.ScreenSize.class)
    void everyWindowSizeAndCustomKeysSurviveANewRepository(GameSettings.ScreenSize size) throws Exception {
        Path file = directory.resolve("nested/settings.properties");
        for (GameSettings.ColorBlindMode mode : GameSettings.ColorBlindMode.values()) {
            GameSettings settings = GameSettings.defaults().withScreenSize(size).withColorBlindMode(mode)
                    .withKeyBindings(KeyBindings.defaults().withKey(GameAction.MOVE_LEFT, KeyEvent.VK_A));
            new SettingsRepository(file).save(settings);
            GameSettings loaded = new SettingsRepository(file).load();
            assertEquals(settings, loaded);
            assertEquals(mode != GameSettings.ColorBlindMode.OFF, loaded.isColorBlindModeEnabled());
            assertTrue(size.width() > 0 && size.height() > 0);
        }
        try (var files = Files.list(file.getParent())) {
            assertEquals(1, files.count(), "Temporary files must be cleaned up");
        }
    }

    @Test
    void resettingDefaultsIsAlsoPersisted() throws Exception {
        Path file = directory.resolve("settings.properties");
        SettingsRepository repository = new SettingsRepository(file);
        repository.save(GameSettings.defaults().withColorBlindMode(GameSettings.ColorBlindMode.RED_GREEN));
        assertEquals(GameSettings.defaults(), repository.reset());
        assertEquals(GameSettings.defaults(), new SettingsRepository(file).load());
    }

    @Test
    void loadsVersionOneWithoutLosingCustomPreferencesThenSavesVersionTwo() throws Exception {
        Path file = directory.resolve("settings.properties");
        SettingsRepository repository = new SettingsRepository(file);
        GameSettings custom = GameSettings.defaults()
                .withScreenSize(GameSettings.ScreenSize.LARGE)
                .withColorBlindMode(GameSettings.ColorBlindMode.BLUE_YELLOW)
                .withKeyBindings(KeyBindings.defaults().withKey(GameAction.MOVE_LEFT, KeyEvent.VK_A));
        repository.save(custom);
        String versionOne = Files.readString(file).replace("version=2", "version=1");
        Files.writeString(file, versionOne);

        assertEquals(custom, new SettingsRepository(file).load());
        assertEquals(versionOne, Files.readString(file), "Reading must not rewrite the user's file");
        repository.save(custom);
        assertTrue(Files.readString(file).contains("version=2"));
    }

    @Test
    void newActionGetsUnusedFallbackWhenItsDefaultConflictsWithASavedKey() {
        Properties saved = new Properties();
        KeyBindings defaults = KeyBindings.defaults();
        for (GameAction action : GameAction.values()) {
            if (action != GameAction.QUIT) {
                int key = action == GameAction.MOVE_LEFT ? KeyEvent.VK_ESCAPE : defaults.keyFor(action);
                saved.setProperty("key." + action.name(), Integer.toString(key));
            }
        }
        Set<String> oldActions = Set.of("MOVE_LEFT", "MOVE_RIGHT", "SOFT_DROP",
                "ROTATE_CLOCKWISE", "HARD_DROP", "PAUSE");
        KeyBindings migrated = SettingsRepository.loadBindings(saved, oldActions);
        assertEquals(KeyEvent.VK_ESCAPE, migrated.keyFor(GameAction.MOVE_LEFT));
        assertEquals(KeyEvent.VK_F1, migrated.keyFor(GameAction.QUIT));
        assertEquals(GameAction.QUIT, migrated.actionFor(KeyEvent.VK_F1).orElseThrow());
    }

    @Test
    void brokenFilesAreReportedAndPreserved() throws Exception {
        Path file = directory.resolve("settings.properties");
        for (String invalid : new String[]{"version=99\n", "version=1\nscreenSize=UNKNOWN\n", "bad=\\uXYZW"}) {
            Files.writeString(file, invalid);
            assertThrows(IOException.class, () -> new SettingsRepository(file).load());
            assertEquals(invalid, Files.readString(file));
        }
    }

    @Test
    void duplicateKeysOnDiskAreRejected() throws Exception {
        Path file = directory.resolve("settings.properties");
        new SettingsRepository(file).save(GameSettings.defaults());
        String text = Files.readString(file).replace("key.MOVE_LEFT=37", "key.MOVE_LEFT=39");
        Files.writeString(file, text);
        assertThrows(IOException.class, () -> new SettingsRepository(file).load());
    }

    @Test
    void missingPreviouslySavedActionIsCorruptionRatherThanMigration() throws Exception {
        Path file = directory.resolve("settings.properties");
        new SettingsRepository(file).save(GameSettings.defaults());
        String broken = Files.readString(file).replaceAll("(?m)^key\\.MOVE_LEFT=.*\\R?", "");
        Files.writeString(file, broken);
        assertThrows(IOException.class, () -> new SettingsRepository(file).load());
        assertEquals(broken, Files.readString(file));
    }

    @Test
    void ioFailureIsReportedWithoutReplacingExistingData() throws Exception {
        Path blocker = directory.resolve("file-not-directory");
        Files.writeString(blocker, "keep me");
        assertThrows(IOException.class, () -> new SettingsRepository(blocker.resolve("settings.properties"))
                .save(GameSettings.defaults()));
        assertEquals("keep me", Files.readString(blocker));
    }

    @Test
    void keyBindingsAreCompleteUniqueAndImmutable() {
        KeyBindings defaults = KeyBindings.defaults();
        for (GameAction action : GameAction.values()) {
            assertEquals(action, defaults.actionFor(defaults.keyFor(action)).orElseThrow());
        }
        assertTrue(defaults.actionFor(KeyEvent.VK_F12).isEmpty());
        assertThrows(IllegalArgumentException.class,
                () -> defaults.withKey(GameAction.MOVE_LEFT, KeyEvent.VK_RIGHT));
        assertThrows(IllegalArgumentException.class,
                () -> defaults.withKey(GameAction.MOVE_LEFT, KeyEvent.VK_UNDEFINED));
        assertThrows(IllegalArgumentException.class,
                () -> defaults.withKey(GameAction.MOVE_LEFT, 0x10000));
        EnumMap<GameAction, Integer> copy = new EnumMap<>(defaults.keys());
        KeyBindings bindings = new KeyBindings(copy);
        copy.put(GameAction.MOVE_LEFT, KeyEvent.VK_A);
        assertEquals(KeyEvent.VK_LEFT, bindings.keyFor(GameAction.MOVE_LEFT));
        copy.remove(GameAction.QUIT);
        assertThrows(IllegalArgumentException.class, () -> new KeyBindings(copy));
        assertThrows(UnsupportedOperationException.class, () -> bindings.keys().clear());
        assertThrows(NullPointerException.class, () -> GameSettings.defaults().withScreenSize(null));
    }
}

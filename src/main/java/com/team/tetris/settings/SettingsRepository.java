package com.team.tetris.settings;

import com.team.tetris.common.persistence.PropertiesFile;
import com.team.tetris.game.GameAction;

import java.awt.event.KeyEvent;
import java.io.IOException;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.Objects;
import java.util.Properties;
import java.util.Set;

/** Missing data uses defaults; invalid data raises IOException without overwriting it. */
public final class SettingsRepository {
    private static final String CURRENT_VERSION = "2";
    // The first release saved these seven actions. Keep this list fixed when new actions are added.
    private static final Set<String> ORIGINAL_ACTIONS = Set.of(
            "MOVE_LEFT", "MOVE_RIGHT", "SOFT_DROP", "ROTATE_CLOCKWISE", "HARD_DROP", "PAUSE", "QUIT");
    private final Path file;

    public SettingsRepository() {
        this(Path.of(System.getProperty("user.home"), ".tetris-team-se8", "settings.properties"));
    }

    public SettingsRepository(Path file) {
        this.file = Objects.requireNonNull(file, "file").toAbsolutePath().normalize();
    }

    public synchronized GameSettings load() throws IOException {
        Properties properties = PropertiesFile.read(file);
        if (properties.isEmpty()) return GameSettings.defaults();
        try {
            String version = PropertiesFile.required(properties, "version");
            if (!"1".equals(version) && !CURRENT_VERSION.equals(version)) {
                throw new IllegalArgumentException("Unsupported settings version: " + version);
            }
            return new GameSettings(
                    GameSettings.ScreenSize.valueOf(PropertiesFile.required(properties, "screenSize")),
                    GameSettings.ColorBlindMode.valueOf(PropertiesFile.required(properties, "colorBlindMode")),
                    loadBindings(properties, ORIGINAL_ACTIONS));
        } catch (IllegalArgumentException invalid) {
            throw new IOException("Invalid settings: " + file, invalid);
        }
    }

    /** Retain saved keys; assign new actions defaults, then a free fallback if needed. */
    static KeyBindings loadBindings(Properties properties, Set<String> requiredActions) {
        EnumMap<GameAction, Integer> keys = new EnumMap<>(GameAction.class);
        for (GameAction action : GameAction.values()) {
            String saved = properties.getProperty("key." + action.name());
            if (saved != null) {
                keys.put(action, Integer.parseInt(saved));
            } else if (requiredActions.contains(action.name())) {
                throw new IllegalArgumentException("Missing property: key." + action.name());
            }
        }
        HashSet<Integer> used = new HashSet<>(keys.values());
        if (used.size() != keys.size()) {
            throw new IllegalArgumentException("Duplicate saved keys");
        }
        KeyBindings defaults = KeyBindings.defaults();
        for (GameAction action : GameAction.values()) {
            if (!keys.containsKey(action)) {
                int preferred = defaults.keyFor(action);
                int assigned = used.contains(preferred) ? freeKey(used) : preferred;
                keys.put(action, assigned);
                used.add(assigned);
            }
        }
        return new KeyBindings(keys);
    }

    private static int freeKey(Set<Integer> used) {
        for (int key = KeyEvent.VK_F1; key <= KeyEvent.VK_F12; key++) {
            if (!used.contains(key)) return key;
        }
        for (int key = KeyEvent.VK_A; key <= KeyEvent.VK_Z; key++) {
            if (!used.contains(key)) return key;
        }
        throw new IllegalArgumentException("No unused key is available for a new action");
    }

    public synchronized void save(GameSettings settings) throws IOException {
        Objects.requireNonNull(settings, "settings");
        Properties properties = new Properties();
        properties.setProperty("version", CURRENT_VERSION);
        properties.setProperty("screenSize", settings.screenSize().name());
        properties.setProperty("colorBlindMode", settings.colorBlindMode().name());
        settings.keyBindings().keys().forEach((action, key) ->
                properties.setProperty("key." + action.name(), key.toString()));
        PropertiesFile.write(file, properties);
    }

    /** Reset preferences only; scoreboard reset is a separate user action. */
    public synchronized GameSettings reset() throws IOException {
        GameSettings defaults = GameSettings.defaults();
        save(defaults);
        return defaults;
    }
}

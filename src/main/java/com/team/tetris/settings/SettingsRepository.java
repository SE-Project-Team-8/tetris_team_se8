package com.team.tetris.settings;

import com.team.tetris.common.persistence.PropertiesFile;

import java.io.IOException;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.Objects;
import java.util.Properties;

/** Missing data uses defaults; invalid data raises IOException without overwriting it. */
public final class SettingsRepository {
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
            PropertiesFile.requireVersion(properties);
            EnumMap<KeyBindings.Action, Integer> keys = new EnumMap<>(KeyBindings.Action.class);
            for (KeyBindings.Action action : KeyBindings.Action.values()) {
                keys.put(action, Integer.parseInt(PropertiesFile.required(properties, "key." + action.name())));
            }
            return new GameSettings(
                    GameSettings.ScreenSize.valueOf(PropertiesFile.required(properties, "screenSize")),
                    GameSettings.ColorBlindMode.valueOf(PropertiesFile.required(properties, "colorBlindMode")),
                    new KeyBindings(keys));
        } catch (IllegalArgumentException invalid) {
            throw new IOException("Invalid settings: " + file, invalid);
        }
    }

    public synchronized void save(GameSettings settings) throws IOException {
        Objects.requireNonNull(settings, "settings");
        Properties properties = new Properties();
        properties.setProperty("version", "1");
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

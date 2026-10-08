package com.team.tetris.settings;

import java.util.Objects;

/** Persisted preferences. Window sizes never change the 20 x 10 board dimensions. */
public record GameSettings(ScreenSize screenSize, ColorBlindMode colorBlindMode, KeyBindings keyBindings) {
    public enum ScreenSize {
        SMALL(480, 640), MEDIUM(600, 800), LARGE(750, 1000);

        private final int width;
        private final int height;

        ScreenSize(int width, int height) {
            this.width = width;
            this.height = height;
        }

        public int width() { return width; }
        public int height() { return height; }
    }

    public enum ColorBlindMode { OFF, RED_GREEN, BLUE_YELLOW }

    public GameSettings {
        Objects.requireNonNull(screenSize, "screenSize");
        Objects.requireNonNull(colorBlindMode, "colorBlindMode");
        Objects.requireNonNull(keyBindings, "keyBindings");
    }

    public static GameSettings defaults() {
        return new GameSettings(ScreenSize.MEDIUM, ColorBlindMode.OFF, KeyBindings.defaults());
    }

    public boolean isColorBlindModeEnabled() { return colorBlindMode != ColorBlindMode.OFF; }

    public GameSettings withScreenSize(ScreenSize size) {
        return new GameSettings(size, colorBlindMode, keyBindings);
    }

    public GameSettings withColorBlindMode(ColorBlindMode mode) {
        return new GameSettings(screenSize, mode, keyBindings);
    }

    public GameSettings withKeyBindings(KeyBindings bindings) {
        return new GameSettings(screenSize, colorBlindMode, bindings);
    }
}

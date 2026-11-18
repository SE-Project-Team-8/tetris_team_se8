package com.team.tetris.settings;

import com.team.tetris.common.Difficulty;

import java.util.Objects;

/** Persisted preferences. Window sizes never change the 20 x 10 board dimensions. */
public record GameSettings(ScreenSize screenSize, ColorBlindMode colorBlindMode, KeyBindings keyBindings,
                           Difficulty difficulty) {
    public enum ScreenSize {
        // LARGE가 디자인 기준 크기(배율 1.0)다. 노트북 화면에서도 창이 넘치지 않도록 800px 높이를 상한으로 둔다.
        SMALL(480, 640), MEDIUM(540, 720), LARGE(600, 800);

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
        Objects.requireNonNull(difficulty, "difficulty");
    }

    public GameSettings(ScreenSize screenSize, ColorBlindMode colorBlindMode, KeyBindings keyBindings) {
        this(screenSize, colorBlindMode, keyBindings, Difficulty.NORMAL);
    }

    public static GameSettings defaults() {
        return new GameSettings(ScreenSize.MEDIUM, ColorBlindMode.OFF, KeyBindings.defaults());
    }

    public boolean isColorBlindModeEnabled() { return colorBlindMode != ColorBlindMode.OFF; }

    public GameSettings withScreenSize(ScreenSize size) {
        return new GameSettings(size, colorBlindMode, keyBindings, difficulty);
    }

    public GameSettings withColorBlindMode(ColorBlindMode mode) {
        return new GameSettings(screenSize, mode, keyBindings, difficulty);
    }

    public GameSettings withKeyBindings(KeyBindings bindings) {
        return new GameSettings(screenSize, colorBlindMode, bindings, difficulty);
    }

    public GameSettings withDifficulty(Difficulty difficulty) {
        return new GameSettings(screenSize, colorBlindMode, keyBindings, difficulty);
    }
}

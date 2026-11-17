package com.team.tetris.common;

/** Game rules chosen from the start menu; scoreboard records keep the mode they were played in. */
public enum GameMode {
    NORMAL("Normal"),
    ITEM("Item");

    private final String label;

    GameMode(String label) {
        this.label = label;
    }

    public String label() { return label; }
}

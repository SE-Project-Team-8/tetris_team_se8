package com.team.tetris.common;

/** Shared game rules. The middle difficulty is NORMAL, as named in Requirements 2. */
public enum Difficulty {
    EASY("Easy", 12, 160, 80),
    NORMAL("Normal", 10, 200, 100),
    HARD("Hard", 8, 240, 120);

    private final String label;
    private final int iBlockWeight;
    private final int lineSpeedStepMillis;
    private final int lineScorePercent;

    Difficulty(String label, int iBlockWeight, int lineSpeedStepMillis, int lineScorePercent) {
        this.label = label;
        this.iBlockWeight = iBlockWeight;
        this.lineSpeedStepMillis = lineSpeedStepMillis;
        this.lineScorePercent = lineScorePercent;
    }

    public String label() { return label; }
    public int iBlockWeight() { return iBlockWeight; }
    public int lineSpeedStepMillis() { return lineSpeedStepMillis; }
    public int lineScorePercent() { return lineScorePercent; }
}

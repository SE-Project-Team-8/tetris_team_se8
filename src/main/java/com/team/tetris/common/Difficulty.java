package com.team.tetris.common;

/** Shared game rules. The middle difficulty is NORMAL, as named in Requirements 2. */
public enum Difficulty {
    EASY("Easy", 12, 80, 160, 120, 80),
    NORMAL("Normal", 10, 100, 200, 100, 100),
    HARD("Hard", 8, 120, 240, 80, 120);

    private final String label;
    private final int iBlockWeight;
    private final int blockSpeedStepMillis;
    private final int lineSpeedStepMillis;
    private final int minDropIntervalMillis;
    private final int lineScorePercent;

    Difficulty(String label, int iBlockWeight, int blockSpeedStepMillis, int lineSpeedStepMillis,
               int minDropIntervalMillis, int lineScorePercent) {
        this.label = label;
        this.iBlockWeight = iBlockWeight;
        this.blockSpeedStepMillis = blockSpeedStepMillis;
        this.lineSpeedStepMillis = lineSpeedStepMillis;
        this.minDropIntervalMillis = minDropIntervalMillis;
        this.lineScorePercent = lineScorePercent;
    }

    public String label() { return label; }
    public int iBlockWeight() { return iBlockWeight; }
    public int blockSpeedStepMillis() { return blockSpeedStepMillis; }
    public int lineSpeedStepMillis() { return lineSpeedStepMillis; }
    /** The fastest drop interval also keeps the 20% gap, so Hard never catches up with Normal. */
    public int minDropIntervalMillis() { return minDropIntervalMillis; }
    public int lineScorePercent() { return lineScorePercent; }
}

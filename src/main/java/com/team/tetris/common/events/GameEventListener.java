package com.team.tetris.common.events;

public interface GameEventListener {

    /** One-way game-to-UI contract; changes require agreement between 담당자2 and B. */
    void onBoardUpdated(BoardSnapshot snapshot);

    void onScoreChanged(int score);

    void onNextBlocksChanged(int[] blockTypes);

    void onPauseStateChanged(boolean paused);

    void onGameOver(int score);
}
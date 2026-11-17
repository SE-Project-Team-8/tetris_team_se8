package com.team.tetris.common.events;

import java.util.List;

public interface GameEventListener {

    /** One-way game-to-UI contract; changes require agreement between 담당자2 and B. */
    void onBoardUpdated(BoardSnapshot snapshot);

    default void onLandingUpdated(BoardSnapshot fallingPiece, BoardSnapshot landingPiece) { }

    default void onLinesCleared(List<Integer> rows) { }

    default void onLinesCleared(List<Integer> rows, BoardSnapshot boardBeforeClear) {
        onLinesCleared(rows);
    }

    void onScoreChanged(int score);

    void onNextBlocksChanged(int[] blockTypes);

    void onPauseStateChanged(boolean paused);

    default void onFeverActivated(int seconds) { }

    default void onSlowDownActivated(int previousLevel, int currentLevel) { }

    void onGameOver(int score);
}
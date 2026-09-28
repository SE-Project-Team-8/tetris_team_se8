package com.team.tetris.game;

import com.team.tetris.common.events.GameEventListener;

/**
 * UI-facing game session contract. GameEngine should implement this interface so
 * screens can send commands without depending on engine internals.
 */
public interface GameSession {

    void addListener(GameEventListener listener);

    void removeListener(GameEventListener listener);

    void startNewGame();

    void resume();

    void pause();

    void moveLeft();

    void moveRight();

    void moveDown();

    void rotateClockwise();

    void hardDrop();
}
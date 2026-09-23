package com.team.tetris.game;

/** A finished game may be restarted; only RUNNING accepts movement. */
public enum GameState {
    READY, RUNNING, PAUSED, GAME_OVER, STOPPED;

    public boolean canTransitionTo(GameState next) {
        if (next == null || next == this) return false;
        return switch (this) {
            case READY, GAME_OVER, STOPPED -> next == RUNNING;
            case RUNNING -> next == PAUSED || next == GAME_OVER || next == STOPPED;
            case PAUSED -> next == RUNNING || next == STOPPED;
        };
    }
}

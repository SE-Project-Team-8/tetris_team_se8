package com.team.tetris.game;

/** Game commands are independent of the device or key that produced them. */
public enum GameAction {
    MOVE_LEFT, MOVE_RIGHT, SOFT_DROP, ROTATE_CLOCKWISE, ROTATE_COUNTERCLOCKWISE, HARD_DROP, PAUSE, QUIT
}

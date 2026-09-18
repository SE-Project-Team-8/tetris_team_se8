package com.team.tetris.common.events;

public record BoardSnapshot(int[][] cells) {

    public static final int EMPTY = -1;

    /** Contains the final state with the currently falling block composed in; UI only draws it. */
    public int rows() {
        return cells.length;
    }

    public int cols() {
        return cells.length == 0 ? 0 : cells[0].length;
    }
}
package com.team.tetris.block;

/** 블록의 고정된 로컬 기준점에 대한 시계 방향 회전 상태. */
public enum Rotation {
    /** 초기 형태. */
    SPAWN,
    /** 초기 형태에서 시계 방향 90도. */
    RIGHT,
    /** 초기 형태에서 180도. */
    REVERSE,
    /** 초기 형태에서 시계 방향 270도. */
    LEFT;

    /**
     * 다음 시계 방향 상태를 반환한다. 네 번 호출하면 원래 상태로 돌아온다.
     *
     * @return 시계 방향 90도 회전한 상태
     */
    public Rotation clockwise() {
        return switch (this) {
            case SPAWN -> RIGHT;
            case RIGHT -> REVERSE;
            case REVERSE -> LEFT;
            case LEFT -> SPAWN;
        };
    }
}

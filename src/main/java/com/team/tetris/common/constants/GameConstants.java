/*
 * 보드 크기·낙하 간격·아이템 등장 줄 수 등 공용 상수.
 */
package com.team.tetris.common.constants;

public final class GameConstants {

    public static final String GAME_TITLE = "Tetris";
    public static final int BOARD_ROWS = 20;
    public static final int BOARD_COLS = 10;
    public static final long INITIAL_DROP_INTERVAL_MS = 1000L;
    public static final int NEXT_QUEUE_SIZE = 1;
    /** 아이템 모드에서 아이템 기회 하나를 얻는 데 필요한 누적 삭제 줄 수 (2차 요구사항 4.2). */
    public static final int LINES_PER_ITEM = 10;
    public static final int SCOREBOARD_MIN_ENTRIES = 10;
    /** 대전 모드에서 공격이 되는 최소 동시 삭제 줄 수 (3차 요구사항 p.4). */
    public static final int MIN_ATTACK_CLEARED_LINES = 2;
    /** 대전 모드의 공격 대기열이 담는 최대 줄 수 (3차 요구사항 p.6). */
    public static final int ATTACK_QUEUE_MAX_LINES = 10;
    /** 시간제한 대전의 제한 시간 (docs/battle/decisions.md D-10). */
    public static final int VERSUS_TIME_LIMIT_SECONDS = 180;

    private GameConstants() {
    }
}
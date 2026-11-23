/*
 * 대전 모드의 게임 방식. 스코어보드 기록의 키인 GameMode와 별개여서 대전 결과는 스코어보드에 저장되지 않는다.
 */
package com.team.tetris.common.battle;

/** 3차 요구사항 p.3의 세 가지 대전 방식. */
public enum VersusMode {
    /** 삭제한 줄이 상대 보드에 추가된다. */
    NORMAL,
    /** 일반 방식에 아이템이 등장한다. */
    ITEM,
    /** 일반 방식에 더해, 제한 시간이 끝나면 점수가 높은 쪽이 이긴다. */
    TIME_LIMIT
}

/*
 * 대전 화면이 받는 알림. 플레이어별 보드·점수·다음 블록은 기존 GameEventListener로 따로 받는다.
 */
package com.team.tetris.game.versus;

import com.team.tetris.common.battle.GarbageLine;
import com.team.tetris.common.battle.Player;

import java.util.List;

/**
 * 대전 한 판 전체에 대한 알림 (docs/battle/ui-contract.md 4.2). 모두 Swing EDT에서 호출된다.
 * 필요한 알림만 구현하면 된다.
 */
public interface VersusListener {
    /** 그 플레이어의 공격 대기열이 바뀌었다. 위에서 아래 순서의 줄이며, 보드에 들어가 비워지면 빈 목록이다. */
    default void onQueueChanged(Player player, List<GarbageLine> lines) { }

    /** 그 플레이어의 레벨이 바뀌었다. 대전을 시작할 때도 한 번 온다. */
    default void onLevelChanged(Player player, int level) { }

    /** 시간제한 대전의 남은 시간(초). 시작할 때와 1초마다 온다. */
    default void onTimeChanged(int remainingSeconds) { }

    /** 대전 전체가 일시정지되거나 다시 진행된다. */
    default void onPauseChanged(boolean paused) { }

    /** 대전이 끝났다. 한 판에 한 번만 온다. 중단(stop)으로 끝나면 오지 않는다. */
    default void onMatchEnded(MatchResult result) { }

    VersusListener NONE = new VersusListener() { };
}

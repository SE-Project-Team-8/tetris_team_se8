/*
 * 활성화된 아이템이 엔진에 요청할 수 있는 효과(점수 배율, 속도 단계 감소)의 좁은 통로.
 * GameEngine이 구현하며, 아이템은 Item.onActivated(GameEffects)에서만 받는다.
 */
package com.team.tetris.game.item;

/**
 * 활성화된 아이템이 엔진에 요청할 수 있는 효과. {@code GameEngine}이 구현한다.
 *
 * 아이템은 엔진 상태를 직접 만지지 않고 이 메서드만 부른다. 보드 효과는 {@link EmbeddedItem#onLock}에,
 * 낙하 규칙은 {@link FallingPiece}에 두고, 점수·속도처럼 엔진이 가진 상태만 여기로 요청한다.
 */
public interface GameEffects {
    /** 앞으로 seconds초 동안 얻는 모든 점수에 multiplier를 곱한다. 이미 켜져 있으면 남은 시간을 더하지 않고 seconds로 갱신한다. */
    void scoreMultiplier(int multiplier, int seconds);

    /** 현재 속도 단계를 steps만큼 영구히 낮춘다. 레벨 1이면 아무 일도 하지 않고 보관하지도 않는다. */
    void lowerLevel(int steps);
}

/*
 * 대전 키 하나가 맡은 일: 누구의 어떤 조작인가. 설정의 대전 키와 VersusInput이 함께 쓴다.
 */
package com.team.tetris.game.versus;

import com.team.tetris.common.battle.Player;
import com.team.tetris.game.GameAction;

import java.util.Objects;

/**
 * 한 플레이어의 조작 하나. 일시정지·나가기는 두 사람이 함께 쓰는 키라 플레이어의 조작이 아니다 (input.md 5절).
 *
 * @param player 조작하는 플레이어
 * @param action 블록 조작. {@link GameAction#PAUSE}·{@link GameAction#QUIT}는 받지 않는다
 */
public record PlayerMove(Player player, GameAction action) {
    public PlayerMove {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(action, "action");
        if (action == GameAction.PAUSE || action == GameAction.QUIT) {
            throw new IllegalArgumentException("Not a player move: " + action);
        }
    }

    /** 누르고 있으면 반복되는 조작인지. 하드드롭은 누를 때 한 번만 (input.md 3절). */
    public boolean repeats() {
        return action != GameAction.HARD_DROP;
    }
}

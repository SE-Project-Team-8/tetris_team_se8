/*
 * 대전 한 판의 결과. 승자(없으면 무승부), 끝난 이유, 두 플레이어의 점수.
 */
package com.team.tetris.game.versus;

import com.team.tetris.common.battle.Player;

import java.util.Objects;
import java.util.Optional;

/**
 * 대전 한 판의 결과 (3차 요구사항 p.5 "누가 승자인지를 화면에 표시").
 *
 * @param winner  승자. 무승부면 null
 * @param reason  끝난 이유
 * @param p1Score P1의 최종 점수
 * @param p2Score P2의 최종 점수
 */
public record MatchResult(Player winner, Reason reason, int p1Score, int p2Score) {
    /** 대전이 끝난 이유. */
    public enum Reason {
        /** 한 플레이어가 더 이상 블록을 쌓을 수 없게 되었다. */
        TOP_OUT,
        /** 시간제한 대전의 제한 시간이 끝났다. */
        TIME_UP
    }

    public MatchResult {
        Objects.requireNonNull(reason, "reason");
        if (winner == null && reason != Reason.TIME_UP) {
            throw new IllegalArgumentException("Only a time-up result can be a draw");
        }
    }

    public boolean isDraw() {
        return winner == null;
    }

    /** 승자. 무승부면 비어 있다. */
    public Optional<Player> winnerIfAny() {
        return Optional.ofNullable(winner);
    }

    public int scoreOf(Player player) {
        return Objects.requireNonNull(player, "player") == Player.P1 ? p1Score : p2Score;
    }
}

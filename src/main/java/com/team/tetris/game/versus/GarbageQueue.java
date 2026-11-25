/*
 * 대전 모드의 공격 대기열. 상대에게 받았지만 아직 보드에 들어가지 않은 줄을 위에서 아래 순서로 담는다.
 */
package com.team.tetris.game.versus;

import com.team.tetris.common.battle.GarbageLine;
import com.team.tetris.common.constants.GameConstants;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 플레이어 한 명의 공격 대기열 (3차 요구사항 p.6).
 *
 * 최대 10줄을 담는다. 새 공격은 이미 있는 줄의 아래쪽에 붙고, 이미 가득 차 있으면 통째로 무시된다.
 * 더해서 10줄을 넘으면 새 공격의 위쪽 줄부터 빈 자리만큼만 받고 아래쪽 줄은 버린다.
 * Swing EDT 한 스레드에서만 쓴다.
 */
public final class GarbageQueue {
    public static final int CAPACITY = GameConstants.ATTACK_QUEUE_MAX_LINES;

    private final List<GarbageLine> lines = new ArrayList<>();

    /**
     * 공격 한 번의 줄들을 받는다.
     *
     * @param attack 위에서 아래 순서의 줄
     * @return 실제로 받은 줄 수. 이미 가득 차 있으면 0
     */
    public int offer(List<GarbageLine> attack) {
        List<GarbageLine> incoming = List.copyOf(Objects.requireNonNull(attack, "attack"));
        int accepted = Math.min(CAPACITY - lines.size(), incoming.size());
        lines.addAll(incoming.subList(0, accepted));
        return accepted;
    }

    /** 대기 중인 줄을 전부 꺼내고 비운다. 보드에 넣기 직전에 부른다. */
    public List<GarbageLine> drain() {
        List<GarbageLine> drained = List.copyOf(lines);
        lines.clear();
        return drained;
    }

    /** 화면 표시용 복사본. 위에서 아래 순서다. */
    public List<GarbageLine> lines() {
        return List.copyOf(lines);
    }

    public int size() {
        return lines.size();
    }

    public boolean isEmpty() {
        return lines.isEmpty();
    }

    public void clear() {
        lines.clear();
    }
}

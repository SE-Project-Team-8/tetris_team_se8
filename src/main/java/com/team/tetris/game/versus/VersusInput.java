/*
 * 로컬 대전의 키 입력: 키 코드를 플레이어·조작으로 나누고, 키마다 따로 누름 반복을 돌린다. 화면은 키 코드만 넘긴다.
 */
package com.team.tetris.game.versus;

import com.team.tetris.common.battle.Player;
import com.team.tetris.game.GameAction;
import com.team.tetris.game.GameEngine;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Supplier;

/**
 * 두 사람의 키를 한 창에서 받아 나눠 준다 (LB-F18, LB-I01, docs/battle/input.md 2·3절).
 *
 * OS의 자동 반복은 마지막에 누른 키 하나만 반복하므로, 한 사람이 키를 누르고 있는 중에 다른 사람이 키를 누르면 앞사람의
 * 반복이 끊긴다. 그래서 OS 반복은 무시하고, 눌린 키마다 반복 타이머를 따로 돌린다. 새로 누른 키는 바로 한 번 실행하고,
 * {@link #REPEAT_DELAY_MILLIS} 뒤부터 {@link #REPEAT_INTERVAL_MILLIS}마다 반복한다. 하드드롭은 반복하지 않는다.
 *
 * 키는 키 코드와 위치(왼쪽·오른쪽)로 받는다. 키 표의 키는 {@link SidedKey}라서 왼쪽 Shift와 오른쪽 Shift를 다른
 * 사람에게 줄 수 있다. 쪽을 정하지 않은 표의 키는 어느 쪽에서 눌려도 맞는다.
 *
 * 일시정지·나가기 키는 다루지 않는다. 화면 전환이 따르는 키라 화면이 직접 받는다. 대전이 진행 중이 아니면 조작 키도
 * 받지 않고, 반복 중이던 키는 다음 반복 때 스스로 멈춘다. 모든 메서드는 Swing EDT에서 호출한다.
 */
public final class VersusInput {
    /** 키를 누른 뒤 첫 반복까지. 플레이해 보며 맞춘다 (input.md 3절의 시작값). */
    public static final int REPEAT_DELAY_MILLIS = 170;
    /** 첫 반복 뒤의 반복 간격. */
    public static final int REPEAT_INTERVAL_MILLIS = 50;
    /**
     * 회전 키의 첫 반복까지. 이동처럼 170ms로 두면 평소처럼 톡 눌러도 두 번 돌아서 1인 모드보다 예민하게 느껴진다.
     * 1인 모드(OS 자동 반복, 보통 0.5초 안팎)처럼 짧게 누르면 한 번만 돌게 길게 둔다.
     */
    public static final int ROTATE_REPEAT_DELAY_MILLIS = 450;
    /** 회전 키를 계속 누르고 있을 때의 반복 간격. 한 바퀴를 눈으로 따라갈 수 있게 이동보다 느리다. */
    public static final int ROTATE_REPEAT_INTERVAL_MILLIS = 150;

    /** 조작을 받는 쪽. 실제로는 {@link VersusMatch}다. */
    interface Target {
        boolean isRunning();

        boolean handle(Player player, GameAction action);
    }

    private final Target target;
    private final Supplier<GameEngine.DropTimer> timers;
    private final Set<Integer> down = new HashSet<>();
    private final Map<Integer, GameEngine.DropTimer> repeating = new HashMap<>();
    private Map<Integer, PlayerMove> keys;

    /**
     * 실제 게임: 반복은 Swing 타이머로 잰다.
     *
     * @param keys 키 코드별 조작. 설정의 {@code GameSettings.versusKeyMap()}
     */
    public VersusInput(VersusMatch match, Map<Integer, PlayerMove> keys) {
        this(match, keys, GameEngine::newSwingTimer);
    }

    /** @param timers 눌린 키마다 반복 타이머를 하나씩 만든다. 테스트는 직접 넘기는 타이머를 넣는다. */
    public VersusInput(VersusMatch match, Map<Integer, PlayerMove> keys, Supplier<GameEngine.DropTimer> timers) {
        this(targetOf(match), keys, timers);
    }

    VersusInput(Target target, Map<Integer, PlayerMove> keys, Supplier<GameEngine.DropTimer> timers) {
        this.target = Objects.requireNonNull(target, "target");
        this.timers = Objects.requireNonNull(timers, "timers");
        setKeys(keys);
    }

    /** 키 설정이 바뀌었다. 눌려 있던 키는 모두 뗀 것으로 친다. */
    public void setKeys(Map<Integer, PlayerMove> keys) {
        Map<Integer, PlayerMove> copy = Map.copyOf(Objects.requireNonNull(keys, "keys"));
        releaseAll();
        this.keys = copy;
    }

    /**
     * 창에서 받은 KEY_PRESSED. OS 자동 반복도 걸러 내지 않고 그대로 넘긴다.
     *
     * @param keyCode  {@code KeyEvent.getKeyCode()}
     * @param location {@code KeyEvent.getKeyLocation()}. 왼쪽·오른쪽 Shift를 가른다
     * @return 대전 조작 키여서 이 입력이 썼으면 true. 대전 키가 아니거나 대전이 진행 중이 아니면 false라서 다른 처리에 넘어간다
     */
    public boolean keyPressed(int keyCode, int location) {
        int key = SidedKey.of(keyCode, location);
        PlayerMove move = moveFor(key);
        if (move == null || !target.isRunning()) return false;
        if (!down.add(key)) return true;   // OS 자동 반복: 반복은 직접 돌린다
        target.handle(move.player(), move.action());
        if (move.repeats()) startRepeat(key, move);
        return true;
    }

    /** 창에서 받은 KEY_RELEASED. 그 키의 반복을 멈춘다. */
    public void keyReleased(int keyCode, int location) {
        int key = SidedKey.of(keyCode, location);
        down.remove(key);
        GameEngine.DropTimer timer = repeating.remove(key);
        if (timer != null) timer.stop();
    }

    /** 그쪽 키로 정한 조작을 먼저, 없으면 쪽을 가리지 않는 키의 조작을 찾는다. */
    private PlayerMove moveFor(int key) {
        PlayerMove move = keys.get(key);
        if (move == null && SidedKey.side(key) != SidedKey.ANY) move = keys.get(SidedKey.keyCode(key));
        return move;
    }

    /** 눌린 키를 모두 뗀 것으로 친다. 화면을 떠날 때, 창이 포커스를 잃을 때, 대화상자를 띄울 때 부른다. */
    public void releaseAll() {
        down.clear();
        repeating.values().forEach(GameEngine.DropTimer::stop);
        repeating.clear();
    }

    private void startRepeat(int keyCode, PlayerMove move) {
        GameEngine.DropTimer timer = Objects.requireNonNull(timers.get(), "timer");
        repeating.put(keyCode, timer);
        boolean rotate = move.action() == GameAction.ROTATE_CLOCKWISE
                || move.action() == GameAction.ROTATE_COUNTERCLOCKWISE;
        int delay = rotate ? ROTATE_REPEAT_DELAY_MILLIS : REPEAT_DELAY_MILLIS;
        int interval = rotate ? ROTATE_REPEAT_INTERVAL_MILLIS : REPEAT_INTERVAL_MILLIS;
        // 첫 지연이 간격보다 길어서 한 번 기다린 뒤 간격으로 다시 건다 (SwingDropTimer는 첫 지연이 간격 이하만 받는다).
        timer.start(delay, () -> {
            if (repeatOnce(keyCode, move, timer)) {
                timer.start(interval, () -> repeatOnce(keyCode, move, timer));
            }
        });
    }

    /**
     * 반복 한 번. 대전이 멈췄거나 끝났으면 눌림 기록을 비우고 false. 키를 뗀 뒤 늦게 도착한 호출(다시 눌러 새 타이머가
     * 생긴 경우 포함)도 false.
     */
    private boolean repeatOnce(int keyCode, PlayerMove move, GameEngine.DropTimer timer) {
        if (repeating.get(keyCode) != timer) return false;
        if (!target.isRunning()) {
            releaseAll();
            return false;
        }
        target.handle(move.player(), move.action());
        return true;
    }

    private static Target targetOf(VersusMatch match) {
        Objects.requireNonNull(match, "match");
        return new Target() {
            @Override public boolean isRunning() { return match.state() == VersusMatch.State.RUNNING; }

            @Override public boolean handle(Player player, GameAction action) { return match.handle(player, action); }
        };
    }
}

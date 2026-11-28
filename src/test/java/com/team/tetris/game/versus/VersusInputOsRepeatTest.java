/*
 * 대전 키 반복이 1인용(OS 자동 반복)과 같은 지연·간격을 쓰는지: OS 반복 시각을 재서 맞추는지 확인한다.
 */
package com.team.tetris.game.versus;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.team.tetris.common.battle.Player;
import com.team.tetris.game.GameAction;
import com.team.tetris.game.GameEngine;

import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.Test;

class VersusInputOsRepeatTest {
    private static final int STANDARD = KeyEvent.KEY_LOCATION_STANDARD;
    private static final Map<Integer, PlayerMove> KEYS = Map.of(
            KeyEvent.VK_A, new PlayerMove(Player.P1, GameAction.MOVE_LEFT),
            KeyEvent.VK_LEFT, new PlayerMove(Player.P2, GameAction.MOVE_LEFT));

    private final AtomicLong nanos = new AtomicLong(5_000_000_000L);
    private final List<Timer> timers = new ArrayList<>();
    private final VersusInput input = new VersusInput(new VersusInput.Target() {
        @Override public boolean isRunning() { return true; }

        @Override public boolean handle(Player player, GameAction action) { return true; }
    }, KEYS, () -> {
        Timer timer = new Timer();
        timers.add(timer);
        return timer;
    }, nanos::get);

    private void after(long millis) {
        nanos.addAndGet(millis * 1_000_000L);
    }

    /** 키를 누르고 OS처럼 delay 뒤 interval마다 반복을 보낸 뒤 뗀다. */
    private void holdLikeTheOs(int key, int delay, int interval, int repeats) {
        input.keyPressed(key, STANDARD);
        after(delay);
        input.keyPressed(key, STANDARD);
        for (int i = 1; i < repeats; i++) {
            after(interval);
            input.keyPressed(key, STANDARD);
        }
        input.keyReleased(key, STANDARD);
    }

    @Test
    void beforeMeasuringItUsesTheWindowsDefault() {
        assertEquals(VersusInput.REPEAT_DELAY_MILLIS, input.repeatDelayMillis());
        assertEquals(VersusInput.REPEAT_INTERVAL_MILLIS, input.repeatIntervalMillis());
    }

    @Test
    void theNextHeldKeyRepeatsLikeTheOsDid() {
        holdLikeTheOs(KeyEvent.VK_A, 250, 40, 2);
        assertEquals(250, input.repeatDelayMillis(), "The first measured delay is used as is");
        assertEquals(40, input.repeatIntervalMillis(), "The first measured interval is used as is");

        input.keyPressed(KeyEvent.VK_LEFT, STANDARD);
        Timer timer = timers.getLast();
        assertEquals(250, timer.interval, "Waits as long as the OS before repeating");
        timer.fire();
        assertEquals(40, timer.interval, "Then repeats as fast as the OS");
    }

    @Test
    void onlyTheLastPressedKeyIsMeasuredBecauseTheOsRepeatsOnlyThatOne() {
        input.keyPressed(KeyEvent.VK_A, STANDARD);
        after(80);
        input.keyPressed(KeyEvent.VK_LEFT, STANDARD);   // 이제 OS는 ←만 반복한다
        after(300);
        input.keyPressed(KeyEvent.VK_A, STANDARD);      // A의 늦은 반복은 재지 않는다
        assertEquals(VersusInput.REPEAT_DELAY_MILLIS, input.repeatDelayMillis());

        input.keyPressed(KeyEvent.VK_LEFT, STANDARD);
        assertEquals(300, input.repeatDelayMillis(), "Measured from when ← was pressed");
    }

    @Test
    void implausibleTimesAreIgnored() {
        holdLikeTheOs(KeyEvent.VK_A, 0, 0, 3);               // 한꺼번에 들어온 반복
        holdLikeTheOs(KeyEvent.VK_A, 5_000, 2_000, 2);       // 창이 멈춰 있었다
        assertEquals(VersusInput.REPEAT_DELAY_MILLIS, input.repeatDelayMillis());
        assertEquals(VersusInput.REPEAT_INTERVAL_MILLIS, input.repeatIntervalMillis());
    }

    @Test
    void laterMeasurementsAreSmoothed() {
        holdLikeTheOs(KeyEvent.VK_A, 500, 32, 2);
        holdLikeTheOs(KeyEvent.VK_A, 520, 48, 2);
        assertEquals(510, input.repeatDelayMillis());
        assertEquals(36, input.repeatIntervalMillis());
    }

    private static final class Timer implements GameEngine.DropTimer {
        int interval;
        Runnable tick = () -> { };

        @Override public void start(int intervalMillis, Runnable tick) {
            this.interval = intervalMillis;
            this.tick = tick;
        }

        @Override public void stop() { }

        void fire() {
            tick.run();
        }
    }
}

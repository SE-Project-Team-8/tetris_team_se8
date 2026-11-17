/*
 * GameEngine.SwingDropTimer 테스트. 가짜 시계로 현재 주기의 진행분(elapsedMillis)이 일시정지 동안 고정되고,
 * 줄인 첫 주기(initialDelay)로 다시 시작하면 남은 진행분을 이어 가는지 검증한다. 실제 틱은 오지 않을 만큼 긴 주기를 쓴다.
 */
package com.team.tetris.game;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;

class SwingDropTimerTest {
    /** Long enough that the real Swing timer never fires while a test runs. */
    private static final int INTERVAL = 60_000;
    private final AtomicLong nanos = new AtomicLong(1_000_000_000L);
    private final GameEngine.SwingDropTimer timer = new GameEngine.SwingDropTimer(nanos::get);

    @AfterEach
    void stopTheSwingTimer() {
        timer.stop();
    }

    @Test
    void reportsHowFarTheCurrentIntervalHasRunAndFreezesItWhenStopped() {
        timer.start(INTERVAL, () -> { });
        assertEquals(0, timer.elapsedMillis());
        advance(300);
        assertEquals(300, timer.elapsedMillis());
        timer.stop();
        advance(5_000);
        assertEquals(300, timer.elapsedMillis(), "Stopped time does not count");
    }

    @Test
    void aShortenedFirstIntervalContinuesThePreservedProgress() {
        timer.start(INTERVAL, () -> { });
        advance(300);
        timer.stop();
        advance(5_000);
        timer.start(INTERVAL - timer.elapsedMillis(), INTERVAL, () -> { });
        assertEquals(300, timer.elapsedMillis());
        advance(200);
        timer.stop();
        assertEquals(500, timer.elapsedMillis(), "Progress adds up across several pauses");
    }

    @Test
    void neverReportsAFullIntervalAndRejectsAnImpossibleFirstDelay() {
        timer.start(INTERVAL, () -> { });
        advance(INTERVAL + 5);
        assertEquals(INTERVAL - 1, timer.elapsedMillis(), "A late tick never makes the next delay zero or negative");
        assertThrows(IllegalArgumentException.class, () -> timer.start(INTERVAL + 1, INTERVAL, () -> { }));
        assertThrows(IllegalArgumentException.class, () -> timer.start(-1, INTERVAL, () -> { }));
    }

    @Test
    void customTimersWithoutSupportRestartTheFullInterval() {
        int[] started = new int[1];
        GameEngine.DropTimer legacy = new GameEngine.DropTimer() {
            public void start(int intervalMillis, Runnable tick) { started[0] = intervalMillis; }
            public void stop() { }
        };
        legacy.start(400, 1000, () -> { });
        assertEquals(1000, started[0]);
        assertEquals(0, legacy.elapsedMillis());
    }

    private void advance(long millis) {
        nanos.addAndGet(millis * 1_000_000L);
    }
}

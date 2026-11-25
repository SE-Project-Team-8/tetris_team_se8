/*
 * 대전 회전 키의 누름 반복: 이동보다 늦게 시작하고 느리게 반복해서, 짧게 누르면 한 번만 돈다 (1인 모드와 같은 느낌).
 */
package com.team.tetris.game.versus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.team.tetris.common.battle.Player;
import com.team.tetris.game.GameAction;
import com.team.tetris.game.GameEngine;

class RotationRepeatTest {
    private final List<GameAction> handled = new ArrayList<>();
    private final List<Timer> timers = new ArrayList<>();
    private final VersusInput input = new VersusInput(new VersusInput.Target() {
        @Override public boolean isRunning() { return true; }

        @Override public boolean handle(Player player, GameAction action) {
            handled.add(action);
            return true;
        }
    }, Map.of(KeyEvent.VK_W, new PlayerMove(Player.P1, GameAction.ROTATE_CLOCKWISE),
            KeyEvent.VK_A, new PlayerMove(Player.P1, GameAction.MOVE_LEFT)), () -> {
                Timer timer = new Timer();
                timers.add(timer);
                return timer;
            });

    @Test
    void rotationWaitsLongerAndRepeatsSlowerThanMovement() {
        input.keyPressed(KeyEvent.VK_W, KeyEvent.KEY_LOCATION_STANDARD);
        assertEquals(List.of(GameAction.ROTATE_CLOCKWISE), handled, "A press rotates once right away");
        Timer rotate = timers.getLast();
        assertEquals(VersusInput.ROTATE_REPEAT_DELAY_MILLIS, rotate.interval);
        assertTrue(VersusInput.ROTATE_REPEAT_DELAY_MILLIS > VersusInput.REPEAT_DELAY_MILLIS,
                "A short tap does not rotate twice");
        rotate.fire();
        assertEquals(VersusInput.ROTATE_REPEAT_INTERVAL_MILLIS, rotate.interval, "Held rotation repeats slowly");
        assertTrue(VersusInput.ROTATE_REPEAT_INTERVAL_MILLIS > VersusInput.REPEAT_INTERVAL_MILLIS);
        assertEquals(2, handled.size());

        input.keyPressed(KeyEvent.VK_A, KeyEvent.KEY_LOCATION_STANDARD);
        assertEquals(VersusInput.REPEAT_DELAY_MILLIS, timers.getLast().interval, "Movement keeps its quick repeat");
    }

    private static final class Timer implements GameEngine.DropTimer {
        int interval;
        Runnable tick = () -> { };

        @Override public void start(int intervalMillis, Runnable tick) {
            this.interval = intervalMillis;
            this.tick = tick;
        }

        @Override public void stop() {
            tick = () -> { };
        }

        void fire() {
            tick.run();
        }
    }
}

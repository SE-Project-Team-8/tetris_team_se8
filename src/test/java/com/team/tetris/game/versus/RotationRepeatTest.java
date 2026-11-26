/*
 * 대전 회전 키는 길게 눌러도 한 번만 돈다 (1인 모드와 같은 느낌). 이동 키는 계속 반복한다.
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
    void holdingARotationKeyRotatesOnceButMovementKeepsRepeating() {
        input.keyPressed(KeyEvent.VK_W, KeyEvent.KEY_LOCATION_STANDARD);
        input.keyPressed(KeyEvent.VK_W, KeyEvent.KEY_LOCATION_STANDARD);   // OS 자동 반복
        assertEquals(List.of(GameAction.ROTATE_CLOCKWISE), handled, "A held rotation key rotates once");
        assertTrue(timers.isEmpty(), "No repeat timer for rotation");

        input.keyReleased(KeyEvent.VK_W, KeyEvent.KEY_LOCATION_STANDARD);
        input.keyPressed(KeyEvent.VK_W, KeyEvent.KEY_LOCATION_STANDARD);
        assertEquals(2, handled.size(), "Pressing again rotates again");

        input.keyPressed(KeyEvent.VK_A, KeyEvent.KEY_LOCATION_STANDARD);
        Timer move = timers.getLast();
        assertEquals(VersusInput.REPEAT_DELAY_MILLIS, move.interval, "Movement keeps its quick repeat");
        move.fire();
        assertEquals(VersusInput.REPEAT_INTERVAL_MILLIS, move.interval);
        assertEquals(4, handled.size());
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

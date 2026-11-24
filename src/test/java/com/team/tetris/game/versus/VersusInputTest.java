/*
 * VersusInput 테스트: 키를 주인에게만 전달, OS 자동 반복 무시, 키마다 따로 도는 누름 반복, 하드드롭은 한 번, 떼기·모두 떼기,
 * 키 설정 바꾸기, 대전이 진행 중이 아닐 때 (input.md 2·3절, LB-F18, LB-I01).
 */
package com.team.tetris.game.versus;

import com.team.tetris.block.TetrominoType;
import com.team.tetris.common.battle.Player;
import com.team.tetris.common.battle.VersusMode;
import com.team.tetris.common.events.BoardSnapshot;
import com.team.tetris.game.GameAction;
import com.team.tetris.game.GameEngine;
import org.junit.jupiter.api.Test;

import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static com.team.tetris.game.versus.VersusMatchTest.edt;
import static org.junit.jupiter.api.Assertions.*;

class VersusInputTest {
    private static final Map<Integer, PlayerMove> KEYS = Map.of(
            KeyEvent.VK_A, new PlayerMove(Player.P1, GameAction.MOVE_LEFT),
            KeyEvent.VK_S, new PlayerMove(Player.P1, GameAction.SOFT_DROP),
            KeyEvent.VK_SHIFT, new PlayerMove(Player.P1, GameAction.HARD_DROP),
            KeyEvent.VK_RIGHT, new PlayerMove(Player.P2, GameAction.MOVE_RIGHT),
            KeyEvent.VK_ENTER, new PlayerMove(Player.P2, GameAction.HARD_DROP));

    private final Recorder match = new Recorder();
    private final List<ManualTimer> timers = new ArrayList<>();
    private final VersusInput input = new VersusInput(match, KEYS, () -> {
        ManualTimer timer = new ManualTimer();
        timers.add(timer);
        return timer;
    });

    @Test
    void eachKeyReachesOnlyItsOwnPlayer() {
        assertTrue(input.keyPressed(KeyEvent.VK_A));
        assertTrue(input.keyPressed(KeyEvent.VK_RIGHT));
        assertEquals(List.of(move(Player.P1, GameAction.MOVE_LEFT), move(Player.P2, GameAction.MOVE_RIGHT)),
                match.moves);
    }

    @Test
    void otherKeysAreLeftForTheScreen() {
        assertFalse(input.keyPressed(KeyEvent.VK_P), "pause is the screen's key");
        assertFalse(input.keyPressed(KeyEvent.VK_ESCAPE), "so is quit");
        assertFalse(input.keyPressed(KeyEvent.VK_SPACE));
        input.keyReleased(KeyEvent.VK_P);
        assertEquals(List.of(), match.moves);
    }

    @Test
    void eitherShiftKeyIsTheSameKey() {
        // 왼쪽·오른쪽 Shift는 같은 키 코드로 온다 (D-13).
        assertTrue(input.keyPressed(KeyEvent.VK_SHIFT));
        assertEquals(List.of(move(Player.P1, GameAction.HARD_DROP)), match.moves);
    }

    @Test
    void theOperatingSystemsRepeatOfAHeldKeyIsIgnored() {
        input.keyPressed(KeyEvent.VK_A);
        assertTrue(input.keyPressed(KeyEvent.VK_A), "still ours, but not a new press");
        assertTrue(input.keyPressed(KeyEvent.VK_A));
        assertEquals(1, match.moves.size());
        assertEquals(1, timers.size(), "one repeat timer for the held key");
    }

    @Test
    void aHeldKeyRepeatsAfterADelayAndThenFaster() {
        input.keyPressed(KeyEvent.VK_A);
        ManualTimer timer = timers.getFirst();
        assertEquals(VersusInput.REPEAT_DELAY_MILLIS, timer.intervalMillis);
        assertEquals(170, VersusInput.REPEAT_DELAY_MILLIS);
        assertEquals(50, VersusInput.REPEAT_INTERVAL_MILLIS);

        timer.fire();
        assertEquals(VersusInput.REPEAT_INTERVAL_MILLIS, timer.intervalMillis, "after the first repeat");
        timer.fire();
        timer.fire();
        assertEquals(4, match.moves.size());
        assertTrue(match.moves.stream().allMatch(m -> m.equals(move(Player.P1, GameAction.MOVE_LEFT))));
    }

    @Test
    void oneHeldKeyKeepsRepeatingWhileTheOtherPlayerPressesKeys() {
        input.keyPressed(KeyEvent.VK_A);
        ManualTimer p1 = timers.getFirst();
        p1.fire();
        input.keyPressed(KeyEvent.VK_RIGHT);
        ManualTimer p2 = timers.get(1);
        assertNotSame(p1, p2);
        p1.fire();
        p2.fire();
        p1.fire();
        input.keyReleased(KeyEvent.VK_RIGHT);
        p1.fire();
        assertEquals(List.of(
                move(Player.P1, GameAction.MOVE_LEFT), move(Player.P1, GameAction.MOVE_LEFT),
                move(Player.P2, GameAction.MOVE_RIGHT), move(Player.P1, GameAction.MOVE_LEFT),
                move(Player.P2, GameAction.MOVE_RIGHT), move(Player.P1, GameAction.MOVE_LEFT),
                move(Player.P1, GameAction.MOVE_LEFT)), match.moves);
        assertTrue(p1.running);
        assertFalse(p2.running);
    }

    @Test
    void releasingAKeyStopsItsRepeat() {
        input.keyPressed(KeyEvent.VK_S);
        ManualTimer timer = timers.getFirst();
        input.keyReleased(KeyEvent.VK_S);
        assertFalse(timer.running);
        timer.tick.run();   // a tick that was already on its way
        assertEquals(1, match.moves.size());

        assertTrue(input.keyPressed(KeyEvent.VK_S), "pressing again is a new press");
        assertEquals(2, match.moves.size());
        timer.tick.run();   // the old timer's late tick does not drive the new press
        assertEquals(2, match.moves.size());
    }

    @Test
    void aHardDropHappensOncePerPress() {
        input.keyPressed(KeyEvent.VK_ENTER);
        input.keyPressed(KeyEvent.VK_ENTER);
        input.keyPressed(KeyEvent.VK_ENTER);
        assertEquals(List.of(move(Player.P2, GameAction.HARD_DROP)), match.moves);
        assertEquals(List.of(), timers, "nothing to repeat");
        input.keyReleased(KeyEvent.VK_ENTER);
        input.keyPressed(KeyEvent.VK_ENTER);
        assertEquals(2, match.moves.size());
    }

    @Test
    void releasingEverythingStopsEveryRepeat() {
        input.keyPressed(KeyEvent.VK_A);
        input.keyPressed(KeyEvent.VK_RIGHT);
        input.releaseAll();
        assertTrue(timers.stream().noneMatch(timer -> timer.running));
        timers.forEach(timer -> timer.tick.run());
        assertEquals(2, match.moves.size());
        assertTrue(input.keyPressed(KeyEvent.VK_A), "a key still held counts as a new press");
        assertEquals(3, match.moves.size());
    }

    @Test
    void newKeySettingsTakeEffectAtOnce() {
        input.keyPressed(KeyEvent.VK_A);
        input.setKeys(Map.of(KeyEvent.VK_J, new PlayerMove(Player.P1, GameAction.MOVE_LEFT)));
        assertFalse(timers.getFirst().running, "keys held under the old settings are released");
        assertFalse(input.keyPressed(KeyEvent.VK_A));
        assertTrue(input.keyPressed(KeyEvent.VK_J));
        assertEquals(List.of(move(Player.P1, GameAction.MOVE_LEFT), move(Player.P1, GameAction.MOVE_LEFT)),
                match.moves);
    }

    @Test
    void movesAreIgnoredWhileTheMatchIsNotRunning() {
        match.running = false;
        assertFalse(input.keyPressed(KeyEvent.VK_A), "a paused or finished match leaves the key to the screen");
        assertEquals(List.of(), match.moves);
    }

    @Test
    void pausingStopsAHeldKeyAtItsNextRepeat() {
        input.keyPressed(KeyEvent.VK_A);
        ManualTimer timer = timers.getFirst();
        match.running = false;
        timer.fire();
        assertFalse(timer.running);
        assertEquals(1, match.moves.size());

        match.running = true;
        assertTrue(input.keyPressed(KeyEvent.VK_A), "after resuming, the OS repeat starts a fresh press");
        assertEquals(2, match.moves.size());
    }

    @Test
    void playerMovesExcludeTheSharedKeys() {
        assertThrows(IllegalArgumentException.class, () -> new PlayerMove(Player.P1, GameAction.PAUSE));
        assertThrows(IllegalArgumentException.class, () -> new PlayerMove(Player.P2, GameAction.QUIT));
        assertFalse(new PlayerMove(Player.P1, GameAction.HARD_DROP).repeats());
        assertTrue(new PlayerMove(Player.P1, GameAction.ROTATE_CLOCKWISE).repeats());
    }

    @Test
    void keysDriveARealMatch() throws Exception {
        VersusMatchTest.Fixture f = new VersusMatchTest.Fixture();
        edt(() -> {
            VersusInput real = new VersusInput(f.match, KEYS, ManualTimer::new);
            assertFalse(real.keyPressed(KeyEvent.VK_A), "nothing to move before the match starts");
            f.match.start(VersusMode.NORMAL);
            BoardSnapshot p2Before = f.views.get(Player.P2).board;
            assertTrue(real.keyPressed(KeyEvent.VK_A));
            assertEquals(TetrominoType.O.id(), f.views.get(Player.P1).board.cells()[0][3], "P1's O moved left");
            assertSame(p2Before, f.views.get(Player.P2).board);
            real.keyReleased(KeyEvent.VK_A);
        });
    }

    private static PlayerMove move(Player player, GameAction action) {
        return new PlayerMove(player, action);
    }

    /** 받은 조작을 순서대로 적는 대전. */
    private static final class Recorder implements VersusInput.Target {
        final List<PlayerMove> moves = new ArrayList<>();
        boolean running = true;

        @Override public boolean isRunning() { return running; }

        @Override public boolean handle(Player player, GameAction action) {
            moves.add(new PlayerMove(player, action));
            return true;
        }
    }

    /** 테스트가 직접 울리는 타이머. */
    private static final class ManualTimer implements GameEngine.DropTimer {
        Runnable tick = () -> { };
        int intervalMillis;
        boolean running;

        @Override public void start(int intervalMillis, Runnable tick) {
            this.intervalMillis = intervalMillis;
            this.tick = tick;
            running = true;
        }

        @Override public void stop() { running = false; }

        /** 돌고 있으면 한 번 울린다. */
        void fire() {
            if (running) tick.run();
        }
    }
}

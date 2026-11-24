/*
 * VersusKeyBindings 테스트: 기본값, 키 찾기, 열 개의 키가 서로 다름, 1인용 키와의 중복 허용, 일시정지·나가기 키와의 겹침
 * (input.md 1·4·5절, D-13). GameSettings가 대전 키를 들고 다니는지도 함께 본다.
 */
package com.team.tetris.settings;

import com.team.tetris.common.Difficulty;
import com.team.tetris.common.battle.Player;
import com.team.tetris.game.GameAction;
import org.junit.jupiter.api.Test;

import java.awt.event.KeyEvent;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class VersusKeyBindingsTest {
    private static final VersusKeyBindings DEFAULTS = VersusKeyBindings.defaults();

    @Test
    void defaultsSplitTheKeyboardBetweenTheTwoPlayers() {
        assertEquals(KeyEvent.VK_A, DEFAULTS.keyFor(Player.P1, GameAction.MOVE_LEFT));
        assertEquals(KeyEvent.VK_D, DEFAULTS.keyFor(Player.P1, GameAction.MOVE_RIGHT));
        assertEquals(KeyEvent.VK_S, DEFAULTS.keyFor(Player.P1, GameAction.SOFT_DROP));
        assertEquals(KeyEvent.VK_W, DEFAULTS.keyFor(Player.P1, GameAction.ROTATE_CLOCKWISE));
        assertEquals(KeyEvent.VK_SHIFT, DEFAULTS.keyFor(Player.P1, GameAction.HARD_DROP));
        assertEquals(KeyEvent.VK_LEFT, DEFAULTS.keyFor(Player.P2, GameAction.MOVE_LEFT));
        assertEquals(KeyEvent.VK_RIGHT, DEFAULTS.keyFor(Player.P2, GameAction.MOVE_RIGHT));
        assertEquals(KeyEvent.VK_DOWN, DEFAULTS.keyFor(Player.P2, GameAction.SOFT_DROP));
        assertEquals(KeyEvent.VK_UP, DEFAULTS.keyFor(Player.P2, GameAction.ROTATE_CLOCKWISE));
        assertEquals(KeyEvent.VK_ENTER, DEFAULTS.keyFor(Player.P2, GameAction.HARD_DROP));
    }

    @Test
    void eachPlayerHasTheFiveMovesButNotPauseOrQuit() {
        assertEquals(List.of(GameAction.MOVE_LEFT, GameAction.MOVE_RIGHT, GameAction.SOFT_DROP,
                GameAction.ROTATE_CLOCKWISE, GameAction.HARD_DROP), List.copyOf(VersusKeyBindings.PLAYER_ACTIONS));
        assertEquals(Set.of(GameAction.PAUSE, GameAction.QUIT), VersusKeyBindings.SHARED_ACTIONS);
        for (Player player : Player.values()) {
            assertEquals(VersusKeyBindings.PLAYER_ACTIONS, DEFAULTS.keys(player).keySet());
        }
        assertThrows(IllegalArgumentException.class, () -> DEFAULTS.keyFor(Player.P1, GameAction.PAUSE));
        assertThrows(IllegalArgumentException.class, () -> DEFAULTS.withKey(Player.P1, GameAction.QUIT, KeyEvent.VK_Q));
    }

    @Test
    void aKeyTellsWhoseMoveItIs() {
        assertEquals(Optional.of(new VersusKeyBindings.Assignment(Player.P1, GameAction.HARD_DROP)),
                DEFAULTS.assignmentFor(KeyEvent.VK_SHIFT));
        assertEquals(Optional.of(new VersusKeyBindings.Assignment(Player.P2, GameAction.MOVE_LEFT)),
                DEFAULTS.assignmentFor(KeyEvent.VK_LEFT));
        assertEquals(Optional.empty(), DEFAULTS.assignmentFor(KeyEvent.VK_P));
        assertTrue(DEFAULTS.uses(KeyEvent.VK_ENTER));
        assertFalse(DEFAULTS.uses(KeyEvent.VK_SPACE));
    }

    @Test
    void theTenKeysMustAllDiffer() {
        assertThrows(IllegalArgumentException.class,
                () -> DEFAULTS.withKey(Player.P1, GameAction.MOVE_LEFT, KeyEvent.VK_D), "same player");
        assertThrows(IllegalArgumentException.class,
                () -> DEFAULTS.withKey(Player.P2, GameAction.HARD_DROP, KeyEvent.VK_SHIFT), "across players");

        VersusKeyBindings changed = DEFAULTS.withKey(Player.P2, GameAction.HARD_DROP, KeyEvent.VK_NUMPAD0);
        assertEquals(KeyEvent.VK_NUMPAD0, changed.keyFor(Player.P2, GameAction.HARD_DROP));
        assertEquals(KeyEvent.VK_ENTER, DEFAULTS.keyFor(Player.P2, GameAction.HARD_DROP), "values are immutable");
        assertEquals(DEFAULTS.p1(), changed.p1());
    }

    @Test
    void everyMoveNeedsOneValidKey() {
        Map<GameAction, Integer> missing = new EnumMap<>(DEFAULTS.p1());
        missing.remove(GameAction.SOFT_DROP);
        assertThrows(IllegalArgumentException.class, () -> new VersusKeyBindings(missing, DEFAULTS.p2()));

        Map<GameAction, Integer> extra = new EnumMap<>(DEFAULTS.p1());
        extra.put(GameAction.PAUSE, KeyEvent.VK_Q);
        assertThrows(IllegalArgumentException.class, () -> new VersusKeyBindings(extra, DEFAULTS.p2()));

        Map<GameAction, Integer> nullKey = new HashMap<>(DEFAULTS.p1());
        nullKey.put(GameAction.SOFT_DROP, null);
        assertThrows(IllegalArgumentException.class, () -> new VersusKeyBindings(nullKey, DEFAULTS.p2()));

        assertThrows(IllegalArgumentException.class,
                () -> DEFAULTS.withKey(Player.P1, GameAction.SOFT_DROP, KeyEvent.VK_UNDEFINED));
        assertThrows(IllegalArgumentException.class,
                () -> DEFAULTS.withKey(Player.P1, GameAction.SOFT_DROP, 0x10000));
        assertThrows(NullPointerException.class, () -> new VersusKeyBindings(null, DEFAULTS.p2()));
    }

    @Test
    void theMapsCannotBeChangedFromOutside() {
        Map<GameAction, Integer> source = new EnumMap<>(DEFAULTS.p1());
        VersusKeyBindings bindings = new VersusKeyBindings(source, DEFAULTS.p2());
        source.put(GameAction.MOVE_LEFT, KeyEvent.VK_Z);
        assertEquals(KeyEvent.VK_A, bindings.keyFor(Player.P1, GameAction.MOVE_LEFT));
        assertThrows(UnsupportedOperationException.class,
                () -> bindings.p1().put(GameAction.MOVE_LEFT, KeyEvent.VK_Z));
        assertEquals(new VersusKeyBindings(DEFAULTS.p1(), DEFAULTS.p2()), DEFAULTS, "compared by value");
    }

    // ---- GameSettings

    @Test
    void settingsCarryDefaultVersusKeysThatMayRepeatSinglePlayerKeys() {
        GameSettings settings = GameSettings.defaults();
        assertEquals(DEFAULTS, settings.versusKeys());
        assertEquals(KeyEvent.VK_LEFT, settings.keyBindings().keyFor(GameAction.MOVE_LEFT),
                "P2's left arrow is also the single-player left key, which is allowed");
        assertEquals(Set.of(), settings.versusKeyClashes());
        assertEquals(DEFAULTS, new GameSettings(GameSettings.ScreenSize.SMALL, GameSettings.ColorBlindMode.OFF,
                KeyBindings.defaults(), Difficulty.HARD).versusKeys());
    }

    @Test
    void changingOtherSettingsKeepsTheVersusKeys() {
        VersusKeyBindings custom = DEFAULTS.withKey(Player.P1, GameAction.MOVE_LEFT, KeyEvent.VK_J);
        GameSettings settings = GameSettings.defaults().withVersusKeys(custom)
                .withScreenSize(GameSettings.ScreenSize.LARGE)
                .withColorBlindMode(GameSettings.ColorBlindMode.RED_GREEN)
                .withDifficulty(Difficulty.EASY)
                .withKeyBindings(KeyBindings.defaults().withKey(GameAction.HARD_DROP, KeyEvent.VK_X));
        assertEquals(custom, settings.versusKeys());
    }

    @Test
    void versusKeysCannotTakeThePauseOrQuitKey() {
        GameSettings settings = GameSettings.defaults();
        assertThrows(IllegalArgumentException.class, () -> settings.withVersusKeys(
                DEFAULTS.withKey(Player.P1, GameAction.HARD_DROP, KeyEvent.VK_P)), "P pauses both players");
        assertThrows(IllegalArgumentException.class, () -> settings.withVersusKeys(
                DEFAULTS.withKey(Player.P2, GameAction.SOFT_DROP, KeyEvent.VK_ESCAPE)), "Esc quits");
        assertThrows(NullPointerException.class, () -> settings.withVersusKeys(null));
    }

    @Test
    void aSinglePlayerPauseKeyThatLaterMatchesAVersusKeyIsReportedNotRejected() {
        GameSettings settings = GameSettings.defaults()
                .withKeyBindings(KeyBindings.defaults().withKey(GameAction.PAUSE, KeyEvent.VK_ENTER));
        assertEquals(Set.of(GameAction.PAUSE), settings.versusKeyClashes());

        GameSettings both = settings.withKeyBindings(KeyBindings.defaults()
                .withKey(GameAction.PAUSE, KeyEvent.VK_ENTER).withKey(GameAction.QUIT, KeyEvent.VK_A));
        assertEquals(Set.of(GameAction.PAUSE, GameAction.QUIT), both.versusKeyClashes());
    }
}

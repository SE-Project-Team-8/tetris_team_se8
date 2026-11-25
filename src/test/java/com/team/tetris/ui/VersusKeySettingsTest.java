/*
 * 설정 화면의 대전 키 영역 (docs/battle/ui-handoff.md 7단계): 1인용 / 대전 P1 / 대전 P2 대상 선택, 다섯 칸, 좌우 Shift 구분 저장.
 */
package com.team.tetris.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

import java.awt.Component;
import java.awt.Container;
import java.awt.GraphicsEnvironment;
import java.awt.event.KeyEvent;
import java.lang.reflect.InvocationTargetException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import javax.swing.AbstractButton;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.team.tetris.common.Screen;
import com.team.tetris.common.battle.Player;
import com.team.tetris.game.GameAction;
import com.team.tetris.game.versus.SidedKey;
import com.team.tetris.scoreboard.ScoreboardRepository;
import com.team.tetris.settings.GameSettings;
import com.team.tetris.settings.SettingsRepository;
import com.team.tetris.settings.VersusKeyBindings;

class VersusKeySettingsTest {
    @TempDir
    Path files;

    @BeforeEach
    void requireDisplay() {
        assumeFalse(GraphicsEnvironment.isHeadless(), "ScreenRouter is a window");
    }

    @Test
    void eachPlayerHasFiveKeysThatAreSavedWithTheirSide() throws Exception {
        AtomicReference<GameSettings> saved = new AtomicReference<>();
        CountDownLatch savedLatch = new CountDownLatch(1);
        ScreenRouter[] router = new ScreenRouter[1];
        edt(() -> {
            router[0] = new ScreenRouter();
            SettingsScreen settings = new SettingsScreen(router[0],
                    new SettingsRepository(files.resolve("settings.properties")),
                    new ScoreboardRepository(files.resolve("scores.properties")), GameSettings::defaults,
                    value -> {
                        saved.set(value);
                        savedLatch.countDown();
                    }, () -> { });
            router[0].addScreen(ScreenRouter.MAIN_MENU, stub());
            router[0].addScreen(ScreenRouter.SETTINGS, settings);
            router[0].setVisible(true);
            router[0].showScreen(ScreenRouter.MAIN_MENU);
            router[0].showScreen(ScreenRouter.SETTINGS);
            assertEquals(GameAction.values().length, visible(settings, "KeyChip").size(), "Single-player keys first");

            Component target = visible(settings, "SegmentedControl").get(3);
            press(target, KeyEvent.VK_RIGHT, KeyEvent.KEY_LOCATION_STANDARD);   // 1인용 → 대전 P1
            List<Component> p1 = visible(settings, "KeyChip");
            assertEquals(VersusKeyBindings.PLAYER_ACTIONS.size(), p1.size(),
                    "A versus player shows only its five keys, without pause and quit");
            // 대전 칸 순서: 왼쪽 이동, 오른쪽 이동, 소프트 드롭, 하드 드롭, 회전
            press(p1.get(3), KeyEvent.VK_Q, KeyEvent.KEY_LOCATION_STANDARD);

            press(target, KeyEvent.VK_RIGHT, KeyEvent.KEY_LOCATION_STANDARD);   // 대전 P1 → 대전 P2
            List<Component> p2 = visible(settings, "KeyChip");
            press(p2.get(0), KeyEvent.VK_J, KeyEvent.KEY_LOCATION_STANDARD);
            // 오른쪽 Shift는 P1의 왼쪽 Shift와 다른 키로 받는다. 수정 키라 눌렀다 뗄 때 지정된다.
            press(p2.get(3), KeyEvent.VK_SHIFT, KeyEvent.KEY_LOCATION_RIGHT);
            release(p2.get(3), KeyEvent.VK_SHIFT, KeyEvent.KEY_LOCATION_RIGHT);

            press(target, KeyEvent.VK_LEFT, KeyEvent.KEY_LOCATION_STANDARD);
            press(target, KeyEvent.VK_LEFT, KeyEvent.KEY_LOCATION_STANDARD);   // 다시 1인용
            assertEquals(GameAction.values().length, visible(settings, "KeyChip").size());
            saveButton(settings).doClick();
        });
        try {
            assertTrue(savedLatch.await(5, TimeUnit.SECONDS), "Settings were saved");
        } finally {
            edt(() -> router[0].dispose());
        }
        VersusKeyBindings keys = saved.get().versusKeys();
        assertEquals(KeyEvent.VK_Q, keys.keyFor(Player.P1, GameAction.HARD_DROP));
        assertEquals(KeyEvent.VK_A, keys.keyFor(Player.P1, GameAction.MOVE_LEFT), "Untouched keys stay");
        assertEquals(KeyEvent.VK_J, keys.keyFor(Player.P2, GameAction.MOVE_LEFT));
        assertEquals(SidedKey.right(KeyEvent.VK_SHIFT), keys.keyFor(Player.P2, GameAction.HARD_DROP),
                "The right Shift is saved as the right one");
        assertEquals(GameSettings.defaults().keyBindings(), saved.get().keyBindings(), "Single-player keys unchanged");
    }

    private static List<Component> visible(Container root, String simpleName) {
        List<Component> found = new ArrayList<>();
        collect(root, simpleName, found);
        return found;
    }

    private static void collect(Container root, String simpleName, List<Component> found) {
        for (Component child : root.getComponents()) {
            if (!child.isVisible()) continue;
            if (child.getClass().getSimpleName().equals(simpleName)) found.add(child);
            if (child instanceof Container container) collect(container, simpleName, found);
        }
    }

    private static AbstractButton saveButton(Container root) {
        for (Component button : visible(root, "PillButton")) {
            if ("저장".equals(((AbstractButton) button).getText())) return (AbstractButton) button;
        }
        throw new AssertionError("No save button");
    }

    private static void press(Component target, int keyCode, int location) {
        target.dispatchEvent(new KeyEvent(target, KeyEvent.KEY_PRESSED, System.currentTimeMillis(), 0, keyCode,
                KeyEvent.CHAR_UNDEFINED, location));
    }

    private static void release(Component target, int keyCode, int location) {
        target.dispatchEvent(new KeyEvent(target, KeyEvent.KEY_RELEASED, System.currentTimeMillis(), 0, keyCode,
                KeyEvent.CHAR_UNDEFINED, location));
    }

    private static Screen stub() {
        JPanel panel = new JPanel();
        return new Screen() {
            @Override public JPanel getPanel() { return panel; }
            @Override public void onShow() { }
            @Override public void onHide() { }
        };
    }

    private static void edt(Runnable action) throws Exception {
        try {
            SwingUtilities.invokeAndWait(action);
        } catch (InvocationTargetException failure) {
            if (failure.getCause() instanceof AssertionError assertion) throw assertion;
            if (failure.getCause() instanceof Exception exception) throw exception;
            throw failure;
        }
    }
}

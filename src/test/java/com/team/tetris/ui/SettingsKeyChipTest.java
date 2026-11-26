package com.team.tetris.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
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
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.team.tetris.common.Screen;
import com.team.tetris.game.GameAction;
import com.team.tetris.scoreboard.ScoreboardRepository;
import com.team.tetris.settings.GameSettings;
import com.team.tetris.settings.KeyBindings;
import com.team.tetris.settings.SettingsRepository;
import com.team.tetris.ui.theme.ThemedDialog;

/**
 * 설정의 조작 키 칸: 방향키로 칸을 옮기고, 칸에서 Enter·Space를 누르면 키 변경 팝업이 열려 다음에 누른 키가 새 키가 된다.
 * 팝업은 Shift·Tab 같은 키도 받고, 다른 동작의 키와 겹치면 받지 않는다.
 */
class SettingsKeyChipTest {
    @TempDir
    Path files;

    @BeforeEach
    void requireDisplay() {
        assumeFalse(GraphicsEnvironment.isHeadless(), "ScreenRouter is a window");
    }

    @AfterEach
    void restorePrompt() {
        SettingsScreen.setKeyPromptForTests(null);
    }

    @Test
    void enterOnAKeyOpensThePopupAndThePressedKeyBecomesTheControlKey() throws Exception {
        List<Integer> openingKeys = new ArrayList<>();
        KeyBindings saved = savedAfter((settings, chips) -> {
            prompt(KeyEvent.VK_K, openingKeys);
            press(chips.get(0), KeyEvent.VK_ENTER);
            prompt(KeyEvent.VK_SHIFT, openingKeys);
            press(chips.get(1), KeyEvent.VK_SPACE);
        });
        assertEquals(List.of(KeyEvent.VK_ENTER, KeyEvent.VK_SPACE), openingKeys, "Enter and Space both open it");
        assertEquals(KeyEvent.VK_K, saved.keyFor(GameAction.MOVE_LEFT));
        assertEquals(KeyEvent.VK_SHIFT, saved.keyFor(GameAction.MOVE_RIGHT), "Shift alone can be a control key");
    }

    @Test
    void otherKeysOnAKeyChipDoNotChangeIt() throws Exception {
        KeyBindings saved = savedAfter((settings, chips) -> {
            SettingsScreen.setKeyPromptForTests((parent, title, message, openingKey, problem) -> {
                throw new AssertionError("Only Enter or Space opens the popup");
            });
            press(chips.get(0), KeyEvent.VK_K);
            press(chips.get(0), KeyEvent.VK_TAB);
        });
        assertEquals(KeyBindings.defaults().keys(), saved.keys());
    }

    @Test
    void aKeyUsedByAnotherActionIsRejectedInsideThePopup() throws Exception {
        String[] problem = {null};
        KeyBindings saved = savedAfter((settings, chips) -> {
            SettingsScreen.setKeyPromptForTests((parent, title, message, openingKey, check) -> {
                problem[0] = check.apply(new ThemedDialog.KeyPress(KeyEvent.VK_SPACE, KeyEvent.KEY_LOCATION_STANDARD));
                return null;   // 그다음 Esc로 닫았다
            });
            press(chips.get(0), KeyEvent.VK_ENTER);
        });
        assertEquals("이미 '하드 드롭'에 쓰이는 키입니다", problem[0]);
        assertEquals(KeyBindings.defaults().keys(), saved.keys(), "Closing the popup keeps the old key");
    }

    @Test
    void arrowKeysMoveBetweenRowsAndBetweenTheTwoKeyColumns() throws Exception {
        savedAfter((settings, chips) -> {
            List<List<JComponent>> rows = settings.navigationRows();
            assertEquals(4 + GameAction.values().length / 2 + 1, rows.size(),
                    "size, difficulty, colour, key target, key pairs, buttons");
            assertSame(chips.get(0), rows.get(4).get(0));
            assertSame(chips.get(1), rows.get(4).get(1));

            Component at = rows.get(0).get(0);   // 화면 크기
            List<String> moves = new ArrayList<>();
            for (int step = 0; step < 4; step++) at = settings.navigationTarget(at, 1, 0);
            moves.add(name(at, chips));
            at = settings.navigationTarget(at, 0, 1);
            moves.add(name(at, chips));
            at = settings.navigationTarget(at, 0, 1);
            moves.add(name(at, chips));   // 오른쪽 끝에서는 그대로
            at = settings.navigationTarget(at, 1, 0);
            moves.add(name(at, chips));
            at = settings.navigationTarget(at, 0, -1);
            moves.add(name(at, chips));
            for (int step = 0; step < 10; step++) at = settings.navigationTarget(at, 1, 0);
            moves.add(name(at, chips));
            at = settings.navigationTarget(at, -1, 0);
            moves.add(name(at, chips));
            assertEquals(List.of("chip 0", "chip 1", "chip 1", "chip 3", "chip 2", "button", "chip 6"), moves);
        });
    }

    @Test
    void realArrowKeyEventsMoveTheSelectionAndLeftRightChangeValuesOnTheTopRows() throws Exception {
        savedAfter((settings, chips) -> {
            List<List<JComponent>> rows = settings.navigationRows();
            JComponent screenSize = rows.get(0).get(0);
            JComponent keyTarget = rows.get(3).get(0);
            JComponent saveButton = rows.get(rows.size() - 1).get(0);

            press(screenSize, KeyEvent.VK_DOWN);
            assertSame(rows.get(1).get(0), settings.lastMoveTarget, "↓ on screen size goes to difficulty");
            press(keyTarget, KeyEvent.VK_DOWN);
            assertSame(chips.get(0), settings.lastMoveTarget, "↓ on the key target goes to the first key");
            press(chips.get(0), KeyEvent.VK_RIGHT);
            assertSame(chips.get(1), settings.lastMoveTarget, "→ on a key goes to the key beside it");
            press(chips.get(1), KeyEvent.VK_UP);
            assertSame(keyTarget, settings.lastMoveTarget);
            press(saveButton, KeyEvent.VK_UP);
            assertSame(chips.get(6), settings.lastMoveTarget, "↑ on the buttons goes back to the keys");

            settings.lastMoveTarget = null;
            press(screenSize, KeyEvent.VK_LEFT);
            assertNull(settings.lastMoveTarget, "←→ on a top row changes its value instead of moving");

            // 아무 항목에도 포커스가 없을 때(설정을 막 열었을 때 등) 방향키는 첫 줄부터 고른다.
            press(settings, KeyEvent.VK_DOWN);
            assertSame(screenSize, settings.lastMoveTarget, "an arrow with nothing selected starts at screen size");
        });
    }

    private static String name(Component component, List<Component> chips) {
        int chip = chips.indexOf(component);
        if (chip >= 0) return "chip " + chip;
        if (component instanceof AbstractButton) return "button";
        return String.valueOf(component);
    }

    private static void prompt(int keyCode, List<Integer> openingKeys) {
        SettingsScreen.setKeyPromptForTests((parent, title, message, openingKey, problem) -> {
            openingKeys.add(openingKey);
            ThemedDialog.KeyPress pressed = new ThemedDialog.KeyPress(keyCode, KeyEvent.KEY_LOCATION_STANDARD);
            assertNull(problem.apply(pressed));
            return pressed;
        });
    }

    /** 설정 화면을 열어 키 칸을 다룬 뒤 저장을 눌러, 저장된 조작 키를 돌려준다. */
    private KeyBindings savedAfter(java.util.function.BiConsumer<SettingsScreen, List<Component>> typing)
            throws Exception {
        AtomicReference<GameSettings> updated = new AtomicReference<>();
        CountDownLatch savedLatch = new CountDownLatch(1);
        ScreenRouter[] router = new ScreenRouter[1];
        edt(() -> {
            router[0] = new ScreenRouter();
            SettingsScreen settings = new SettingsScreen(router[0],
                    new SettingsRepository(files.resolve("settings.properties")),
                    new ScoreboardRepository(files.resolve("scores.properties")), GameSettings::defaults,
                    value -> {
                        updated.set(value);
                        savedLatch.countDown();
                    }, () -> { });
            router[0].addScreen(ScreenRouter.MAIN_MENU, stub());
            router[0].addScreen(ScreenRouter.SETTINGS, settings);
            router[0].setVisible(true);
            router[0].showScreen(ScreenRouter.MAIN_MENU);
            router[0].showScreen(ScreenRouter.SETTINGS);

            List<Component> chips = new ArrayList<>();
            collect(settings, "KeyChip", chips);
            assertEquals(GameAction.values().length, chips.size());
            typing.accept(settings, chips);
            saveButton(settings).doClick();
        });
        try {
            assertTrue(savedLatch.await(5, TimeUnit.SECONDS), "Settings were saved");
        } finally {
            edt(() -> router[0].dispose());
        }
        return updated.get().keyBindings();
    }

    private static AbstractButton saveButton(Container root) {
        List<Component> buttons = new ArrayList<>();
        collect(root, "PillButton", buttons);
        for (Component button : buttons) {
            if ("저장".equals(((AbstractButton) button).getText())) return (AbstractButton) button;
        }
        throw new AssertionError("No save button");
    }

    private static void collect(Container root, String simpleName, List<Component> found) {
        for (Component child : root.getComponents()) {
            // 숨겨진 대전 키 칸(1인용을 보고 있을 때)은 세지 않는다.
            if (!child.isVisible()) continue;
            if (child.getClass().getSimpleName().equals(simpleName)) found.add(child);
            if (child instanceof Container container) collect(container, simpleName, found);
        }
    }

    private static Screen stub() {
        JPanel panel = new JPanel();
        return new Screen() {
            public JPanel getPanel() { return panel; }
            public void onShow() { }
            public void onHide() { }
        };
    }

    private static void press(Component target, int keyCode) {
        target.dispatchEvent(new KeyEvent(target, KeyEvent.KEY_PRESSED, System.currentTimeMillis(), 0,
                keyCode, KeyEvent.CHAR_UNDEFINED));
        target.dispatchEvent(new KeyEvent(target, KeyEvent.KEY_RELEASED, System.currentTimeMillis(), 0,
                keyCode, KeyEvent.CHAR_UNDEFINED));
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

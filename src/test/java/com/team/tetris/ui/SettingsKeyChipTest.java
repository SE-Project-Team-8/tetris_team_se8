package com.team.tetris.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

import java.awt.Component;
import java.awt.Container;
import java.awt.GraphicsEnvironment;
import java.awt.event.InputEvent;
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
import com.team.tetris.game.GameAction;
import com.team.tetris.scoreboard.ScoreboardRepository;
import com.team.tetris.settings.GameSettings;
import com.team.tetris.settings.KeyBindings;
import com.team.tetris.settings.SettingsRepository;

/** 설정의 조작 키 칸: Shift 같은 수정 키는 단독으로 눌렀다 뗄 때만 지정되고, Shift+Tab은 키를 바꾸지 않는다. */
class SettingsKeyChipTest {
    @TempDir
    Path files;

    @BeforeEach
    void requireDisplay() {
        assumeFalse(GraphicsEnvironment.isHeadless(), "ScreenRouter is a window");
    }

    @Test
    void shiftTabMovesBackWithoutBindingShift() throws Exception {
        KeyBindings saved = savedAfter(chips -> {
            Component second = chips.get(1);
            press(second, KeyEvent.VK_SHIFT, InputEvent.SHIFT_DOWN_MASK);
            press(second, KeyEvent.VK_TAB, InputEvent.SHIFT_DOWN_MASK);
            // Shift+Tab으로 포커스가 이전 칸으로 옮겨 간 뒤 Shift를 뗀다.
            release(chips.get(0), KeyEvent.VK_SHIFT, 0);
        });
        assertEquals(KeyBindings.defaults().keys(), saved.keys(), "Shift+Tab leaves every control key unchanged");
    }

    @Test
    void shiftPressedAndReleasedAloneBecomesTheControlKey() throws Exception {
        GameAction first = GameAction.values()[0];
        KeyBindings saved = savedAfter(chips -> {
            press(chips.get(0), KeyEvent.VK_SHIFT, InputEvent.SHIFT_DOWN_MASK);
            release(chips.get(0), KeyEvent.VK_SHIFT, 0);
        });
        assertEquals(KeyEvent.VK_SHIFT, saved.keyFor(first));
    }

    @Test
    void ordinaryKeysAreStillBoundOnPress() throws Exception {
        GameAction first = GameAction.values()[0];
        KeyBindings saved = savedAfter(chips -> press(chips.get(0), KeyEvent.VK_K, 0));
        assertEquals(KeyEvent.VK_K, saved.keyFor(first));
    }

    /** 설정 화면을 열어 키 칸에 입력한 뒤 저장을 눌러, 저장된 조작 키를 돌려준다. */
    private KeyBindings savedAfter(java.util.function.Consumer<List<Component>> typing) throws Exception {
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
            typing.accept(chips);
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

    private static void press(Component target, int keyCode, int modifiers) {
        target.dispatchEvent(new KeyEvent(target, KeyEvent.KEY_PRESSED, System.currentTimeMillis(), modifiers,
                keyCode, KeyEvent.CHAR_UNDEFINED));
    }

    private static void release(Component target, int keyCode, int modifiers) {
        target.dispatchEvent(new KeyEvent(target, KeyEvent.KEY_RELEASED, System.currentTimeMillis(), modifiers,
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

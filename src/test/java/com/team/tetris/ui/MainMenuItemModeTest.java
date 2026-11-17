package com.team.tetris.ui;

import com.team.tetris.common.Screen;
import org.junit.jupiter.api.Test;

import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import java.awt.GraphicsEnvironment;
import java.awt.event.KeyEvent;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

class MainMenuItemModeTest {
    /** 요구사항 2차 4.1: 시작 메뉴에서 일반 게임과 아이템 모드를 따로 시작한다. */
    @Test
    void secondMenuEntryStartsItemModeAndOthersKeepTheirOrder() throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless(), "The router is a window");
        List<String> started = new ArrayList<>();
        edt(() -> {
            ScreenRouter router = new ScreenRouter();
            try {
                MainMenuScreen menu = new MainMenuScreen(router, () -> started.add("normal"),
                        () -> started.add("item"));
                router.addScreen(ScreenRouter.MAIN_MENU, menu);
                router.addScreen(ScreenRouter.SETTINGS, new BlankScreen());
                router.setVisible(true);
                router.showScreen(ScreenRouter.MAIN_MENU);

                press(menu, KeyEvent.VK_DOWN);
                press(menu, KeyEvent.VK_ENTER);
                press(menu, KeyEvent.VK_UP);
                press(menu, KeyEvent.VK_ENTER);
                assertEquals(List.of("item", "normal"), started);

                press(menu, KeyEvent.VK_S);
                press(menu, KeyEvent.VK_S);
                press(menu, KeyEvent.VK_ENTER);
                assertEquals(ScreenRouter.SETTINGS, router.getCurrentScreenName());
            } finally {
                router.dispose();
            }
        });
    }

    private static void press(MainMenuScreen menu, int keyCode) {
        menu.dispatchEvent(new KeyEvent(menu, KeyEvent.KEY_PRESSED, System.currentTimeMillis(),
                0, keyCode, KeyEvent.CHAR_UNDEFINED));
    }

    private static final class BlankScreen implements Screen {
        private final JPanel panel = new JPanel();
        public JPanel getPanel() { return panel; }
        public void onShow() { }
        public void onHide() { }
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

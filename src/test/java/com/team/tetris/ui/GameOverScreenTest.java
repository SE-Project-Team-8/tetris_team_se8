package com.team.tetris.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.awt.Component;
import java.awt.event.KeyEvent;
import java.lang.reflect.InvocationTargetException;

import javax.swing.JPanel;
import javax.swing.SwingUtilities;

import org.junit.jupiter.api.Test;

import com.team.tetris.common.Screen;

class GameOverScreenTest {
    @Test
    void enterRunsTheFirstButtonByDefault() throws Exception {
        assertEquals(ScreenRouter.NAME_INPUT, screenAfter(true));
        assertEquals(ScreenRouter.SCOREBOARD, screenAfter(false));
    }

    @Test
    void arrowKeysChooseTheButtonThatEnterRuns() throws Exception {
        assertEquals(ScreenRouter.MAIN_MENU, screenAfter(true, KeyEvent.VK_RIGHT));
        assertEquals(ScreenRouter.NAME_INPUT, screenAfter(true, KeyEvent.VK_RIGHT, KeyEvent.VK_LEFT));
    }

    private static String screenAfter(boolean qualifies, int... arrows) throws Exception {
        String[] current = new String[1];
        edt(() -> {
            ScreenRouter router = new ScreenRouter();
            try {
                GameOverScreen over = new GameOverScreen(router);
                over.setGameResult(120, 1, 0, qualifies);
                router.addScreen(ScreenRouter.GAME_OVER, over);
                router.addScreen(ScreenRouter.NAME_INPUT, stub());
                router.addScreen(ScreenRouter.SCOREBOARD, stub());
                router.addScreen(ScreenRouter.MAIN_MENU, stub());
                router.setVisible(true);
                router.showScreen(ScreenRouter.GAME_OVER);
                for (int arrow : arrows) tap(over, arrow);
                tap(over, KeyEvent.VK_ENTER);
                current[0] = router.getCurrentScreenName();
            } finally {
                router.dispose();
            }
        });
        return current[0];
    }

    private static Screen stub() {
        JPanel panel = new JPanel();
        return new Screen() {
            public JPanel getPanel() { return panel; }
            public void onShow() { }
            public void onHide() { }
        };
    }

    private static void tap(Component target, int keyCode) {
        target.dispatchEvent(new KeyEvent(target, KeyEvent.KEY_PRESSED, System.currentTimeMillis(), 0, keyCode,
                KeyEvent.CHAR_UNDEFINED));
        target.dispatchEvent(new KeyEvent(target, KeyEvent.KEY_RELEASED, System.currentTimeMillis(), 0, keyCode,
                KeyEvent.CHAR_UNDEFINED));
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

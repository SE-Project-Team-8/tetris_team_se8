package com.team.tetris.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

import java.awt.Component;
import java.awt.GraphicsEnvironment;
import java.awt.event.KeyEvent;
import java.lang.reflect.InvocationTargetException;
import java.nio.file.Path;

import javax.swing.JPanel;
import javax.swing.SwingUtilities;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.team.tetris.block.Board;
import com.team.tetris.block.TetrominoType;
import com.team.tetris.common.Screen;
import com.team.tetris.common.events.BoardSnapshot;
import com.team.tetris.common.events.GameEventListener;
import com.team.tetris.game.BlockBoardDriver;
import com.team.tetris.game.GameEngine;
import com.team.tetris.game.GameState;
import com.team.tetris.game.ScoreCalculator;
import com.team.tetris.scoreboard.ScoreboardRepository;
import com.team.tetris.settings.GameSettings;
import com.team.tetris.settings.KeyBindings;
import com.team.tetris.settings.SettingsRepository;
import com.team.tetris.ui.theme.PressFeedback;

/** 게임 중 일시정지 화면에서 설정을 열고, 설정을 나가면 멈춘 게임의 일시정지 화면으로 돌아온다. */
class PauseSettingsTest {
    @TempDir
    Path files;

    @BeforeAll
    static void runMenuPressesImmediately() {
        PressFeedback.setPressDurationForTests(0);
    }

    @AfterAll
    static void restoreMenuPressDuration() {
        PressFeedback.setPressDurationForTests(PressFeedback.PRESS_MS);
    }

    @BeforeEach
    void requireDisplay() {
        assumeFalse(GraphicsEnvironment.isHeadless(), "ScreenRouter is a window");
    }

    @Test
    void settingsOpenedFromPauseReturnToThePausedGame() throws Exception {
        GameEngine engine = engine();
        edt(() -> {
            ScreenRouter router = new ScreenRouter();
            try {
                GameScreen game = new GameScreen(router, engine, KeyBindings.defaults(), score -> { });
                PauseScreen pause = new PauseScreen(router, engine, KeyBindings::defaults);
                SettingsScreen settings = settingsScreen(router);
                router.addScreen(ScreenRouter.MAIN_MENU, stub());
                router.addScreen(ScreenRouter.GAME, game);
                router.addScreen(ScreenRouter.PAUSE, pause);
                router.addScreen(ScreenRouter.SETTINGS, settings);
                router.setVisible(true);
                router.showScreen(ScreenRouter.GAME);

                tap(game, KeyEvent.VK_P);
                // 계속하기 → 설정
                tap(pause, KeyEvent.VK_DOWN);
                tap(pause, KeyEvent.VK_ENTER);
                assertEquals(ScreenRouter.SETTINGS, router.getCurrentScreenName());
                assertEquals(GameState.PAUSED, engine.getState(), "The game stays paused while settings are open");

                tap(settings, KeyEvent.VK_ESCAPE);
                assertEquals(ScreenRouter.PAUSE, router.getCurrentScreenName(), "Esc returns to the pause menu");
                assertEquals(GameState.PAUSED, engine.getState());
                assertTrue(game.isShowing(), "The paused game is visible again under the pause menu");

                tap(pause, KeyEvent.VK_UP);
                tap(pause, KeyEvent.VK_ENTER);
                assertEquals(ScreenRouter.GAME, router.getCurrentScreenName());
                assertEquals(GameState.RUNNING, engine.getState(), "Resume continues the same game");
            } finally {
                engine.close();
                router.dispose();
            }
        });
    }

    @Test
    void settingsOpenedFromTheStartMenuStillReturnToIt() throws Exception {
        edt(() -> {
            ScreenRouter router = new ScreenRouter();
            try {
                SettingsScreen settings = settingsScreen(router);
                router.addScreen(ScreenRouter.MAIN_MENU, stub());
                router.addScreen(ScreenRouter.SETTINGS, settings);
                router.setVisible(true);
                router.showScreen(ScreenRouter.MAIN_MENU);
                router.showScreen(ScreenRouter.SETTINGS);

                tap(settings, KeyEvent.VK_ESCAPE);
                assertEquals(ScreenRouter.MAIN_MENU, router.getCurrentScreenName());
            } finally {
                router.dispose();
            }
        });
    }

    private SettingsScreen settingsScreen(ScreenRouter router) {
        return new SettingsScreen(router, new SettingsRepository(files.resolve("settings.properties")),
                new ScoreboardRepository(files.resolve("scores.properties")), GameSettings::defaults,
                updated -> { }, () -> { });
    }

    private static GameEngine engine() {
        return new GameEngine(new BlockBoardDriver(new Board(), () -> TetrominoType.O), new GameEventListener() {
            public void onBoardUpdated(BoardSnapshot snapshot) { }
            public void onScoreChanged(int score) { }
            public void onNextBlocksChanged(int[] types) { }
            public void onPauseStateChanged(boolean paused) { }
            public void onGameOver(int score) { }
        }, new ScoreCalculator(), new GameEngine.DropTimer() {
            public void start(int intervalMillis, Runnable tick) { }
            public void stop() { }
        });
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

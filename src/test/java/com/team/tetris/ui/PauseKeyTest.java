package com.team.tetris.ui;

import com.team.tetris.block.Board;
import com.team.tetris.block.TetrominoType;
import com.team.tetris.common.Screen;
import com.team.tetris.common.events.BoardSnapshot;
import com.team.tetris.common.events.GameEventListener;
import com.team.tetris.game.BlockBoardDriver;
import com.team.tetris.game.GameAction;
import com.team.tetris.game.GameEngine;
import com.team.tetris.game.GameState;
import com.team.tetris.game.ScoreCalculator;
import com.team.tetris.settings.KeyBindings;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import java.awt.Component;
import java.awt.GraphicsEnvironment;
import java.awt.event.KeyEvent;
import java.lang.reflect.InvocationTargetException;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

/** 일시정지 화면은 설정한 일시정지 키로만 재개하고, 메뉴는 다른 메뉴처럼 Enter와 Space로 고른다. */
class PauseKeyTest {
    private final KeyBindings oPauses = KeyBindings.defaults().withKey(GameAction.PAUSE, KeyEvent.VK_O);

    @BeforeEach
    void requireDisplay() {
        assumeFalse(GraphicsEnvironment.isHeadless(), "ScreenRouter is a window");
    }

    @Test
    void onlyTheConfiguredPauseKeyResumes() throws Exception {
        GameEngine engine = engine();
        edt(() -> {
            ScreenRouter router = new ScreenRouter();
            try {
                GameScreen game = new GameScreen(router, engine, oPauses, score -> { });
                PauseScreen pause = new PauseScreen(router, engine, () -> oPauses);
                router.addScreen(ScreenRouter.GAME, game);
                router.addScreen(ScreenRouter.PAUSE, pause);
                router.setVisible(true);
                router.showScreen(ScreenRouter.GAME);

                tap(game, KeyEvent.VK_O);
                assertEquals(ScreenRouter.PAUSE, router.getCurrentScreenName());
                tap(pause, KeyEvent.VK_P);
                assertEquals(ScreenRouter.PAUSE, router.getCurrentScreenName(), "P is not the pause key any more");
                assertEquals(GameState.PAUSED, engine.getState());
                tap(pause, KeyEvent.VK_O);
                assertEquals(ScreenRouter.GAME, router.getCurrentScreenName());
                assertEquals(GameState.RUNNING, engine.getState());
            } finally {
                engine.close();
                router.dispose();
            }
        });
    }

    @Test
    void spaceSelectsAPauseMenuItemLikeEnter() throws Exception {
        GameEngine engine = engine();
        edt(() -> {
            ScreenRouter router = new ScreenRouter();
            try {
                GameScreen game = new GameScreen(router, engine, KeyBindings.defaults(), score -> { });
                PauseScreen pause = new PauseScreen(router, engine, KeyBindings::defaults);
                router.addScreen(ScreenRouter.MAIN_MENU, stub());
                router.addScreen(ScreenRouter.GAME, game);
                router.addScreen(ScreenRouter.PAUSE, pause);
                router.setVisible(true);
                router.showScreen(ScreenRouter.GAME);

                tap(game, KeyEvent.VK_P);
                tap(pause, KeyEvent.VK_SPACE);
                assertEquals(ScreenRouter.GAME, router.getCurrentScreenName(), "Space on Resume resumes");
                tap(game, KeyEvent.VK_P);
                tap(pause, KeyEvent.VK_DOWN);
                tap(pause, KeyEvent.VK_SPACE);
                assertEquals(ScreenRouter.MAIN_MENU, router.getCurrentScreenName(), "Space on Main Menu leaves");
            } finally {
                engine.close();
                router.dispose();
            }
        });
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

    /** 실제 키 입력처럼 누른 뒤 뗀다. */
    private static void tap(Component target, int keyCode) {
        for (int id : new int[]{KeyEvent.KEY_PRESSED, KeyEvent.KEY_RELEASED}) {
            target.dispatchEvent(new KeyEvent(target, id, System.currentTimeMillis(), 0, keyCode,
                    KeyEvent.CHAR_UNDEFINED));
        }
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

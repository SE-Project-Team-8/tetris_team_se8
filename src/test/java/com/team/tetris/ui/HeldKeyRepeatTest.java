package com.team.tetris.ui;

import com.team.tetris.block.Board;
import com.team.tetris.block.TetrominoType;
import com.team.tetris.common.Screen;
import com.team.tetris.common.events.BoardSnapshot;
import com.team.tetris.common.events.GameEventListener;
import com.team.tetris.game.BlockBoardDriver;
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

/**
 * 키를 누르고 있으면 Windows·macOS는 KEY_RELEASED 없이 KEY_PRESSED를 계속 보낸다(자동 반복).
 * 한 번 누르면 한 번만 일어나야 하는 동작이 자동 반복으로 여러 번 일어나거나, 앞 화면에서 누른 키가
 * 새 화면으로 넘어가지 않는지 확인한다. 이벤트는 실제 창의 키 처리 경로로 보낸다.
 */
class HeldKeyRepeatTest {
    @BeforeEach
    void requireDisplay() {
        assumeFalse(GraphicsEnvironment.isHeadless(), "Key bindings need a visible window");
    }

    @Test
    void holdingHardDropDropsOneBlockUntilTheKeyIsReleasedWhileMovementStillRepeats() throws Exception {
        LastBoard events = new LastBoard();
        GameEngine engine = engine(events);
        edt(() -> {
            ScreenRouter router = new ScreenRouter();
            try {
                GameScreen game = new GameScreen(router, engine, KeyBindings.defaults(), score -> { });
                router.addScreen(ScreenRouter.GAME, game);
                router.setVisible(true);
                router.showScreen(ScreenRouter.GAME);
                assertEquals(1, engine.getGeneratedBlocks());

                press(game, KeyEvent.VK_SPACE);
                press(game, KeyEvent.VK_SPACE);
                press(game, KeyEvent.VK_SPACE);
                assertEquals(2, engine.getGeneratedBlocks(), "Only the first press of a held key hard drops");
                release(game, KeyEvent.VK_SPACE);
                press(game, KeyEvent.VK_SPACE);
                assertEquals(3, engine.getGeneratedBlocks(), "A new press after releasing hard drops again");

                // 좌우·아래 이동은 누르고 있으면 계속 움직여야 한다(1차 요구사항 2.4).
                for (int i = 0; i < 3; i++) press(game, KeyEvent.VK_LEFT);
                assertEquals(TetrominoType.O.id(), events.board.cells()[0][1]);
                assertEquals(BoardSnapshot.EMPTY, events.board.cells()[0][4]);
            } finally {
                engine.close();
                router.dispose();
            }
        });
    }

    @Test
    void heldPauseKeyDoesNotResumeRightAfterPausing() throws Exception {
        GameEngine engine = engine(new LastBoard());
        edt(() -> {
            ScreenRouter router = new ScreenRouter();
            try {
                GameScreen game = new GameScreen(router, engine, KeyBindings.defaults(), score -> { });
                router.addScreen(ScreenRouter.GAME, game);
                router.addScreen(ScreenRouter.PAUSE, new PauseScreen(router, engine, KeyBindings::defaults));
                router.setVisible(true);
                router.showScreen(ScreenRouter.GAME);

                press(game, KeyEvent.VK_P);
                assertEquals(ScreenRouter.PAUSE, router.getCurrentScreenName());
                // 수정 전에는 반복마다 재개와 일시정지를 오갔으므로 매번 확인한다.
                for (int repeat = 0; repeat < 2; repeat++) {
                    press(router.getContentPane(), KeyEvent.VK_P);
                    assertEquals(ScreenRouter.PAUSE, router.getCurrentScreenName(), "The held P stays paused");
                    assertEquals(GameState.PAUSED, engine.getState());
                }
                release(router.getContentPane(), KeyEvent.VK_P);
                press(router.getContentPane(), KeyEvent.VK_P);
                assertEquals(ScreenRouter.GAME, router.getCurrentScreenName(), "A new press resumes");
                assertEquals(GameState.RUNNING, engine.getState());
            } finally {
                engine.close();
                router.dispose();
            }
        });
    }

    @Test
    void keyHeldWhenTheGameEndsDoesNotSkipTheGameOverScreen() throws Exception {
        edt(() -> {
            ScreenRouter router = new ScreenRouter();
            try {
                GameOverScreen over = new GameOverScreen(router);
                over.setGameResult(120, 1, 0, false);
                router.addScreen(ScreenRouter.GAME_OVER, over);
                router.addScreen(ScreenRouter.SCOREBOARD, stub());
                router.setVisible(true);
                // 마지막 블록을 하드 드롭한 Space가 아직 눌려 있는 상태다.
                press(new JPanel(), KeyEvent.VK_SPACE);
                router.showScreen(ScreenRouter.GAME_OVER);

                press(over, KeyEvent.VK_SPACE);
                press(over, KeyEvent.VK_SPACE);
                assertEquals(ScreenRouter.GAME_OVER, router.getCurrentScreenName(), "The held Space shows the result");
                release(over, KeyEvent.VK_SPACE);
                press(over, KeyEvent.VK_SPACE);
                assertEquals(ScreenRouter.SCOREBOARD, router.getCurrentScreenName());
            } finally {
                router.dispose();
            }
        });
    }

    @Test
    void enterHeldFromThePauseMenuDoesNotStartANewGameFromTheStartMenu() throws Exception {
        int[] started = new int[1];
        edt(() -> {
            ScreenRouter router = new ScreenRouter();
            try {
                MainMenuScreen menu = new MainMenuScreen(router, () -> started[0]++, () -> { });
                router.addScreen(ScreenRouter.MAIN_MENU, menu);
                router.addScreen(ScreenRouter.GAME, stub());
                router.setVisible(true);
                // 일시정지 메뉴에서 Main Menu를 고른 Enter가 아직 눌려 있는 상태다.
                press(new JPanel(), KeyEvent.VK_ENTER);
                router.showScreen(ScreenRouter.MAIN_MENU);

                press(menu, KeyEvent.VK_ENTER);
                assertEquals(ScreenRouter.MAIN_MENU, router.getCurrentScreenName());
                assertEquals(0, started[0], "The held Enter does not pick New Game");
                release(menu, KeyEvent.VK_ENTER);
                press(menu, KeyEvent.VK_ENTER);
                assertTrue(started[0] == 1 || ScreenRouter.GAME.equals(router.getCurrentScreenName()),
                        "A new press picks New Game");
            } finally {
                router.dispose();
            }
        });
    }

    private static GameEngine engine(GameEventListener events) {
        return new GameEngine(new BlockBoardDriver(new Board(), () -> TetrominoType.O), events,
                new ScoreCalculator(), new GameEngine.DropTimer() {
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

    private static void press(Component target, int keyCode) {
        target.dispatchEvent(new KeyEvent(target, KeyEvent.KEY_PRESSED, System.currentTimeMillis(), 0, keyCode,
                KeyEvent.CHAR_UNDEFINED));
    }

    private static void release(Component target, int keyCode) {
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

    private static final class LastBoard implements GameEventListener {
        BoardSnapshot board;
        public void onBoardUpdated(BoardSnapshot snapshot) { board = snapshot; }
        public void onScoreChanged(int score) { }
        public void onNextBlocksChanged(int[] types) { }
        public void onPauseStateChanged(boolean paused) { }
        public void onGameOver(int score) { }
    }
}

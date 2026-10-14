package com.team.tetris.ui;

import com.team.tetris.block.Board;
import com.team.tetris.block.TetrominoType;
import com.team.tetris.common.events.BoardSnapshot;
import com.team.tetris.common.events.GameEventListener;
import com.team.tetris.game.BlockBoardDriver;
import com.team.tetris.game.GameEngine;
import com.team.tetris.game.GameState;
import com.team.tetris.game.ScoreCalculator;
import com.team.tetris.settings.KeyBindings;
import org.junit.jupiter.api.Test;

import javax.swing.SwingUtilities;
import java.awt.GraphicsEnvironment;
import java.awt.event.KeyEvent;
import java.lang.reflect.InvocationTargetException;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

class MainMenuScreenTest {

    /** Issue #29: 메뉴에서 Space로 새 게임을 고르면 같은 키 입력이 첫 블록을 Hard Drop 하던 문제. */
    @Test
    void spaceThatSelectsNewGameDoesNotAlsoHardDropTheFirstBlock() throws Exception {
        // WHEN_IN_FOCUSED_WINDOW 키 바인딩은 실제로 표시된 창 안에서만 동작한다.
        assumeFalse(GraphicsEnvironment.isHeadless(), "Key bindings need a visible window");
        LastBoard events = new LastBoard();
        GameEngine engine = new GameEngine(new BlockBoardDriver(new Board(), () -> TetrominoType.O),
                events, new ScoreCalculator(), new GameEngine.DropTimer() {
                    public void start(int intervalMillis, Runnable tick) { }
                    public void stop() { }
                });
        edt(() -> {
            ScreenRouter router = new ScreenRouter();
            try {
                MainMenuScreen menu = new MainMenuScreen(router);
                router.addScreen(ScreenRouter.MAIN_MENU, menu);
                router.addScreen(ScreenRouter.GAME,
                        new GameScreen(router, engine, KeyBindings.defaults(), score -> { }));
                router.setVisible(true);
                router.showScreen(ScreenRouter.MAIN_MENU);

                // 기본 키 설정에서 Space는 메뉴 선택 키이면서 게임 화면의 Hard Drop 키다.
                menu.dispatchEvent(new KeyEvent(menu, KeyEvent.KEY_PRESSED, System.currentTimeMillis(),
                        0, KeyEvent.VK_SPACE, ' '));

                assertEquals(ScreenRouter.GAME, router.getCurrentScreenName());
                assertEquals(GameState.RUNNING, engine.getState());
                assertEquals(1, engine.getGeneratedBlocks());
                assertEquals(0, engine.getScore());
                assertEquals(TetrominoType.O.id(), events.board.cells()[0][4]);
                assertEquals(BoardSnapshot.EMPTY, events.board.cells()[19][4]);
            } finally {
                engine.close();
                router.dispose();
            }
        });
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
        public void onGameOver(int finalScore) { }
    }
}

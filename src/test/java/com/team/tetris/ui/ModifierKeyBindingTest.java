package com.team.tetris.ui;

import com.team.tetris.block.Board;
import com.team.tetris.block.TetrominoType;
import com.team.tetris.common.events.BoardSnapshot;
import com.team.tetris.common.events.GameEventListener;
import com.team.tetris.game.BlockBoardDriver;
import com.team.tetris.game.GameAction;
import com.team.tetris.game.GameEngine;
import com.team.tetris.game.ScoreCalculator;
import com.team.tetris.settings.KeyBindings;
import org.junit.jupiter.api.Test;

import javax.swing.SwingUtilities;
import java.awt.GraphicsEnvironment;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.lang.reflect.InvocationTargetException;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

/**
 * 설정에서 Shift·Ctrl 같은 수정 키도 조작 키로 지정할 수 있다. 수정 키를 누르면 이벤트에 그 수정 키가
 * 눌린 상태가 함께 오므로, 수정 키가 없는 상태로만 연결하면 지정한 키가 동작하지 않는다.
 */
class ModifierKeyBindingTest {
    @Test
    void modifierKeysWorkAsControlsAndOtherKeysStillWorkWhileAModifierIsHeld() throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless(), "Key bindings need a visible window");
        LastBoard events = new LastBoard();
        GameEngine engine = new GameEngine(new BlockBoardDriver(new Board(), () -> TetrominoType.O), events,
                new ScoreCalculator(), new GameEngine.DropTimer() {
                    public void start(int intervalMillis, Runnable tick) { }
                    public void stop() { }
                });
        KeyBindings bindings = KeyBindings.defaults()
                .withKey(GameAction.HARD_DROP, KeyEvent.VK_SHIFT)
                .withKey(GameAction.ROTATE_CLOCKWISE, KeyEvent.VK_CONTROL);
        edt(() -> {
            ScreenRouter router = new ScreenRouter();
            try {
                GameScreen game = new GameScreen(router, engine, bindings, score -> { });
                router.addScreen(ScreenRouter.GAME, game);
                router.setVisible(true);
                router.showScreen(ScreenRouter.GAME);
                assertEquals(1, engine.getGeneratedBlocks());

                press(game, KeyEvent.VK_SHIFT, InputEvent.SHIFT_DOWN_MASK);
                assertEquals(2, engine.getGeneratedBlocks(), "Shift bound to Hard Drop drops the block");

                // Shift를 누른 채 왼쪽 키를 눌러도 이동한다.
                press(game, KeyEvent.VK_LEFT, InputEvent.SHIFT_DOWN_MASK);
                assertEquals(TetrominoType.O.id(), events.board.cells()[0][3]);
                press(game, KeyEvent.VK_LEFT, InputEvent.CTRL_DOWN_MASK | InputEvent.ALT_DOWN_MASK);
                assertEquals(TetrominoType.O.id(), events.board.cells()[0][2]);
                assertEquals(BoardSnapshot.EMPTY, events.board.cells()[0][4]);
            } finally {
                engine.close();
                router.dispose();
            }
        });
    }

    private static void press(GameScreen target, int keyCode, int modifiers) {
        target.dispatchEvent(new KeyEvent(target, KeyEvent.KEY_PRESSED, System.currentTimeMillis(), modifiers,
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

    private static final class LastBoard implements GameEventListener {
        BoardSnapshot board;
        public void onBoardUpdated(BoardSnapshot snapshot) { board = snapshot; }
        public void onScoreChanged(int score) { }
        public void onNextBlocksChanged(int[] types) { }
        public void onPauseStateChanged(boolean paused) { }
        public void onGameOver(int score) { }
    }
}

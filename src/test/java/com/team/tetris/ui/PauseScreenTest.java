package com.team.tetris.ui;

import com.team.tetris.block.Board;
import com.team.tetris.block.TetrominoType;
import com.team.tetris.common.events.BoardSnapshot;
import com.team.tetris.common.events.GameEventListener;
import com.team.tetris.game.BlockBoardDriver;
import com.team.tetris.game.GameEngine;
import com.team.tetris.game.ScoreCalculator;
import com.team.tetris.settings.KeyBindings;
import org.junit.jupiter.api.Test;

import javax.swing.SwingUtilities;
import java.awt.GraphicsEnvironment;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.lang.reflect.InvocationTargetException;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

class PauseScreenTest {
    /** 일시정지 화면의 키가 아래 게임 화면 바인딩으로 넘어가면 재개 직후 다시 일시정지하거나 Hard Drop 한다. */
    @Test
    void keysHandledOnThePauseScreenDoNotReachTheGameUnderneath() throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless(), "ScreenRouter is a window");
        GameEngine engine = new GameEngine(new BlockBoardDriver(new Board(), () -> TetrominoType.O),
                new GameEventListener() {
                    public void onBoardUpdated(BoardSnapshot snapshot) { }
                    public void onScoreChanged(int score) { }
                    public void onNextBlocksChanged(int[] types) { }
                    public void onPauseStateChanged(boolean paused) { }
                    public void onGameOver(int score) { }
                }, new ScoreCalculator(), new GameEngine.DropTimer() {
                    public void start(int intervalMillis, Runnable tick) { }
                    public void stop() { }
                });
        try {
            SwingUtilities.invokeAndWait(() -> {
                ScreenRouter router = new ScreenRouter();
                try {
                    PauseScreen pause = new PauseScreen(router, engine, KeyBindings::defaults);
                    for (int keyCode : new int[]{KeyEvent.VK_DOWN, KeyEvent.VK_Q, KeyEvent.VK_UP}) {
                        KeyEvent event = new KeyEvent(pause, KeyEvent.KEY_PRESSED, System.currentTimeMillis(),
                                0, keyCode, KeyEvent.CHAR_UNDEFINED);
                        for (KeyListener listener : pause.getKeyListeners()) listener.keyPressed(event);
                        assertTrue(event.isConsumed(), KeyEvent.getKeyText(keyCode) + " stays on the pause screen");
                    }
                } finally {
                    engine.close();
                    router.dispose();
                }
            });
        } catch (InvocationTargetException failure) {
            if (failure.getCause() instanceof AssertionError assertion) throw assertion;
            throw failure;
        }
    }
}

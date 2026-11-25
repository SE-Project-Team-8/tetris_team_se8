/*
 * 반시계 회전 조작 키의 화면 쪽: 게임에서 Z가 블록을 반시계로 돌리고, 설정 화면에 "왼쪽 회전" 칸이 있다 (이슈 #64).
 */
package com.team.tetris.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

import java.awt.Component;
import java.awt.Container;
import java.awt.GraphicsEnvironment;
import java.awt.event.KeyEvent;
import java.lang.reflect.InvocationTargetException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.team.tetris.block.Board;
import com.team.tetris.block.TetrominoType;
import com.team.tetris.common.Screen;
import com.team.tetris.common.events.BoardSnapshot;
import com.team.tetris.common.events.GameEventListener;
import com.team.tetris.game.BlockBoardDriver;
import com.team.tetris.game.GameAction;
import com.team.tetris.game.GameEngine;
import com.team.tetris.game.ScoreCalculator;
import com.team.tetris.scoreboard.ScoreboardRepository;
import com.team.tetris.settings.GameSettings;
import com.team.tetris.settings.KeyBindings;
import com.team.tetris.settings.SettingsRepository;

class CounterclockwiseKeyScreenTest {
    @TempDir
    Path files;

    @BeforeEach
    void requireDisplay() {
        assumeFalse(GraphicsEnvironment.isHeadless(), "Key bindings need a visible window");
    }

    @Test
    void zRotatesTheFallingPieceTheOtherWayFromUp() throws Exception {
        int[][][] boards = new int[3][][];
        edt(() -> {
            int[][][] last = new int[1][][];
            GameEngine engine = new GameEngine(new BlockBoardDriver(new Board(), () -> TetrominoType.T),
                    new GameEventListener() {
                        @Override public void onBoardUpdated(BoardSnapshot snapshot) { last[0] = snapshot.cells(); }
                        @Override public void onScoreChanged(int score) { }
                        @Override public void onNextBlocksChanged(int[] types) { }
                        @Override public void onPauseStateChanged(boolean paused) { }
                        @Override public void onGameOver(int score) { }
                    }, new ScoreCalculator(), new GameEngine.DropTimer() {
                        @Override public void start(int intervalMillis, Runnable tick) { }
                        @Override public void stop() { }
                    });
            ScreenRouter router = new ScreenRouter();
            try {
                GameScreen game = new GameScreen(router, engine, KeyBindings.defaults(), score -> { });
                router.addScreen(ScreenRouter.GAME, game);
                router.setVisible(true);
                router.showScreen(ScreenRouter.GAME);
                boards[0] = last[0];
                // Z로 반시계로 돌린 뒤 ↑(시계)로 한 번 돌리면 처음 모양으로 돌아온다 → Z는 ↑의 반대 방향이다.
                press(game, KeyEvent.VK_Z);
                boards[1] = last[0];
                press(game, KeyEvent.VK_UP);
                boards[2] = last[0];
            } finally {
                engine.close();
                router.dispose();
            }
        });
        assertNotEquals(text(boards[0]), text(boards[1]), "Z rotates the piece");
        assertEquals(text(boards[0]), text(boards[2]), "Up undoes Z, so Z turns the other way");
    }

    @Test
    void settingsListTheKeysInPairsWithRightAndLeftRotation() throws Exception {
        List<String> labels = new ArrayList<>();
        edt(() -> {
            ScreenRouter router = new ScreenRouter();
            try {
                SettingsScreen settings = new SettingsScreen(router,
                        new SettingsRepository(files.resolve("settings.properties")),
                        new ScoreboardRepository(files.resolve("scores.properties")), GameSettings::defaults,
                        updated -> { }, () -> { });
                router.addScreen(ScreenRouter.MAIN_MENU, stub());
                router.addScreen(ScreenRouter.SETTINGS, settings);
                router.setVisible(true);
                router.showScreen(ScreenRouter.SETTINGS);
                collectLabels(settings, labels);
            } finally {
                router.dispose();
            }
        });
        List<String> expected = List.of("왼쪽 이동", "오른쪽 이동", "소프트 드롭", "하드 드롭",
                "오른쪽 회전", "왼쪽 회전", "일시정지", "나가기");
        List<String> keyLabels = labels.stream().filter(expected::contains).toList();
        assertEquals(expected, keyLabels, "Key cards come in pairs, left column then right column");
        assertEquals(GameAction.values().length, keyLabels.size(), "Every action has a key card");
    }

    private static String text(int[][] board) {
        StringBuilder builder = new StringBuilder();
        for (int[] row : board) builder.append(java.util.Arrays.toString(row));
        return builder.toString();
    }

    private static void collectLabels(Container root, List<String> labels) {
        for (Component child : root.getComponents()) {
            if (child instanceof JLabel label) labels.add(label.getText());
            if (child instanceof Container container) collectLabels(container, labels);
        }
    }

    private static Screen stub() {
        JPanel panel = new JPanel();
        return new Screen() {
            @Override public JPanel getPanel() { return panel; }
            @Override public void onShow() { }
            @Override public void onHide() { }
        };
    }

    private static void press(Component target, int keyCode) {
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

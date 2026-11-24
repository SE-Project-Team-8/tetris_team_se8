/*
 * 확인용 대전 화면 테스트: 창을 넓히고 대전을 시작함, 떠나면 멈추고 원래 크기로, 일시정지·나가기·다시 대전 키,
 * 모든 상태에서 그리기 (ui-contract.md 5절). 두 사람이 실제로 하는 확인은 test-plan.md 3절의 수동 확인이다.
 */
package com.team.tetris.ui;

import com.team.tetris.block.Board;
import com.team.tetris.block.TetrominoType;
import com.team.tetris.common.Difficulty;
import com.team.tetris.common.Screen;
import com.team.tetris.common.battle.Player;
import com.team.tetris.common.battle.VersusMode;
import com.team.tetris.common.events.GameEventListener;
import com.team.tetris.game.BlockBoardDriver;
import com.team.tetris.game.DefaultSpeedPolicy;
import com.team.tetris.game.GameEngine;
import com.team.tetris.game.ScoreCalculator;
import com.team.tetris.game.versus.MatchResult;
import com.team.tetris.game.versus.SidedKey;
import com.team.tetris.game.versus.VersusInput;
import com.team.tetris.game.versus.VersusMatch;
import com.team.tetris.settings.GameSettings;
import com.team.tetris.settings.KeyBindings;
import com.team.tetris.game.GameAction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import java.awt.Graphics2D;
import java.awt.GraphicsEnvironment;
import java.awt.event.KeyEvent;
import java.awt.image.BufferedImage;
import java.io.File;
import java.lang.reflect.InvocationTargetException;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

class VersusCheckScreenTest {
    @BeforeEach
    void requireDisplay() {
        assumeFalse(GraphicsEnvironment.isHeadless(), "ScreenRouter is a window");
    }

    @Test
    void showingWidensTheWindowAndStartsTheMatchAndLeavingUndoesBoth() throws Exception {
        Fixture f = new Fixture(GameSettings.defaults(), VersusMode.NORMAL);
        edt(() -> {
            f.router.showScreen(VersusCheckScreen.NAME);
            assertEquals(VersusCheckScreen.WINDOW_WIDTH, f.router.getCurrentWidth());
            assertEquals(VersusCheckScreen.WINDOW_HEIGHT, f.router.getCurrentHeight());
            assertEquals(VersusMatch.State.RUNNING, f.match.state());

            assertTrue(f.screen.keyPressed(KeyEvent.VK_ESCAPE, KeyEvent.KEY_LOCATION_STANDARD), "quit goes back to the main menu");
            assertEquals(ScreenRouter.MAIN_MENU, f.router.getCurrentScreenName());
            assertEquals(VersusMatch.State.READY, f.match.state(), "leaving stops the match");
            assertEquals(GameSettings.ScreenSize.MEDIUM.width(), f.router.getCurrentWidth());
            assertEquals(GameSettings.ScreenSize.MEDIUM.height(), f.router.getCurrentHeight());
            f.close();
        });
    }

    @Test
    void movesGoToTheInputAndThePauseKeyPausesBothBoards() throws Exception {
        Fixture f = new Fixture(GameSettings.defaults(), VersusMode.NORMAL);
        edt(() -> {
            f.router.showScreen(VersusCheckScreen.NAME);
            assertTrue(f.screen.keyPressed(KeyEvent.VK_A, KeyEvent.KEY_LOCATION_STANDARD), "P1's move key");
            assertFalse(f.screen.keyPressed(KeyEvent.VK_Q, KeyEvent.KEY_LOCATION_STANDARD), "not a key of this screen");

            assertTrue(f.screen.keyPressed(KeyEvent.VK_P, KeyEvent.KEY_LOCATION_STANDARD));
            assertEquals(VersusMatch.State.PAUSED, f.match.state());
            assertFalse(f.screen.keyPressed(KeyEvent.VK_ENTER, KeyEvent.KEY_LOCATION_STANDARD), "Enter neither drops nor restarts while paused");
            assertTrue(f.screen.keyPressed(KeyEvent.VK_P, KeyEvent.KEY_LOCATION_STANDARD));
            assertEquals(VersusMatch.State.RUNNING, f.match.state());
            f.close();
        });
    }

    @Test
    void afterAMatchEnterPlaysAgainAndNumbersChooseTheMode() throws Exception {
        Fixture f = new Fixture(GameSettings.defaults(), VersusMode.TIME_LIMIT);
        edt(() -> {
            f.router.showScreen(VersusCheckScreen.NAME);
            for (int i = 0; i < 180; i++) f.clock.tick.run();
            assertEquals(VersusMatch.State.ENDED, f.match.state());
            assertEquals(MatchResult.Reason.TIME_UP, f.screen.result().reason());

            assertTrue(f.screen.keyPressed(KeyEvent.VK_ENTER, KeyEvent.KEY_LOCATION_STANDARD));
            assertEquals(VersusMatch.State.RUNNING, f.match.state());
            assertEquals(VersusMode.TIME_LIMIT, f.screen.currentMode());
            assertNull(f.screen.result());

            f.match.stop();
            assertTrue(f.screen.keyPressed(KeyEvent.VK_2, KeyEvent.KEY_LOCATION_STANDARD));
            assertEquals(VersusMode.ITEM, f.match.mode());
            f.match.stop();
            assertTrue(f.screen.keyPressed(KeyEvent.VK_1, KeyEvent.KEY_LOCATION_STANDARD));
            assertEquals(VersusMode.NORMAL, f.match.mode());
            f.match.stop();
            assertTrue(f.screen.keyPressed(KeyEvent.VK_3, KeyEvent.KEY_LOCATION_STANDARD));
            assertEquals(VersusMode.TIME_LIMIT, f.match.mode());
            f.close();
        });
    }

    @Test
    void theSharedKeysFollowTheSinglePlayerSettings() throws Exception {
        GameSettings settings = GameSettings.defaults().withKeyBindings(
                KeyBindings.defaults().withKey(GameAction.PAUSE, KeyEvent.VK_F1));
        Fixture f = new Fixture(settings, VersusMode.NORMAL);
        edt(() -> {
            f.router.showScreen(VersusCheckScreen.NAME);
            assertFalse(f.screen.keyPressed(KeyEvent.VK_P, KeyEvent.KEY_LOCATION_STANDARD));
            assertTrue(f.screen.keyPressed(KeyEvent.VK_F1, KeyEvent.KEY_LOCATION_STANDARD));
            assertEquals(VersusMatch.State.PAUSED, f.match.state());
            f.close();
        });
    }

    @Test
    void everyStateCanBePainted() throws Exception {
        Fixture f = new Fixture(GameSettings.defaults().withColorBlindMode(GameSettings.ColorBlindMode.RED_GREEN),
                VersusMode.TIME_LIMIT);
        edt(() -> {
            f.router.showScreen(VersusCheckScreen.NAME);
            f.router.validate();
            // P1 clears two rows twice: P2 takes the first attack into its board, the second waits in its queue.
            fillAroundTheMiddle(f.boards[0], 18);
            fillAroundTheMiddle(f.boards[0], 19);
            assertTrue(f.screen.keyPressed(KeyEvent.VK_SHIFT, KeyEvent.KEY_LOCATION_LEFT));
            assertFalse(f.screen.keyPressed(KeyEvent.VK_SHIFT, KeyEvent.KEY_LOCATION_RIGHT),
                    "the right Shift is nobody's by default");
            assertTrue(f.screen.keyPressed(KeyEvent.VK_ENTER, KeyEvent.KEY_LOCATION_STANDARD));
            f.input.keyReleased(KeyEvent.VK_SHIFT, KeyEvent.KEY_LOCATION_LEFT);
            fillAroundTheMiddle(f.boards[0], 18);
            fillAroundTheMiddle(f.boards[0], 19);
            assertTrue(f.screen.keyPressed(KeyEvent.VK_SHIFT, KeyEvent.KEY_LOCATION_LEFT));
            assertEquals(2, f.match.queue(Player.P2).size());
            for (int i = 0; i < 7; i++) f.clock.tick.run();
            paint(f.screen, "running");

            f.screen.keyPressed(KeyEvent.VK_P, KeyEvent.KEY_LOCATION_STANDARD);
            paint(f.screen, "paused");
            f.screen.keyPressed(KeyEvent.VK_P, KeyEvent.KEY_LOCATION_STANDARD);
            for (int i = 0; i < 180; i++) f.clock.tick.run();
            paint(f.screen, "result");
            f.close();
        });
    }

    @Test
    void keyNamesUseArrowsAndShortWords() {
        assertEquals("←", VersusCheckScreen.keyName(KeyEvent.VK_LEFT));
        assertEquals("Esc", VersusCheckScreen.keyName(KeyEvent.VK_ESCAPE));
        assertEquals("Shift", VersusCheckScreen.keyName(KeyEvent.VK_SHIFT));
        assertEquals("Enter", VersusCheckScreen.keyName(KeyEvent.VK_ENTER));
        assertEquals("A", VersusCheckScreen.keyName(KeyEvent.VK_A));
        assertEquals("왼쪽 Shift", VersusCheckScreen.versusKeyName(SidedKey.left(KeyEvent.VK_SHIFT)));
        assertEquals("오른쪽 Shift", VersusCheckScreen.versusKeyName(SidedKey.right(KeyEvent.VK_SHIFT)));
        assertEquals("↑", VersusCheckScreen.versusKeyName(KeyEvent.VK_UP));
    }

    /** 그려서 픽셀이 바뀌었는지 본다. 환경 변수 TETRIS_VERSUS_CHECK_OUT이 있으면 그 폴더에 PNG로 남긴다. */
    private static void paint(VersusCheckScreen screen, String name) {
        BufferedImage image = new BufferedImage(screen.getWidth(), screen.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        screen.paint(g);
        g.dispose();
        assertNotEquals(image.getRGB(0, 0), image.getRGB(screen.getWidth() / 4, screen.getHeight() / 2),
                "something was drawn: " + name);
        String out = System.getenv("TETRIS_VERSUS_CHECK_OUT");
        if (out != null) {
            try {
                ImageIO.write(image, "png", new File(out, "versus-" + name + ".png"));
            } catch (java.io.IOException failure) {
                throw new AssertionError(failure);
            }
        }
    }

    /** row 행의 0~3열과 6~9열을 채운다. */
    private static void fillAroundTheMiddle(Board board, int row) {
        assertTrue(board.tryPlace(new com.team.tetris.block.Tetromino(TetrominoType.I,
                com.team.tetris.block.Rotation.SPAWN, row - 1, 0)));
        assertTrue(board.tryPlace(new com.team.tetris.block.Tetromino(TetrominoType.I,
                com.team.tetris.block.Rotation.SPAWN, row - 1, 6)));
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

    /** O만 나오고 자동 낙하가 없는 엔진 두 개, 직접 넘기는 제한 시간, 기본 크기 창으로 만든 확인용 화면. */
    private static final class Fixture {
        final Board[] boards = {new Board(), new Board()};
        final ManualTimer clock = new ManualTimer();
        ScreenRouter router;
        VersusMatch match;
        VersusInput input;
        VersusCheckScreen screen;

        Fixture(GameSettings settings, VersusMode mode) throws Exception {
            edt(() -> {
                router = new ScreenRouter(GameSettings.ScreenSize.MEDIUM);
                match = new VersusMatch(new VersusMatch.PlayerSetup() {
                    @Override public BlockBoardDriver driver(Player player) {
                        return new BlockBoardDriver(boards[player.ordinal()], () -> TetrominoType.O);
                    }

                    @Override public GameEngine engine(Player player, BlockBoardDriver driver,
                                                       GameEventListener listener, GameEngine.AttackLink link) {
                        return new GameEngine(driver, listener, new ScoreCalculator(), new DefaultSpeedPolicy(),
                                new ManualTimer(), new ManualTimer(), () -> Difficulty.NORMAL, link);
                    }
                }, clock);
                input = new VersusInput(match, settings.versusKeyMap(), ManualTimer::new);
                screen = new VersusCheckScreen(router, match, input, () -> settings, mode);
                router.addScreen(ScreenRouter.MAIN_MENU, new Blank());
                router.addScreen(VersusCheckScreen.NAME, screen);
                router.setVisible(true);
            });
        }

        /** 화면을 떠나 전역 키 디스패처를 떼고 창을 닫는다. 떼지 않으면 뒤 테스트의 키를 이 화면이 가로챈다. */
        void close() {
            router.showScreen(ScreenRouter.MAIN_MENU);
            match.close();
            router.dispose();
        }
    }

    private static final class Blank implements Screen {
        private final JPanel panel = new JPanel();
        @Override public JPanel getPanel() { return panel; }
        @Override public void onShow() { }
        @Override public void onHide() { }
    }

    private static final class ManualTimer implements GameEngine.DropTimer {
        Runnable tick = () -> { };
        @Override public void start(int intervalMillis, Runnable tick) { this.tick = tick; }
        @Override public void stop() { tick = () -> { }; }
    }
}

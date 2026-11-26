/*
 * 정식 대전 화면: 창 크기, 키 분배, 일시정지 메뉴, 공격 연출, 결과 넘기기 (docs/battle/ui-handoff.md 7절).
 */
package com.team.tetris.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

import java.awt.GraphicsEnvironment;
import java.awt.event.KeyEvent;
import java.awt.image.BufferedImage;
import java.lang.reflect.InvocationTargetException;
import java.util.concurrent.atomic.AtomicReference;

import javax.swing.JPanel;
import javax.swing.SwingUtilities;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.team.tetris.block.Board;
import com.team.tetris.block.Rotation;
import com.team.tetris.block.Tetromino;
import com.team.tetris.block.TetrominoType;
import com.team.tetris.common.Difficulty;
import com.team.tetris.common.Screen;
import com.team.tetris.common.battle.Player;
import com.team.tetris.common.battle.VersusMode;
import com.team.tetris.common.events.GameEventListener;
import com.team.tetris.game.BlockBoardDriver;
import com.team.tetris.game.DefaultSpeedPolicy;
import com.team.tetris.game.GameAction;
import com.team.tetris.game.GameEngine;
import com.team.tetris.game.ScoreCalculator;
import com.team.tetris.game.item.ItemRegistry;
import com.team.tetris.game.versus.MatchResult;
import com.team.tetris.game.versus.VersusInput;
import com.team.tetris.game.versus.VersusMatch;
import com.team.tetris.settings.GameSettings;
import com.team.tetris.ui.theme.PressFeedback;

class VersusScreenTest {
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
    void enteringStartsTheChosenModeInAWideWindowAndLeavingRestoresIt() throws Exception {
        Fixture f = new Fixture();
        try {
            edt(() -> {
                int width = f.router.getCurrentWidth();
                int height = f.router.getCurrentHeight();
                f.show(VersusMode.TIME_LIMIT);
                assertEquals(VersusMatch.State.RUNNING, f.match.state());
                assertEquals(VersusMode.TIME_LIMIT, f.match.mode());
                assertEquals(1152, f.router.getCurrentWidth(), "MEDIUM settings widen to the medium versus window");
                assertEquals(720, f.router.getCurrentHeight());
                f.router.showScreen(ScreenRouter.MAIN_MENU);
                assertEquals(width, f.router.getCurrentWidth(), "Leaving restores the single-player window");
                assertEquals(height, f.router.getCurrentHeight());
                assertEquals(VersusMatch.State.PAUSED, f.match.state(), "Leaving mid-match pauses it");
            });
        } finally {
            f.close();
        }
    }

    @Test
    void movesGoToTheInputAndThePauseKeyOpensThePauseMenu() throws Exception {
        Fixture f = new Fixture();
        try {
            edt(() -> {
                f.show(VersusMode.NORMAL);
                assertTrue(f.screen.keyPressed(KeyEvent.VK_A, KeyEvent.KEY_LOCATION_STANDARD), "P1 move key is used");
                assertTrue(f.screen.keyPressed(KeyEvent.VK_LEFT, KeyEvent.KEY_LOCATION_STANDARD), "P2 move key is used");
                f.input.releaseAll();

                f.screen.keyPressed(KeyEvent.VK_P, KeyEvent.KEY_LOCATION_STANDARD);
                assertEquals(VersusMatch.State.PAUSED, f.match.state(), "P pauses both boards");
                // 메뉴가 떠 있는 동안 화살표는 메뉴를 움직이고 대전에는 가지 않는다. 계속하기로 재개한다.
                f.screen.keyPressed(KeyEvent.VK_ENTER, KeyEvent.KEY_LOCATION_STANDARD);
                assertEquals(VersusMatch.State.RUNNING, f.match.state(), "Continue resumes the match");

                f.screen.keyPressed(KeyEvent.VK_P, KeyEvent.KEY_LOCATION_STANDARD);
                f.screen.keyPressed(KeyEvent.VK_DOWN, KeyEvent.KEY_LOCATION_STANDARD);
                f.screen.keyPressed(KeyEvent.VK_ENTER, KeyEvent.KEY_LOCATION_STANDARD);
                assertEquals(ScreenRouter.BATTLE_SELECT, f.router.getCurrentScreenName(), "The second item opens mode selection");
                assertEquals(VersusMatch.State.READY, f.match.state(), "Leaving through the menu stops the match");
            });
        } finally {
            f.close();
        }
    }

    @Test
    void aTwoLineClearSendsAnAttackThatFlashesIntoTheOpponentQueueAndIsInsertedLater() throws Exception {
        Fixture f = new Fixture();
        BufferedImage[] frames = new BufferedImage[3];
        try {
            edt(() -> {
                f.show(VersusMode.NORMAL);
                frames[0] = f.paint();
                // P1의 아래 두 줄을 O 자리(4·5열)만 비우고 채운 뒤 하드드롭한다.
                f.fillBottomRowsExceptTheMiddle(Player.P1);
                f.match.handle(Player.P1, GameAction.HARD_DROP);
                assertEquals(2, f.match.queue(Player.P2).size(), "Two cleared lines attack P2");
                frames[1] = f.paint();
            });
            assertNotEquals(f.queueCell(frames[0], Player.P2), -1);
            // 회색 뭉치가 날아가는 동안에는 새 줄이 아직 비어 있다.
            assertEquals(f.queueCell(frames[0], Player.P2), f.queueCell(frames[1], Player.P2),
                    "The new lines wait for the comet to arrive");
            // 혜성이 도착하고 깜빡임이 끝나면 대기열에 회색 줄이 보인다. 느린 기기를 생각해 최대 5초 기다린다.
            long deadline = System.currentTimeMillis() + 5_000;
            do {
                Thread.sleep(50);
                edt(() -> frames[2] = f.paint());
            } while (f.queueCell(frames[0], Player.P2) == f.queueCell(frames[2], Player.P2)
                    && System.currentTimeMillis() < deadline);
            assertNotEquals(f.queueCell(frames[0], Player.P2), f.queueCell(frames[2], Player.P2),
                    "After the flash the queue shows the grey lines");

            edt(() -> {
                f.match.handle(Player.P2, GameAction.HARD_DROP);
                assertTrue(f.match.queue(Player.P2).isEmpty(), "Locking a piece inserts the queue into the board");
                f.paint();   // 삽입 연출 중에도 그릴 수 있다
            });
        } finally {
            f.close();
        }
    }

    @Test
    void theResultIsHandedOnAfterAShortFreeze() throws Exception {
        Fixture f = new Fixture();
        try {
            edt(() -> {
                f.show(VersusMode.TIME_LIMIT);
                for (int second = 0; second < 180; second++) f.clock.tick.run();
                assertEquals(VersusMatch.State.ENDED, f.match.state());
                assertNull(f.result.get(), "Both boards stay on screen for a moment");
                f.paint();   // 승리 배너
                assertTrue(f.screen.keyPressed(KeyEvent.VK_A, KeyEvent.KEY_LOCATION_STANDARD),
                        "Keys are swallowed while the result is shown");
            });
            long deadline = System.currentTimeMillis() + 5_000;
            while (f.result.get() == null && System.currentTimeMillis() < deadline) Thread.sleep(50);
            MatchResult result = f.result.get();
            assertNotNull(result, "The result screen receives the result");
            assertEquals(MatchResult.Reason.TIME_UP, result.reason());
        } finally {
            f.close();
        }
    }

    @Test
    void everyStateCanBePaintedAtEveryWindowSize() throws Exception {
        for (GameSettings.ScreenSize size : GameSettings.ScreenSize.values()) {
            Fixture f = new Fixture(size);
            try {
                edt(() -> {
                    f.show(VersusMode.ITEM);
                    f.paint();
                    f.screen.keyPressed(KeyEvent.VK_P, KeyEvent.KEY_LOCATION_STANDARD);
                    f.paint();
                });
            } finally {
                f.close();
            }
        }
    }

    @Test
    void theTimeLimitStartsWithTheChosenMinutesAndRematchKeepsThem() throws Exception {
        Fixture f = new Fixture();
        try {
            edt(() -> {
                f.screen.prepare(VersusMode.TIME_LIMIT, 60);
                f.router.showScreen(ScreenRouter.VERSUS);
                assertEquals(60, f.match.remainingSeconds(), "One minute was chosen");
                f.router.showScreen(ScreenRouter.MAIN_MENU);
                f.show(VersusMode.TIME_LIMIT);   // 다시 대전: 같은 시간
                assertEquals(60, f.match.remainingSeconds());
                for (int second = 0; second < 60; second++) f.clock.tick.run();
                assertEquals(VersusMatch.State.ENDED, f.match.state(), "The match ends after the chosen minute");
            });
        } finally {
            f.close();
        }
    }

    @Test
    void theKeyHintFollowsTheVersusKeySettings() {
        GameSettings settings = GameSettings.defaults();
        assertEquals("W A S D  ·  왼쪽 회전 Q  ·  하드드롭 Left Shift", VersusScreen.playerKeys(settings, Player.P1));
        assertEquals("↑ ← ↓ →  ·  왼쪽 회전 /  ·  하드드롭 Enter", VersusScreen.playerKeys(settings, Player.P2));
        assertEquals(1024, VersusScreen.windowSize(GameSettings.ScreenSize.SMALL)[0]);
        assertEquals(800, VersusScreen.windowSize(GameSettings.ScreenSize.LARGE)[1]);
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

    private static final class Fixture {
        final Board[] boards = {new Board(), new Board()};
        final ManualTimer clock = new ManualTimer();
        final AtomicReference<MatchResult> result = new AtomicReference<>();
        ScreenRouter router;
        VersusMatch match;
        VersusInput input;
        VersusScreen screen;

        Fixture() throws Exception {
            this(GameSettings.ScreenSize.MEDIUM);
        }

        Fixture(GameSettings.ScreenSize size) throws Exception {
            GameSettings settings = GameSettings.defaults().withScreenSize(size);
            edt(() -> {
                router = new ScreenRouter(size);
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
                screen = new VersusScreen(router, match, input, () -> settings, ItemRegistry.defaults(), result::set);
                router.addScreen(ScreenRouter.MAIN_MENU, new Blank());
                router.addScreen(ScreenRouter.BATTLE_SELECT, new Blank());
                router.addScreen(ScreenRouter.VERSUS, screen);
                router.setVisible(true);
                router.showScreen(ScreenRouter.MAIN_MENU);
            });
        }

        void show(VersusMode mode) {
            screen.prepare(mode);
            router.showScreen(ScreenRouter.VERSUS);
        }

        void fillBottomRowsExceptTheMiddle(Player player) {
            Board board = boards[player.ordinal()];
            for (int row : new int[]{17, 18}) {
                assertTrue(board.tryPlace(new Tetromino(TetrominoType.I, Rotation.SPAWN, row, 0)));
                assertTrue(board.tryPlace(new Tetromino(TetrominoType.I, Rotation.SPAWN, row, 6)));
            }
        }

        BufferedImage paint() {
            screen.setSize(Math.max(1, screen.getWidth()), Math.max(1, screen.getHeight()));
            BufferedImage image = new BufferedImage(screen.getWidth(), screen.getHeight(), BufferedImage.TYPE_INT_RGB);
            var graphics = image.createGraphics();
            screen.paint(graphics);
            graphics.dispose();
            return image;
        }

        /** 그 플레이어 공격 대기열 격자의 맨 윗줄 첫 칸 가운데 색. */
        int queueCell(BufferedImage image, Player player) {
            java.awt.Rectangle grid = screen.queueGridBounds(player);
            int cell = grid.width / 10;
            return image.getRGB(grid.x + cell / 2, grid.y + cell / 2) & 0xFFFFFF;
        }

        void close() throws Exception {
            edt(() -> {
                router.showScreen(ScreenRouter.MAIN_MENU);
                match.close();
                router.dispose();
            });
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

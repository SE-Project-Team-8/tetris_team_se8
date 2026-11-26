/*
 * P2P 대전 화면: 접속·대기실 화면과 경기 화면을 같은 PC의 루프백(127.0.0.1)으로 실제로 연결해 확인한다.
 */
package com.team.tetris.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeFalse;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.awt.Window;
import java.awt.event.KeyEvent;
import java.awt.image.BufferedImage;
import java.lang.reflect.InvocationTargetException;
import java.nio.file.Path;
import java.util.List;
import java.util.function.BooleanSupplier;

import javax.swing.JDialog;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.team.tetris.common.Difficulty;
import com.team.tetris.common.Screen;
import com.team.tetris.common.battle.Player;
import com.team.tetris.common.battle.VersusMode;
import com.team.tetris.common.events.BoardSnapshot;
import com.team.tetris.game.item.ItemRegistry;
import com.team.tetris.p2p.P2pSession;
import com.team.tetris.settings.GameSettings;
import com.team.tetris.ui.theme.PressFeedback;

class P2pScreensTest {
    @TempDir
    static Path home;
    private static String realHome;

    @BeforeAll
    static void isolate() {
        // 최근 접속 IP 파일이 개발자의 홈 폴더에 생기지 않게 한다.
        realHome = System.getProperty("user.home");
        System.setProperty("user.home", home.toString());
        PressFeedback.setPressDurationForTests(0);
    }

    @AfterAll
    static void restore() {
        System.setProperty("user.home", realHome);
        PressFeedback.setPressDurationForTests(PressFeedback.PRESS_MS);
    }

    @Test
    void theHostAddressIsReadFromTheWaitingStatus() {
        assertEquals("192.168.0.12, 10.0.0.3",
                P2pLobbyScreen.addressesFrom("상대 접속 대기 중 · 포트 47820\n내 IP  192.168.0.12, 10.0.0.3"));
        assertEquals("", P2pLobbyScreen.addressesFrom("서버를 여는 중…"));
    }

    @Test
    void theServerAddressAcceptsOnlyDigitsAndDots() {
        assertTrue(P2pLobbyScreen.acceptsIpChar("", '1'));
        assertTrue(P2pLobbyScreen.acceptsIpChar("192", '.'));
        assertTrue(P2pLobbyScreen.acceptsIpChar("192.168.0.1", '2'));
        assertFalse(P2pLobbyScreen.acceptsIpChar("", 'a'));
        assertFalse(P2pLobbyScreen.acceptsIpChar("192", 'ㄱ'));
        assertFalse(P2pLobbyScreen.acceptsIpChar("192", ':'));
        assertFalse(P2pLobbyScreen.acceptsIpChar("", '.'));
        assertFalse(P2pLobbyScreen.acceptsIpChar("192.", '.'));
        assertFalse(P2pLobbyScreen.acceptsIpChar("192", '1'));
        assertFalse(P2pLobbyScreen.acceptsIpChar("1.2.3.4", '.'));
    }

    @Test
    void landingPositionsBecomeAGhostBoard() {
        BoardSnapshot ghost = P2pGameScreen.landingSnapshot(List.of(18 * 10 + 4, 19 * 10 + 4, 19 * 10 + 5));
        assertTrue(ghost.cells()[18][4] != BoardSnapshot.EMPTY);
        assertTrue(ghost.cells()[19][5] != BoardSnapshot.EMPTY);
        assertEquals(BoardSnapshot.EMPTY, ghost.cells()[0][0]);
    }

    @Test
    void twoPeersConnectAgreeOnTheModeStartTogetherAndADisconnectReturnsToTheFirstScreen() throws Exception {
        assumeFalse(java.awt.GraphicsEnvironment.isHeadless(), "ScreenRouter is a window");
        Peer host = new Peer();
        Peer client = new Peer();
        try {
            edt(() -> host.lobby.handleKey(KeyEvent.VK_ENTER));   // 서버 열기
            waitFor(() -> P2pLobbyScreen.addressesFrom(host.session.status()).length() > 0 || host.session.hasError());
            assumeTrue(!host.session.hasError(), "Port " + P2pSession.PORT + " is free on this machine");
            paint(host.lobby);

            edt(() -> {
                client.lobby.handleKey(KeyEvent.VK_RIGHT);   // 접속하기
                for (char c : "127.0.0.1".toCharArray()) {
                    client.lobby.dispatchEvent(new KeyEvent(client.lobby, KeyEvent.KEY_TYPED, 0, 0,
                            KeyEvent.VK_UNDEFINED, c));
                }
                client.lobby.handleKey(KeyEvent.VK_ENTER);
            });
            waitFor(() -> host.session.state() == P2pSession.State.LOBBY
                    && client.session.state() == P2pSession.State.LOBBY);

            // 게임 방식은 서버만 바꾼다. 클라이언트의 ←→는 아무 일도 하지 않는다.
            edt(() -> client.lobby.handleKey(KeyEvent.VK_RIGHT));
            edt(() -> host.lobby.handleKey(KeyEvent.VK_RIGHT));
            waitFor(() -> client.session.mode() == VersusMode.ITEM);
            assertEquals(VersusMode.ITEM, host.session.mode());
            paint(host.lobby);
            paint(client.lobby);

            // 시간제한에서는 서버만 ↑↓로 제한 시간(1~5분)을 바꾸고, 클라이언트에도 반영된다.
            edt(() -> host.lobby.handleKey(KeyEvent.VK_RIGHT));
            waitFor(() -> client.session.mode() == VersusMode.TIME_LIMIT);
            edt(() -> client.lobby.handleKey(KeyEvent.VK_UP));
            edt(() -> host.lobby.handleKey(KeyEvent.VK_UP));
            waitFor(() -> client.session.timeLimitMinutes() == 4);
            edt(() -> {
                for (int press = 0; press < 6; press++) host.lobby.handleKey(KeyEvent.VK_UP);
            });
            waitFor(() -> client.session.timeLimitMinutes() == P2pSession.MAX_TIME_LIMIT_MINUTES);
            edt(() -> host.lobby.handleKey(KeyEvent.VK_DOWN));
            waitFor(() -> client.session.timeLimitMinutes() == 4);
            paint(host.lobby);
            paint(client.lobby);
            // 다시 아이템 방식으로 (아래 경기는 끝까지 쌓아서 끝낸다)
            edt(() -> host.lobby.handleKey(KeyEvent.VK_LEFT));
            waitFor(() -> client.session.mode() == VersusMode.ITEM);

            edt(() -> host.lobby.handleKey(KeyEvent.VK_ENTER));     // 서버 준비
            edt(() -> client.lobby.handleKey(KeyEvent.VK_ENTER));   // 클라이언트 준비 → 시작
            waitFor(() -> ScreenRouter.P2P_GAME.equals(host.router.getCurrentScreenName())
                    && ScreenRouter.P2P_GAME.equals(client.router.getCurrentScreenName()));

            // 내 키는 내 보드를 움직이고, 그 보드가 상대 화면에도 전달된다.
            edt(() -> client.game.keyPressed(KeyEvent.VK_SPACE));
            waitFor(() -> host.session.board(Player.P2) != null && host.session.board(Player.P2).score() > 0);
            // 하드드롭 이펙트가 내 화면과 상대 화면의 같은 보드(P2)에 그려진다.
            waitFor(() -> client.game.effectsShown(Player.P2) > 0 && host.game.effectsShown(Player.P2) > 0);
            assertEquals(0, host.game.effectsShown(Player.P1), "Nothing happened on the host's own board yet");
            paint(host.game);
            paint(client.game);

            // 한쪽이 끝까지 쌓으면 두 화면 모두 승리 배너를 잠깐 보여 준 뒤 대기실로 돌아간다.
            waitFor(() -> {
                if (client.session.state() == P2pSession.State.PLAYING) client.game.keyPressed(KeyEvent.VK_SPACE);
                return host.game.showingResult() && client.game.showingResult();
            });
            assertEquals(Player.P1, host.session.result().winner());
            assertEquals(ScreenRouter.P2P_GAME, host.router.getCurrentScreenName());
            paint(host.game);
            paint(client.game);
            waitFor(() -> ScreenRouter.P2P_LOBBY.equals(host.router.getCurrentScreenName())
                    && ScreenRouter.P2P_LOBBY.equals(client.router.getCurrentScreenName()));

            // 상대가 연결을 닫으면 끊김을 알리고 P2P 첫 화면으로 돌아온다.
            edt(client.session::close);
            waitFor(() -> host.session.state() == P2pSession.State.IDLE);
            waitFor(() -> ScreenRouter.P2P_LOBBY.equals(host.router.getCurrentScreenName()));
            assertTrue(host.session.hasError(), "The host shows why the connection ended");
            paint(host.lobby);
        } finally {
            edt(() -> {
                for (Window window : Window.getWindows()) {
                    if (window instanceof JDialog dialog) dialog.dispose();
                }
                host.close();
                client.close();
            });
        }
    }

    private static void waitFor(BooleanSupplier condition) throws Exception {
        long deadline = System.currentTimeMillis() + 8_000;
        while (System.currentTimeMillis() < deadline) {
            boolean[] done = new boolean[1];
            edt(() -> done[0] = condition.getAsBoolean());
            if (done[0]) return;
            Thread.sleep(50);
        }
        throw new AssertionError("Timed out waiting for the P2P session");
    }

    private static void paint(JPanel panel) throws Exception {
        edt(() -> {
            BufferedImage image = new BufferedImage(Math.max(1, panel.getWidth()), Math.max(1, panel.getHeight()),
                    BufferedImage.TYPE_INT_RGB);
            var graphics = image.createGraphics();
            panel.paint(graphics);
            graphics.dispose();
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

    /** 한 PC 안의 한 사람: 창, 세션, 접속·대기실 화면, 경기 화면. */
    private static final class Peer {
        ScreenRouter router;
        P2pSession session;
        P2pLobbyScreen lobby;
        P2pGameScreen game;

        Peer() throws Exception {
            edt(() -> {
                router = new ScreenRouter(GameSettings.ScreenSize.MEDIUM);
                session = new P2pSession(ItemRegistry.defaults(), () -> Difficulty.NORMAL, () -> {
                    lobby.sessionChanged();
                    game.sessionChanged();
                });
                lobby = new P2pLobbyScreen(router, session);
                game = new P2pGameScreen(router, session, GameSettings::defaults, ItemRegistry.defaults());
                JPanel blank = new JPanel();
                router.addScreen(ScreenRouter.MAIN_MENU, new Screen() {
                    @Override public JPanel getPanel() { return blank; }
                    @Override public void onShow() { }
                    @Override public void onHide() { }
                });
                router.addScreen(ScreenRouter.P2P_LOBBY, lobby);
                router.addScreen(ScreenRouter.P2P_GAME, game);
                router.setVisible(true);
                router.showScreen(ScreenRouter.P2P_LOBBY);
            });
        }

        void close() {
            session.close();
            router.dispose();
        }
    }
}

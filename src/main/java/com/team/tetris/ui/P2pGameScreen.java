/*
 * P2P 대전 경기 화면 (docs/p2p-handoff.md PLAYING·FINISHING, 3차 요구사항 p.9~10).
 */
package com.team.tetris.ui;

import java.awt.Color;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.KeyEventDispatcher;
import java.awt.KeyboardFocusManager;
import java.awt.event.KeyEvent;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.function.Supplier;

import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.Timer;

import com.team.tetris.block.ColorScheme;
import com.team.tetris.block.TetrominoType;
import com.team.tetris.common.Screen;
import com.team.tetris.common.battle.Player;
import com.team.tetris.common.constants.GameConstants;
import com.team.tetris.common.events.BoardSnapshot;
import com.team.tetris.game.GameAction;
import com.team.tetris.game.item.ItemRegistry;
import com.team.tetris.game.versus.MatchResult;
import com.team.tetris.p2p.P2pSession;
import com.team.tetris.settings.GameSettings;
import com.team.tetris.settings.KeyBindings;
import com.team.tetris.ui.theme.ThemedDialog;
import com.team.tetris.ui.theme.UiColors;
import com.team.tetris.ui.theme.UiFonts;
import com.team.tetris.ui.theme.UiPainter;

/**
 * 내 보드와 상대 보드를 로컬 대전 화면과 같은 배치·연출({@link VersusBoards})로 그린다. P1은 서버, P2는 클라이언트이고
 * 내 쪽 이름에 "(나)"를 붙인다. 보드 값은 세션의 {@link P2pSession.BoardState}에서 읽는다(연출 알림은 오지 않으므로
 * 줄 삭제 팝·하드드롭·폭탄 연출은 없고, 공격 연출은 대기열 변화로 그린다).
 *
 * 키는 1인용 설정을 쓴다(한 PC에 한 사람). 일시정지 키는 두 사람 모두 멈춘다(클라이언트는 서버에 요청).
 * 나가기는 확인 뒤 연결을 끊고 P2P 첫 화면으로. 연결이 끊기면 오류를 알리고 첫 화면으로 돌아간다.
 */
public final class P2pGameScreen extends JPanel implements Screen {
    private static final Set<GameAction> ONE_PRESS = EnumSet.of(GameAction.HARD_DROP, GameAction.PAUSE, GameAction.QUIT);
    private static final Color DIMMED_OVERLAY = new Color(0x14, 0x0F, 0x28, 184);
    /** 경기가 끝난 뒤 승리 배너를 보여 주고 대기실로 돌아가기까지의 시간. */
    static final int END_BANNER_MS = 2_500;

    private final ScreenRouter router;
    private final P2pSession session;
    private final Supplier<GameSettings> settings;
    private final VersusBoards boards;
    private final KeyEventDispatcher dispatcher = this::dispatch;
    /** 마지막으로 받은 보드 스냅샷. 같은 스냅샷이면 다시 넘기지 않는다. */
    private final BoardSnapshot[] shownBoards = new BoardSnapshot[Player.values().length];
    private boolean dialogOpen;
    private int previousWidth;
    private int previousHeight;
    /** 방금 끝난 경기 결과. 승리 배너를 띄우는 동안만 값이 있다. */
    private MatchResult ended;
    private final Timer endTimer = new Timer(END_BANNER_MS, event -> leaveToLobby());

    public P2pGameScreen(ScreenRouter router, P2pSession session, Supplier<GameSettings> settings, ItemRegistry items) {
        this.router = Objects.requireNonNull(router, "router");
        this.session = Objects.requireNonNull(session, "session");
        this.settings = Objects.requireNonNull(settings, "settings");
        this.boards = new VersusBoards(this, items);
        setBackground(UiColors.BACKGROUND);
        setOpaque(true);
        setFocusable(true);
    }

    @Override public JPanel getPanel() { return this; }

    @Override
    public void onShow() {
        previousWidth = router.getCurrentWidth();
        previousHeight = router.getCurrentHeight();
        int[] size = VersusScreen.windowSize(settings.get().screenSize());
        router.updateWindowSize(size[0], size[1]);
        boards.reset();
        Arrays.fill(shownBoards, null);
        ColorScheme colors = settings.get().isColorBlindModeEnabled() ? ColorScheme.colorBlind() : ColorScheme.standard();
        boards.setColorScheme(colors);
        for (Player player : Player.values()) {
            boards.setLabel(player, player.name() + (player == session.localPlayer() ? " (나)" : ""));
        }
        KeyboardFocusManager.getCurrentKeyboardFocusManager().addKeyEventDispatcher(dispatcher);
        refreshBoards();
        requestFocusInWindow();
        repaint();
    }

    @Override
    public void onHide() {
        KeyboardFocusManager.getCurrentKeyboardFocusManager().removeKeyEventDispatcher(dispatcher);
        boards.stopEffects();
        endTimer.stop();
        ended = null;
        router.updateWindowSize(previousWidth, previousHeight);
    }

    /**
     * 세션이 바뀔 때마다 부른다. 경기 중이면 보드를 갱신하고, 경기가 끝나 대기실로 돌아왔거나 연결이 끊기면
     * P2P 첫 화면(대기실)으로 간다. 끊긴 경우에는 오류를 알린다.
     */
    public void sessionChanged() {
        if (!ScreenRouter.P2P_GAME.equals(router.getCurrentScreenName())) return;
        P2pSession.State state = session.state();
        if (state == P2pSession.State.PLAYING || state == P2pSession.State.FINISHING) {
            refreshBoards();
            repaint();
            return;
        }
        if (state == P2pSession.State.LOBBY && session.result() != null) {
            // 경기가 끝나 대기실로 넘어가도 승리 배너를 잠깐 보여 준 뒤 대기실로 간다.
            if (ended == null) {
                ended = session.result();
                endTimer.restart();
                repaint();
            }
            return;
        }
        boolean lost = state == P2pSession.State.IDLE && session.hasError();
        String reason = session.status();
        endTimer.stop();
        ended = null;
        router.showScreen(ScreenRouter.P2P_LOBBY);
        if (lost) {
            // 세션 알림 도중에 모달 대화상자를 띄우지 않도록 한 박자 늦게 띄운다.
            SwingUtilities.invokeLater(() -> ThemedDialog.showMessage(router, "연결 끊김",
                    reason + "\nP2P 첫 화면으로 돌아갑니다.", ThemedDialog.Tone.ERROR));
        }
    }

    private void leaveToLobby() {
        endTimer.stop();
        if (ScreenRouter.P2P_GAME.equals(router.getCurrentScreenName())) router.showScreen(ScreenRouter.P2P_LOBBY);
    }

    /** 승리 배너를 띄우고 있는지. */
    boolean showingResult() {
        return ended != null;
    }

    /** 세션의 두 보드 스냅샷을 보드 영역에 옮긴다. 대기열이 바뀌면 공격 연출이 시작된다. */
    private void refreshBoards() {
        for (Player player : Player.values()) {
            P2pSession.BoardState state = session.board(player);
            if (state == null) continue;
            VersusBoards.Side side = boards.side(player);
            if (state.board() != shownBoards[player.ordinal()]) {
                shownBoards[player.ordinal()] = state.board();
                side.board.onBoardUpdated(state.board());
                side.board.onLandingUpdated(null, landingSnapshot(state.landing()));
            }
            side.score = state.score();
            side.level = state.level();
            side.next = state.next() == BoardSnapshot.EMPTY ? new int[0] : new int[]{state.next()};
            if (!state.queue().equals(side.queue)) boards.queueChanged(player, state.queue());
        }
    }

    /** 착지 예고 위치(행 * 10 + 열)를 고스트를 그릴 보드로 만든다. 칸 값은 그리기에만 쓰는 블록 코드다. */
    static BoardSnapshot landingSnapshot(List<Integer> positions) {
        int[][] cells = new int[GameConstants.BOARD_ROWS][GameConstants.BOARD_COLS];
        for (int[] row : cells) Arrays.fill(row, BoardSnapshot.EMPTY);
        for (int position : positions) {
            int row = position / GameConstants.BOARD_COLS;
            int col = position % GameConstants.BOARD_COLS;
            if (row >= 0 && row < GameConstants.BOARD_ROWS) cells[row][col] = TetrominoType.T.id();
        }
        return new BoardSnapshot(cells);
    }

    // ---- 키

    private boolean dispatch(KeyEvent event) {
        if (dialogOpen || event.getID() != KeyEvent.KEY_PRESSED) return false;
        if (ended != null) return true;   // 승리 배너 동안에는 키를 받지 않는다.
        return keyPressed(event.getKeyCode());
    }

    /** 1인용 키 설정으로 조작을 골라 세션에 넘긴다. 하드드롭·일시정지·나가기는 누를 때 한 번만. */
    boolean keyPressed(int keyCode) {
        KeyBindings keys = settings.get().keyBindings();
        GameAction action = keys.actionFor(keyCode).orElse(null);
        if (action == null) return false;
        if (ONE_PRESS.contains(action) && HeldKeys.isAutoRepeat()) return true;
        if (action == GameAction.QUIT) {
            confirmQuit();
        } else {
            session.handle(action);
        }
        return true;
    }

    private void confirmQuit() {
        dialogOpen = true;
        int choice;
        try {
            choice = ThemedDialog.showOptions(this, "P2P 대전 나가기",
                    "대전을 끝내고 상대와의 연결을 끊을까요?", ThemedDialog.Tone.WARNING, 1, "나가기", "계속하기");
        } finally {
            dialogOpen = false;
        }
        if (choice == 0) {
            session.close();
            router.showScreen(ScreenRouter.P2P_LOBBY);
        }
    }

    // ---- 그리기

    @Override
    protected void paintComponent(Graphics graphics) {
        super.paintComponent(graphics);
        Graphics2D g2d = (Graphics2D) graphics.create();
        try {
            UiPainter.prepare(g2d);
            int width = getWidth();
            int height = getHeight();
            UiPainter.fillBackground(g2d, width, height);
            float scale = boards.layout(width, height);
            drawHeader(g2d, width, scale);
            boards.paint(g2d);
            drawFooter(g2d, width, height, scale);
            if (session.paused()) drawOverlay(g2d, width, height, scale, "일시정지",
                    VersusScreen.keyName(settings.get().keyBindings().keyFor(GameAction.PAUSE)) + " 키로 재개 (두 사람 모두 누를 수 있어요)");
            if (session.state() == P2pSession.State.FINISHING) drawOverlay(g2d, width, height, scale, "경기 종료",
                    "결과를 확인하는 중…");
            if (ended != null) VersusScreen.drawWinnerBanner(g2d, width, height, scale, boards.vsCenterY(), ended,
                    ended.isDraw() ? null : ended.winner() == session.localPlayer() ? "내가 이겼어요" : "상대가 이겼어요");
        } finally {
            g2d.dispose();
        }
    }

    private void drawHeader(Graphics2D g2d, int width, float scale) {
        int header = Math.round(VersusScreen.BASE_HEADER * scale);
        int margin = Math.round(32 * scale);
        g2d.setFont(UiFonts.display(Math.round(28 * scale)));
        g2d.setColor(UiColors.TEXT_PRIMARY);
        FontMetrics metrics = g2d.getFontMetrics();
        UiPainter.drawText(g2d, "P2P 대전 — " + VersusScreen.modeName(session.mode()), margin,
                UiPainter.centeredBaseline(metrics, 0, header));
        int right = width - margin;
        if (session.isTimed()) {
            int seconds = session.seconds();
            String time = String.format(Locale.US, "%02d:%02d", seconds / 60, seconds % 60);
            g2d.setFont(UiFonts.mono(Math.round(28 * scale)));
            g2d.setColor(seconds <= 10 ? UiColors.PLAYER2 : UiColors.ACCENT);
            FontMetrics timeMetrics = g2d.getFontMetrics();
            g2d.drawString(time, right - timeMetrics.stringWidth(time), UiPainter.centeredBaseline(timeMetrics, 0, header));
            right -= timeMetrics.stringWidth(time) + Math.round(24 * scale);
        }
        drawConnection(g2d, right, header, scale);
    }

    /** 연결 상태와 왕복 지연. 랙이면 빨강으로 "지연 높음"을 보여 준다 (요구사항 3차 p.10). */
    private void drawConnection(Graphics2D g2d, int right, int header, float scale) {
        long rtt = session.roundTripMillis();
        String text = (session.lagging() ? "지연 높음" : "연결됨") + (rtt < 0 ? "" : "  ·  " + rtt + "ms");
        Color color = session.lagging() ? UiColors.PLAYER2 : UiColors.LINES;
        g2d.setFont(UiFonts.body(Math.round(14 * scale)));
        FontMetrics metrics = g2d.getFontMetrics();
        int textWidth = UiPainter.textWidth(metrics, text);
        int baseline = UiPainter.centeredBaseline(metrics, 0, header);
        int dot = Math.round(10 * scale);
        g2d.setColor(color);
        g2d.fillOval(right - textWidth - dot - Math.round(8 * scale), baseline - metrics.getAscent() / 2 - dot / 2 + 1,
                dot, dot);
        UiPainter.drawText(g2d, text, right - textWidth, baseline);
    }

    private void drawFooter(Graphics2D g2d, int width, int height, float scale) {
        KeyBindings keys = settings.get().keyBindings();
        String help = "이동 " + VersusScreen.keyName(keys.keyFor(GameAction.MOVE_LEFT)) + " "
                + VersusScreen.keyName(keys.keyFor(GameAction.MOVE_RIGHT)) + " "
                + VersusScreen.keyName(keys.keyFor(GameAction.SOFT_DROP))
                + "  ·  회전 " + VersusScreen.keyName(keys.keyFor(GameAction.ROTATE_CLOCKWISE)) + " "
                + VersusScreen.keyName(keys.keyFor(GameAction.ROTATE_COUNTERCLOCKWISE))
                + "  ·  하드드롭 " + VersusScreen.keyName(keys.keyFor(GameAction.HARD_DROP))
                + "  ·  일시정지 " + VersusScreen.keyName(keys.keyFor(GameAction.PAUSE))
                + "  ·  나가기 " + VersusScreen.keyName(keys.keyFor(GameAction.QUIT));
        g2d.setFont(UiFonts.body(Math.round(13 * scale)));
        g2d.setColor(UiColors.withAlpha(UiColors.TEXT_MUTED, 0.85f));
        UiPainter.drawCentered(g2d, help, width / 2,
                height - Math.round(VersusScreen.BASE_FOOTER * scale / 2) + Math.round(5 * scale));
    }

    /** 일시정지·경기 종료 안내: 반투명 막 위에 노란 테두리 카드. */
    private void drawOverlay(Graphics2D g2d, int width, int height, float scale, String title, String subtitle) {
        g2d.setColor(DIMMED_OVERLAY);
        g2d.fillRect(0, 0, width, height);
        int cardWidth = Math.round(420 * scale);
        int cardHeight = Math.round(120 * scale);
        int cardX = (width - cardWidth) / 2;
        int cardY = boards.vsCenterY() - cardHeight / 2;
        UiPainter.drawPanel(g2d, cardX, cardY, cardWidth, cardHeight, Math.round(22 * scale), UiColors.PANEL,
                UiColors.ACCENT, 3f);
        g2d.setFont(UiFonts.display(Math.round(30 * scale)));
        g2d.setColor(UiColors.ACCENT);
        UiPainter.drawCentered(g2d, title, width / 2, cardY + Math.round(54 * scale));
        g2d.setFont(UiFonts.body(Math.round(13 * scale)));
        g2d.setColor(UiColors.TEXT_SECONDARY);
        UiPainter.drawCentered(g2d, subtitle, width / 2, cardY + cardHeight - Math.round(26 * scale));
    }
}

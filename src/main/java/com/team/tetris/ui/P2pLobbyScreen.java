/*
 * P2P 대전 접속·대기실 화면 (docs/p2p-handoff.md, 목업 P2PLobbyScreen).
 */
package com.team.tetris.ui;

import java.awt.Color;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.io.IOException;
import java.util.Locale;
import java.util.Objects;

import javax.swing.JPanel;

import com.team.tetris.common.Screen;
import com.team.tetris.common.battle.Player;
import com.team.tetris.common.battle.VersusMode;
import com.team.tetris.game.versus.MatchResult;
import com.team.tetris.p2p.P2pSession;
import com.team.tetris.ui.theme.PressFeedback;
import com.team.tetris.ui.theme.ThemedDialog;
import com.team.tetris.ui.theme.UiColors;
import com.team.tetris.ui.theme.UiFonts;
import com.team.tetris.ui.theme.UiPainter;

/**
 * P2P 첫 화면이자 대기실. 세션 상태에 따라 한 화면 안의 내용이 바뀐다.
 * <ul>
 *   <li>IDLE: 서버 만들기 / 접속하기(←→). 접속하기는 서버 IP를 입력한다(최근 접속 IP를 미리 채움). Enter로 시작, Esc는 시작 메뉴.</li>
 *   <li>CONNECTING: 서버는 내 IP와 포트를, 클라이언트는 연결 중임을 보여 준다. Enter·Esc로 취소.</li>
 *   <li>LOBBY: 연결 상태·지연, P1·P2 준비 상태, 게임 방식(서버만 ←→로 고름), 지난 경기 결과. Enter로 준비,
 *       양쪽이 준비하면 경기가 시작되어 경기 화면으로 간다. Esc는 연결을 끊고 첫 화면으로.</li>
 * </ul>
 * 연결이 끊기면 세션이 IDLE로 돌아오고 이 화면에 오류를 빨간 글자로 보여 준다 (요구사항 3차 p.10).
 */
public final class P2pLobbyScreen extends JPanel implements Screen {
    private static final VersusMode[] MODES = {VersusMode.NORMAL, VersusMode.ITEM, VersusMode.TIME_LIMIT};
    private static final Color READY = UiColors.LINES;
    private static final Color ERROR = UiColors.PLAYER2;

    private final ScreenRouter router;
    private final P2pSession session;
    private final PressFeedback press = new PressFeedback(this);
    /** 첫 화면에서 고른 역할. true면 서버 만들기, false면 접속하기. */
    private boolean hostRole = true;
    private String ipInput = "";
    private String recentIp = "";
    /** 서버를 연 동안 상태 문구에서 읽어 둔 내 IP. 상대가 들어와 문구가 바뀌어도 대기실에 계속 보여 준다. */
    private String myAddresses = "";

    public P2pLobbyScreen(ScreenRouter router, P2pSession session) {
        this.router = Objects.requireNonNull(router, "router");
        this.session = Objects.requireNonNull(session, "session");
        setBackground(UiColors.BACKGROUND);
        setOpaque(true);
        setFocusable(true);
        addKeyListener(new KeyAdapter() {
            @Override public void keyPressed(KeyEvent event) {
                if (handleKey(event.getKeyCode())) event.consume();
            }

            @Override public void keyTyped(KeyEvent event) {
                typed(event.getKeyChar());
            }
        });
    }

    @Override public JPanel getPanel() { return this; }

    @Override
    public void onShow() {
        try {
            recentIp = session.recentIp();
        } catch (IOException unreadable) {
            recentIp = "";
        }
        if (ipInput.isEmpty()) ipInput = recentIp;
        requestFocusInWindow();
        repaint();
    }

    @Override
    public void onHide() {
        press.cancel();
    }

    /** 세션이 바뀔 때마다 부른다. 경기가 시작되면 경기 화면으로 넘어간다. */
    public void sessionChanged() {
        if (session.state() == P2pSession.State.CONNECTING && session.isHost()) {
            String addresses = addressesFrom(session.status());
            if (!addresses.isEmpty()) myAddresses = addresses;
        } else if (session.state() == P2pSession.State.IDLE) {
            myAddresses = "";
        }
        if (session.state() == P2pSession.State.PLAYING
                && ScreenRouter.P2P_LOBBY.equals(router.getCurrentScreenName())) {
            router.showScreen(ScreenRouter.P2P_GAME);
            return;
        }
        repaint();
    }

    /** 서버 대기 문구("…\n내 IP  a, b")에서 IP 목록만 꺼낸다. 없으면 빈 문자열. */
    static String addressesFrom(String status) {
        int at = status.indexOf("내 IP");
        return at < 0 ? "" : status.substring(at + "내 IP".length()).strip();
    }

    // ---- 키

    boolean handleKey(int keyCode) {
        if (press.isPressing()) return true;
        P2pSession.State state = session.state();
        switch (state) {
            case IDLE -> {
                switch (keyCode) {
                    case KeyEvent.VK_LEFT, KeyEvent.VK_RIGHT -> {
                        hostRole = !hostRole;
                        repaint();
                    }
                    case KeyEvent.VK_ENTER -> press.press(0, this::begin);
                    case KeyEvent.VK_BACK_SPACE -> {
                        if (!hostRole && !ipInput.isEmpty()) ipInput = ipInput.substring(0, ipInput.length() - 1);
                        repaint();
                    }
                    case KeyEvent.VK_ESCAPE -> router.showScreen(ScreenRouter.MAIN_MENU);
                    default -> { return false; }
                }
            }
            case CONNECTING -> {
                if (keyCode == KeyEvent.VK_ENTER || keyCode == KeyEvent.VK_ESCAPE) {
                    press.press(0, session::close);
                } else {
                    return false;
                }
            }
            case LOBBY -> {
                switch (keyCode) {
                    case KeyEvent.VK_LEFT -> changeMode(-1);
                    case KeyEvent.VK_RIGHT -> changeMode(1);
                    case KeyEvent.VK_ENTER -> {
                        if (!session.localReady()) press.press(0, session::ready);
                    }
                    case KeyEvent.VK_ESCAPE -> confirmLeave();
                    default -> { return false; }
                }
            }
            default -> { return false; }
        }
        return true;
    }

    private void typed(char ch) {
        if (session.state() != P2pSession.State.IDLE || hostRole) return;
        boolean ipChar = Character.isDigit(ch) || ch == '.' || ch == ':'
                || "abcdefABCDEF".indexOf(ch) >= 0;
        if (ipChar && ipInput.length() < 39) {
            ipInput += ch;
            repaint();
        }
    }

    private void begin() {
        if (hostRole) {
            session.host();
        } else {
            session.join(ipInput);
        }
    }

    /** 서버만 게임 방식을 바꾼다. 내가 준비한 뒤에는 바꾸지 않는다 (세션이 거부). */
    private void changeMode(int delta) {
        if (!session.isHost() || session.localReady()) return;
        int index = 0;
        for (int i = 0; i < MODES.length; i++) if (MODES[i] == session.mode()) index = i;
        int next = Math.max(0, Math.min(MODES.length - 1, index + delta));
        session.selectMode(MODES[next]);
    }

    private void confirmLeave() {
        if (ThemedDialog.showOptions(this, "연결 끊기", "상대와의 연결을 끊고 처음 화면으로 돌아갈까요?",
                ThemedDialog.Tone.WARNING, 1, "끊기", "계속 대기") == 0) {
            session.close();
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
            float scale = UiPainter.scale(width);
            UiPainter.fillBackground(g2d, width, height);
            int margin = Math.round(36 * scale);
            int contentWidth = width - margin * 2;
            P2pSession.State state = session.state();

            g2d.setFont(UiFonts.display(Math.round(36 * scale)));
            g2d.setColor(UiColors.CREAM);
            UiPainter.drawText(g2d, "P2P 대전", margin, Math.round(70 * scale));
            g2d.setFont(UiFonts.body(Math.round(15 * scale)));
            g2d.setColor(UiColors.TEXT_SECONDARY);
            UiPainter.drawText(g2d, "네트워크로 친구와 대결합니다", margin, Math.round(100 * scale));

            int y = Math.round(122 * scale);
            drawRolePills(g2d, margin, y, scale, state == P2pSession.State.IDLE);
            y += Math.round(54 * scale);

            int cardHeight = Math.round(130 * scale);
            drawAddressCard(g2d, margin, y, contentWidth, cardHeight, scale);
            y += cardHeight + Math.round(16 * scale);

            if (state == P2pSession.State.LOBBY) {
                int playerHeight = Math.round(76 * scale);
                int gap = Math.round(12 * scale);
                int half = (contentWidth - gap) / 2;
                drawPlayerCard(g2d, Player.P1, margin, y, half, playerHeight, scale);
                drawPlayerCard(g2d, Player.P2, margin + half + gap, y, contentWidth - half - gap, playerHeight, scale);
                y += playerHeight + Math.round(14 * scale);
                drawModeCard(g2d, margin, y, contentWidth, Math.round(84 * scale), scale);
                y += Math.round(84 * scale) + Math.round(14 * scale);
                if (session.result() != null) drawLastResult(g2d, margin, y, contentWidth, Math.round(52 * scale), scale);
            }

            drawButton(g2d, width, height, scale, state);
            g2d.setFont(UiFonts.body(Math.round(13 * scale)));
            g2d.setColor(UiColors.withAlpha(UiColors.TEXT_MUTED, 0.8f));
            UiPainter.drawCentered(g2d, hint(state), width / 2, height - Math.round(32 * scale));
        } finally {
            g2d.dispose();
        }
    }

    private void drawRolePills(Graphics2D g2d, int x, int y, float scale, boolean active) {
        int height = Math.round(42 * scale);
        int width = Math.round(150 * scale);
        int gap = Math.round(12 * scale);
        boolean host = session.state() == P2pSession.State.IDLE ? hostRole : session.isHost();
        // 처음 화면에서는 고른 쪽이 노란 버튼이고 다른 쪽은 보통 버튼이다. 접속을 시작한 뒤에는 역할을 바꿀 수 없으므로
        // 내 역할만 노랗게 두고 다른 쪽은 흐리게 그린다.
        UiPainter.ButtonState other = active ? UiPainter.ButtonState.NORMAL : UiPainter.ButtonState.DISABLED;
        UiPainter.drawPillButton(g2d, x, y, width, height, "서버 만들기",
                host ? UiPainter.ButtonState.SELECTED : other, true, Math.round(17 * scale));
        UiPainter.drawPillButton(g2d, x + width + gap, y, width, height, "접속하기",
                host ? other : UiPainter.ButtonState.SELECTED, true, Math.round(17 * scale));
    }

    /** 주소 카드: 서버는 내 IP, 클라이언트는 입력한 서버 IP. 오른쪽 위에 연결 상태와 지연을 적는다. */
    private void drawAddressCard(Graphics2D g2d, int x, int y, int width, int height, float scale) {
        P2pSession.State state = session.state();
        boolean connected = state == P2pSession.State.LOBBY || state == P2pSession.State.PLAYING
                || state == P2pSession.State.FINISHING;
        boolean host = state == P2pSession.State.IDLE ? hostRole : session.isHost();
        UiPainter.drawPanel(g2d, x, y, width, height, Math.round(20 * scale), UiColors.BOARD,
                connected ? UiColors.PLAYER1 : UiColors.PANEL_BORDER, connected ? 3f : 1.5f);
        int inner = Math.round(26 * scale);
        g2d.setFont(UiFonts.body(Math.round(13 * scale)));
        g2d.setColor(UiColors.TEXT_MUTED);
        UiPainter.drawText(g2d, host ? "내 주소" : "서버 주소", x + inner, y + Math.round(32 * scale));

        drawConnectionState(g2d, x + width - inner, y + Math.round(32 * scale), scale, state);

        String big;
        Color bigColor = UiColors.PLAYER1;
        String small;
        if (host) {
            String[] addresses = myAddresses.isEmpty() ? new String[0] : myAddresses.split(",\\s*");
            big = addresses.length > 0 ? addresses[0] : state == P2pSession.State.IDLE ? "Enter로 서버 열기" : "주소 확인 중…";
            if (addresses.length == 0) bigColor = UiColors.TEXT_SECONDARY;
            small = addresses.length > 1 ? "다른 주소: " + String.join("  ·  ", java.util.Arrays.copyOfRange(addresses, 1,
                    addresses.length)) : "포트 " + P2pSession.PORT + "  ·  상대가 이 주소로 접속합니다";
        } else {
            String shown = state == P2pSession.State.IDLE ? ipInput : session.connectedIp().isEmpty() ? ipInput
                    : session.connectedIp();
            boolean typing = state == P2pSession.State.IDLE;
            big = shown.isEmpty() && typing ? "IP 주소를 입력하세요" : shown + (typing ? "_" : "");
            if (shown.isEmpty()) bigColor = UiColors.TEXT_SECONDARY;
            small = recentIp.isEmpty() ? "포트 " + P2pSession.PORT : "최근 접속: " + recentIp;
        }
        g2d.setFont(UiFonts.monoOrFallback(big, Math.round(36 * scale)));
        g2d.setColor(bigColor);
        UiPainter.drawText(g2d, big, x + inner, y + Math.round(80 * scale));

        if (session.hasError()) {
            small = session.status();
        }
        g2d.setFont(UiFonts.body(Math.round(13 * scale)));
        g2d.setColor(session.hasError() ? ERROR : UiColors.withAlpha(UiColors.TEXT_MUTED, 0.85f));
        UiPainter.drawText(g2d, small, x + inner, y + height - Math.round(22 * scale));
    }

    /** "● 연결됨 · 지연 42ms"처럼 오른쪽 정렬로 적는다. 랙이면 빨강, 대기 중이면 노랑. */
    private void drawConnectionState(Graphics2D g2d, int right, int baseline, float scale, P2pSession.State state) {
        String text;
        Color color;
        switch (state) {
            case IDLE -> {
                if (!session.hasError()) return;
                text = "연결 안 됨";
                color = ERROR;
            }
            case CONNECTING -> {
                text = session.isHost() ? "상대 접속 대기 중" : "연결 중…";
                color = UiColors.ACCENT;
            }
            default -> {
                long rtt = session.roundTripMillis();
                String delay = rtt < 0 ? "" : "  ·  지연 " + rtt + "ms";
                text = (session.lagging() ? "지연 높음" : "연결됨") + delay;
                color = session.lagging() ? ERROR : READY;
            }
        }
        g2d.setFont(UiFonts.body(Math.round(14 * scale)));
        FontMetrics metrics = g2d.getFontMetrics();
        int textWidth = UiPainter.textWidth(metrics, text);
        int dot = Math.round(10 * scale);
        int textX = right - textWidth;
        g2d.setColor(color);
        g2d.fillOval(textX - dot - Math.round(8 * scale), baseline - metrics.getAscent() / 2 - dot / 2 + 1, dot, dot);
        UiPainter.drawText(g2d, text, textX, baseline);
    }

    private void drawPlayerCard(Graphics2D g2d, Player player, int x, int y, int width, int height, float scale) {
        boolean me = player == session.localPlayer();
        boolean ready = me ? session.localReady() : session.remoteReady();
        UiPainter.drawPanel(g2d, x, y, width, height, Math.round(16 * scale));
        g2d.setFont(UiFonts.display(Math.round(19 * scale)));
        g2d.setColor(VersusScreen.playerColor(player));
        UiPainter.drawCentered(g2d, player.name() + (me ? " (나)" : ""), x + width / 2, y + Math.round(30 * scale));
        String badge = ready ? "준비 완료" : "선택 중…";
        int badgeHeight = Math.round(24 * scale);
        int badgeWidth = UiPainter.badgeWidth(g2d, badgeHeight, badge);
        UiPainter.drawBadge(g2d, x + (width - badgeWidth) / 2, y + height - badgeHeight - Math.round(12 * scale),
                badgeHeight, badge, ready ? READY : UiColors.PANEL_BORDER, ready ? UiColors.TEXT_ON_ACCENT : UiColors.TEXT_SECONDARY);
    }

    /** 게임 방식: 서버만 ←→로 고른다. 클라이언트는 서버가 고른 방식을 본다. */
    private void drawModeCard(Graphics2D g2d, int x, int y, int width, int height, float scale) {
        UiPainter.drawPanel(g2d, x, y, width, height, Math.round(16 * scale));
        int inner = Math.round(22 * scale);
        g2d.setFont(UiFonts.display(Math.round(14 * scale)));
        g2d.setColor(UiColors.TEXT_SECONDARY);
        UiPainter.drawText(g2d, session.isHost() ? "게임 방식  ·  ←→로 선택" : "게임 방식 (서버가 선택)", x + inner,
                y + Math.round(28 * scale));
        int pillHeight = Math.round(32 * scale);
        int pillY = y + height - pillHeight - Math.round(14 * scale);
        int pillX = x + inner;
        g2d.setFont(UiFonts.display(Math.round(15 * scale)));
        FontMetrics metrics = g2d.getFontMetrics();
        for (VersusMode mode : MODES) {
            String label = VersusScreen.modeName(mode);
            int pillWidth = metrics.stringWidth(label) + Math.round(36 * scale);
            boolean chosen = mode == session.mode();
            Color fill = chosen ? UiColors.ACCENT : UiColors.PANEL;
            UiPainter.drawPanel(g2d, pillX, pillY, pillWidth, pillHeight, pillHeight / 2, fill,
                    chosen ? fill : UiColors.PANEL_BORDER, 2f);
            g2d.setColor(chosen ? UiColors.TEXT_ON_ACCENT : UiColors.TEXT_PRIMARY);
            UiPainter.drawCentered(g2d, label, pillX + pillWidth / 2, UiPainter.centeredBaseline(metrics, pillY, pillHeight));
            pillX += pillWidth + Math.round(10 * scale);
        }
    }

    /** 지난 경기 결과: 경기가 끝나면 대기실로 돌아와 여기에 보여 주고, 다시 준비하면 재대전한다. */
    private void drawLastResult(Graphics2D g2d, int x, int y, int width, int height, float scale) {
        MatchResult result = session.result();
        Player winner = result.winner();
        Color color = winner == null ? UiColors.ACCENT : VersusScreen.playerColor(winner);
        String title = winner == null ? "무승부" : winner.name() + " 승리"
                + (winner == session.localPlayer() ? " (나)" : "");
        UiPainter.drawPanel(g2d, x, y, width, height, Math.round(16 * scale), UiColors.PANEL, color, 2.5f);
        int inner = Math.round(22 * scale);
        int baseline = UiPainter.centeredBaseline(g2d.getFontMetrics(UiFonts.display(Math.round(17 * scale))), y, height);
        g2d.setFont(UiFonts.body(Math.round(13 * scale)));
        g2d.setColor(UiColors.TEXT_MUTED);
        UiPainter.drawText(g2d, "지난 경기", x + inner, baseline);
        g2d.setFont(UiFonts.display(Math.round(17 * scale)));
        g2d.setColor(color);
        UiPainter.drawText(g2d, title, x + inner + Math.round(76 * scale), baseline);
        String scores = String.format(Locale.US, "P1 %,d  :  P2 %,d", result.p1Score(), result.p2Score());
        g2d.setFont(UiFonts.mono(Math.round(15 * scale)));
        g2d.setColor(UiColors.CREAM);
        FontMetrics metrics = g2d.getFontMetrics();
        g2d.drawString(scores, x + width - inner - metrics.stringWidth(scores), baseline);
    }

    private void drawButton(Graphics2D g2d, int width, int height, float scale, P2pSession.State state) {
        String label = switch (state) {
            case IDLE -> hostRole ? "서버 열기" : "접속하기";
            case CONNECTING -> "취소";
            case LOBBY -> session.localReady() ? "상대를 기다리는 중…" : session.result() != null ? "다시 대전" : "게임 시작";
            default -> "경기 중";
        };
        boolean waiting = state == P2pSession.State.LOBBY && session.localReady();
        int buttonWidth = Math.round((waiting ? 240 : 190) * scale);
        int buttonHeight = Math.round(52 * scale);
        UiPainter.ButtonState buttonState = waiting ? UiPainter.ButtonState.DISABLED
                : state == P2pSession.State.CONNECTING ? press.stateOf(0, -1) : press.stateOf(0, 0);
        UiPainter.drawPillButton(g2d, (width - buttonWidth) / 2, height - Math.round(120 * scale), buttonWidth,
                buttonHeight, label, buttonState, true, Math.round(20 * scale));
    }

    private String hint(P2pSession.State state) {
        return switch (state) {
            case IDLE -> hostRole ? "←→ 서버·접속 선택  ·  Enter 서버 열기  ·  Esc 뒤로"
                    : "←→ 서버·접속 선택  ·  IP 입력 후 Enter  ·  Esc 뒤로";
            case CONNECTING -> "Enter·Esc 취소";
            case LOBBY -> "양쪽 모두 시작을 누르면 대전 시작  ·  연결 끊김 시 이 화면으로 복귀  ·  Esc 나가기";
            default -> "";
        };
    }
}

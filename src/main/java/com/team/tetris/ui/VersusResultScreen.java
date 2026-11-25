/*
 * 대전 승자 화면 (docs/battle/ui-handoff.md 4단계).
 */
package com.team.tetris.ui;

import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.util.Locale;
import java.util.Objects;
import java.util.function.Consumer;

import javax.swing.JPanel;

import com.team.tetris.common.Screen;
import com.team.tetris.common.battle.Player;
import com.team.tetris.common.battle.VersusMode;
import com.team.tetris.game.versus.MatchResult;
import com.team.tetris.ui.theme.MenuBackground;
import com.team.tetris.ui.theme.PressFeedback;
import com.team.tetris.ui.theme.UiColors;
import com.team.tetris.ui.theme.UiFonts;
import com.team.tetris.ui.theme.UiPainter;

/**
 * 승리 배너(승자색 테두리 패널, 컴포넌트 시트 "토스트 / 승리 배너"), 두 플레이어 점수 카드, 버튼 셋
 * (다시 대전 / 방식 선택 / 메인 화면으로). ←→로 고르고 Enter, Esc는 메인 화면.
 * 이름 입력·스코어보드로 가는 길은 없다. 대전 결과는 저장하지 않는다 (LB-F15).
 */
public final class VersusResultScreen extends JPanel implements Screen {
    private static final String[] BUTTONS = {"다시 대전", "방식 선택", "메인 화면으로"};

    private final ScreenRouter router;
    private final Consumer<VersusMode> startVersus;
    private final PressFeedback press = new PressFeedback(this);
    private MatchResult result;
    private VersusMode mode = VersusMode.NORMAL;
    private int selected;

    /**
     * @param startVersus 같은 방식으로 다시 대전을 여는 동작
     */
    public VersusResultScreen(ScreenRouter router, Consumer<VersusMode> startVersus) {
        this.router = Objects.requireNonNull(router, "router");
        this.startVersus = Objects.requireNonNull(startVersus, "startVersus");
        setBackground(UiColors.BACKGROUND);
        setOpaque(true);
        setFocusable(true);
        addKeyListener(new KeyAdapter() {
            @Override public void keyPressed(KeyEvent event) {
                handleKey(event.getKeyCode());
                event.consume();
            }
        });
    }

    /** 끝난 대전의 결과와 방식. 이 화면을 보이기 전에 부른다. */
    public void setResult(MatchResult finished, VersusMode finishedMode) {
        result = Objects.requireNonNull(finished, "result");
        mode = Objects.requireNonNull(finishedMode, "mode");
        selected = 0;
        repaint();
    }

    @Override public JPanel getPanel() { return this; }

    @Override
    public void onShow() {
        requestFocusInWindow();
        repaint();
    }

    @Override
    public void onHide() {
        press.cancel();
    }

    void handleKey(int keyCode) {
        if (press.isPressing()) return;
        switch (keyCode) {
            case KeyEvent.VK_LEFT, KeyEvent.VK_A -> select(Math.max(0, selected - 1));
            case KeyEvent.VK_RIGHT, KeyEvent.VK_D -> select(Math.min(BUTTONS.length - 1, selected + 1));
            case KeyEvent.VK_ENTER, KeyEvent.VK_SPACE -> press.press(selected, this::runSelected);
            case KeyEvent.VK_ESCAPE -> router.showScreen(ScreenRouter.MAIN_MENU);
            default -> { }
        }
    }

    private void select(int index) {
        selected = index;
        repaint();
    }

    private void runSelected() {
        switch (selected) {
            case 0 -> startVersus.accept(mode);
            case 1 -> router.showScreen(ScreenRouter.BATTLE_SELECT);
            default -> router.showScreen(ScreenRouter.MAIN_MENU);
        }
    }

    @Override
    protected void paintComponent(Graphics graphics) {
        super.paintComponent(graphics);
        Graphics2D g2d = (Graphics2D) graphics.create();
        try {
            UiPainter.prepare(g2d);
            int width = getWidth();
            int height = getHeight();
            float scale = UiPainter.scale(width);
            int centerX = width / 2;
            int bannerHeight = Math.round(132 * scale);
            int cardHeight = Math.round(120 * scale);
            int buttonHeight = Math.round(46 * scale);
            int gap = Math.round(30 * scale);
            int blockHeight = bannerHeight + gap + cardHeight + gap + buttonHeight;
            int top = Math.max(Math.round(24 * scale), (height - blockHeight) / 2 - Math.round(20 * scale));
            MenuBackground.draw(g2d, width, height, top + bannerHeight / 2, scale);
            if (result == null) return;

            drawBanner(g2d, centerX, top, Math.round(380 * scale), bannerHeight, scale);
            int y = top + bannerHeight + gap;
            int cardWidth = Math.round(190 * scale);
            int cardGap = Math.round(16 * scale);
            drawScoreCard(g2d, Player.P1, centerX - cardGap / 2 - cardWidth, y, cardWidth, cardHeight, scale);
            drawScoreCard(g2d, Player.P2, centerX + cardGap / 2, y, cardWidth, cardHeight, scale);
            y += cardHeight + gap;

            int buttonWidth = Math.round(156 * scale);
            int buttonGap = Math.round(12 * scale);
            int buttonsX = centerX - (buttonWidth * BUTTONS.length + buttonGap * (BUTTONS.length - 1)) / 2;
            for (int i = 0; i < BUTTONS.length; i++) {
                UiPainter.drawPillButton(g2d, buttonsX + i * (buttonWidth + buttonGap), y, buttonWidth, buttonHeight,
                        BUTTONS[i], press.stateOf(i, selected), true, Math.round(17 * scale));
            }

            g2d.setColor(UiColors.withAlpha(UiColors.TEXT_MUTED, 0.75f));
            g2d.setFont(UiFonts.body(Math.round(13 * scale)));
            UiPainter.drawCentered(g2d, "←→ 선택  ·  Enter 확인  ·  Esc 메인 화면으로", centerX,
                    height - Math.round(36 * scale));
        } finally {
            g2d.dispose();
        }
    }

    private void drawBanner(Graphics2D g2d, int centerX, int y, int width, int height, float scale) {
        Player winner = result.winner();
        Color color = winner == null ? UiColors.ACCENT : VersusScreen.playerColor(winner);
        String title = winner == null ? "무승부" : winner.name() + " 승리!";
        String reason = result.reason() == MatchResult.Reason.TIME_UP ? "시간 종료" : "상대가 더 쌓을 수 없음";
        int x = centerX - width / 2;
        int radius = Math.round(22 * scale);
        g2d.setColor(UiColors.darker(color, 0.55f));
        g2d.fillRoundRect(x, y + Math.round(6 * scale), width, height, radius * 2, radius * 2);
        UiPainter.drawPanel(g2d, x, y, width, height, radius, UiColors.PANEL, color, 3f);
        g2d.setFont(UiFonts.body(Math.round(13 * scale)));
        g2d.setColor(UiColors.TEXT_MUTED);
        UiPainter.drawCentered(g2d, "대전 모드 — " + VersusScreen.modeName(mode), centerX, y + Math.round(30 * scale));
        g2d.setFont(UiFonts.display(Math.round(44 * scale)));
        g2d.setColor(UiColors.darker(color, 0.55f));
        UiPainter.drawCentered(g2d, title, centerX + Math.round(2 * scale), y + Math.round(84 * scale));
        g2d.setColor(color);
        UiPainter.drawCentered(g2d, title, centerX, y + Math.round(82 * scale));
        g2d.setFont(UiFonts.body(Math.round(14 * scale)));
        g2d.setColor(UiColors.TEXT_SECONDARY);
        UiPainter.drawCentered(g2d, reason, centerX, y + height - Math.round(18 * scale));
    }

    /** 플레이어 점수 카드. 이긴 쪽은 플레이어색 테두리로 강조한다. */
    private void drawScoreCard(Graphics2D g2d, Player player, int x, int y, int width, int height, float scale) {
        Color color = VersusScreen.playerColor(player);
        boolean won = player == result.winner();
        UiPainter.drawPanel(g2d, x, y, width, height, Math.round(16 * scale), UiColors.PANEL,
                won ? color : UiColors.PANEL_BORDER, won ? 3f : 1f);
        g2d.setFont(UiFonts.display(Math.round(22 * scale)));
        g2d.setColor(color);
        UiPainter.drawCentered(g2d, player.name(), x + width / 2, y + Math.round(38 * scale));
        g2d.setFont(UiFonts.body(Math.round(11 * scale)));
        g2d.setColor(UiColors.TEXT_MUTED);
        UiPainter.drawCentered(g2d, "SCORE", x + width / 2, y + Math.round(60 * scale));
        g2d.setFont(UiFonts.mono(Math.round(30 * scale)));
        g2d.setColor(UiColors.CREAM);
        UiPainter.drawCentered(g2d, String.format(Locale.US, "%,d", result.scoreOf(player)), x + width / 2,
                y + height - Math.round(20 * scale));
    }
}

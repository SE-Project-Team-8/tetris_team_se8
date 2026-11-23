package com.team.tetris.ui;

import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.font.TextAttribute;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

import javax.swing.JPanel;

import com.team.tetris.common.Screen;
import com.team.tetris.ui.theme.PressFeedback;
import com.team.tetris.ui.theme.UiColors;
import com.team.tetris.ui.theme.UiFonts;
import com.team.tetris.ui.theme.UiPainter;

/** 게임 결과를 보여주고 입력에 따라 이름 입력, 스코어보드 또는 메인 화면으로 이동하는 화면. */
public class GameOverScreen extends JPanel implements Screen {
    /** GAMEOVER 로고 타일 색 순서 (목업 기준). 장식이라 팔레트 모드와 무관하게 고정한다. */
    private static final Color[] LOGO_COLORS = {
            new Color(0xFF7F7F), new Color(0xFFA94F), new Color(0xFFD34E), new Color(0x6FD66F),
            new Color(0x4FC3E8), new Color(0x5C8BF2), new Color(0xB07FE8)
    };

    private final ScreenRouter router;
    private int finalScore = 0;
    private int finalLevel = 1;
    private int finalLinesCleared = 0;
    // 순위권 판정은 게임/스코어보드 쪽에서 수행하고 그 결과만 전달받는다.
    private boolean qualifiesForLeaderboard;
    // 0: 이름 등록/스코어보드, 1: 메인 화면으로. 두 버튼 중 노란 강조가 들어갈 쪽이다.
    private int selectedIndex = 0;
    private final PressFeedback press = new PressFeedback(this);

    public GameOverScreen(ScreenRouter router) {
        this.router = Objects.requireNonNull(router, "router");
        setBackground(UiColors.BACKGROUND);
        setOpaque(true);
        setFocusable(true);

        addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                handleKeyPress(e.getKeyCode());
            }
        });
    }

    @Override
    public JPanel getPanel() {
        return this;
    }

    @Override
    public void onShow() {
        selectedIndex = 0;
        requestFocusInWindow();
        repaint();
    }

    @Override
    public void onHide() {
        press.cancel();
    }

    private void handleKeyPress(int keyCode) {
        // 버튼 눌림 표시 중에는 선택을 바꾸거나 다시 실행하지 않는다.
        if (press.isPressing()) return;
        switch (keyCode) {
            case KeyEvent.VK_LEFT, KeyEvent.VK_A -> select(0);
            case KeyEvent.VK_RIGHT, KeyEvent.VK_D -> select(1);
            case KeyEvent.VK_ENTER, KeyEvent.VK_SPACE -> press.press(selectedIndex, this::executeSelected);
            case KeyEvent.VK_ESCAPE -> router.showScreen(ScreenRouter.MAIN_MENU);
            default -> {
            }
        }
    }

    private void executeSelected() {
        if (selectedIndex == 1) {
            router.showScreen(ScreenRouter.MAIN_MENU);
        } else if (qualifiesForLeaderboard) {
            // 순위권 점수라면 이름을 먼저 받고, 아니면 바로 전체 기록을 보여준다.
            router.showScreen(ScreenRouter.NAME_INPUT);
        } else {
            router.showScreen(ScreenRouter.SCOREBOARD);
        }
    }

    private void select(int index) {
        selectedIndex = index;
        repaint();
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);

        // Swing이 전달한 Graphics 상태를 변경하지 않도록 복사본을 만들고, 그리기 후 해제한다.
        Graphics2D g2d = (Graphics2D) g.create();
        try {
            UiPainter.prepare(g2d);

            int screenWidth = getWidth();
            int screenHeight = getHeight();
            int centerX = screenWidth / 2;
            // 수치는 창 크기 L(600px) 기준이며 창 폭 배율을 곱한다.
            float scale = UiPainter.scale(screenWidth);

            UiPainter.fillBackground(g2d, screenWidth, screenHeight);
            UiPainter.drawSoftCircle(g2d, Math.round(screenWidth * 0.05f), Math.round(screenHeight * 0.17f),
                    Math.round(screenWidth * 0.13f), UiColors.withAlpha(LOGO_COLORS[0], 0.18f));
            UiPainter.drawSoftCircle(g2d, Math.round(screenWidth * 0.96f), Math.round(screenHeight * 0.80f),
                    Math.round(screenWidth * 0.17f), UiColors.withAlpha(LOGO_COLORS[6], 0.16f));

            // 순위권이면 배너 줄이 하나 더 들어가므로 전체 묶음 높이를 먼저 구해 세로 가운데에 둔다.
            int tile = Math.round(46 * scale);
            int cardHeight = Math.round(150 * scale);
            int bannerHeight = Math.round(46 * scale);
            int buttonHeight = Math.round(46 * scale);
            int gap = Math.round(28 * scale);
            int blockHeight = tile + gap + cardHeight + gap
                    + (qualifiesForLeaderboard ? bannerHeight + gap : 0) + buttonHeight;
            int y = Math.max(Math.round(24 * scale), (screenHeight - blockHeight) / 2 - Math.round(20 * scale));

            int logoBottom = UiPainter.drawLogo(g2d, "GAMEOVER", centerX, y, tile, LOGO_COLORS);
            y = logoBottom + gap;

            drawScoreCard(g2d, centerX, y, Math.round(300 * scale), cardHeight, scale);
            y += cardHeight + gap;

            if (qualifiesForLeaderboard) {
                int bannerWidth = Math.round(240 * scale);
                UiPainter.drawPanel(g2d, centerX - bannerWidth / 2, y, bannerWidth, bannerHeight,
                        Math.round(16 * scale), UiColors.PANEL, UiColors.ACCENT, 2.5f);
                g2d.setFont(UiFonts.display(Math.round(18 * scale)));
                g2d.setColor(UiColors.ACCENT);
                UiPainter.drawCentered(g2d, "TOP 10 진입!", centerX,
                        UiPainter.centeredBaseline(g2d.getFontMetrics(), y, bannerHeight));
                y += bannerHeight + gap;
            }

            // ←→로 고른 버튼(Enter가 실행할 동작)을 노란 버튼으로 보여 준다.
            String primary = qualifiesForLeaderboard ? "이름 등록" : "스코어보드";
            int primaryWidth = Math.round(170 * scale);
            int secondaryWidth = Math.round(170 * scale);
            int buttonGap = Math.round(14 * scale);
            int buttonsX = centerX - (primaryWidth + buttonGap + secondaryWidth) / 2;
            UiPainter.drawPillButton(g2d, buttonsX, y, primaryWidth, buttonHeight, primary,
                    buttonState(0), true, Math.round(18 * scale));
            UiPainter.drawPillButton(g2d, buttonsX + primaryWidth + buttonGap, y, secondaryWidth, buttonHeight,
                    "메인 화면으로", buttonState(1), true, Math.round(18 * scale));

            g2d.setColor(UiColors.withAlpha(UiColors.TEXT_MUTED, 0.75f));
            g2d.setFont(UiFonts.body(Math.round(13 * scale)));
            UiPainter.drawCentered(g2d, "←→ 선택  ·  Enter 확인  ·  Esc 메인 화면으로", centerX,
                    screenHeight - Math.round(36 * scale));
        } finally {
            g2d.dispose();
        }
    }

    private UiPainter.ButtonState buttonState(int index) {
        return press.stateOf(index, selectedIndex);
    }

    /** FINAL SCORE 카드: 큰 점수 + 아래에 레벨·줄 수. */
    private void drawScoreCard(Graphics2D g2d, int centerX, int y, int width, int height, float scale) {
        UiPainter.drawPanel(g2d, centerX - width / 2, y, width, height, Math.round(18 * scale));

        g2d.setFont(UiFonts.body(Math.round(12 * scale)).deriveFont(Map.of(TextAttribute.TRACKING, 0.2f)));
        g2d.setColor(UiColors.TEXT_MUTED);
        UiPainter.drawCentered(g2d, "FINAL SCORE", centerX, y + Math.round(34 * scale));

        g2d.setFont(UiFonts.mono(Math.round(46 * scale)));
        g2d.setColor(UiColors.CREAM);
        UiPainter.drawCentered(g2d, String.format(Locale.US, "%,d", finalScore), centerX,
                y + Math.round(92 * scale));

        g2d.setFont(UiFonts.mono(Math.round(14 * scale)));
        g2d.setColor(UiColors.TEXT_SECONDARY);
        UiPainter.drawCentered(g2d, "LEVEL " + finalLevel + "  ·  LINES " + finalLinesCleared, centerX,
                y + height - Math.round(22 * scale));
    }

    /** 게임 종료 시점의 결과와 외부에서 계산한 순위권 여부를 설정한다. */
    public void setGameResult(int score, int level, int linesCleared, boolean qualifiesForLeaderboard) {
        if (score < 0 || level < 1 || linesCleared < 0) {
            throw new IllegalArgumentException("Game result values are out of range");
        }
        this.finalScore = score;
        this.finalLevel = level;
        this.finalLinesCleared = linesCleared;
        this.qualifiesForLeaderboard = qualifiesForLeaderboard;
        this.selectedIndex = 0;
        repaint();
    }
}
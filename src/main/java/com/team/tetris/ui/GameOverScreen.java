package com.team.tetris.ui;

import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.util.Objects;

import javax.swing.JPanel;

import com.team.tetris.common.Screen;

/** 게임 결과를 보여주고 입력에 따라 이름 입력, 스코어보드 또는 메뉴로 이동하는 화면. */
public class GameOverScreen extends JPanel implements Screen {
    private final ScreenRouter router;
    private int finalScore = 0;
    private int finalLevel = 1;
    private int finalLinesCleared = 0;
    // 순위권 판정은 게임/스코어보드 쪽에서 수행하고 그 결과만 전달받는다.
    private boolean qualifiesForLeaderboard;

    public GameOverScreen(ScreenRouter router) {
        this.router = Objects.requireNonNull(router, "router");
        setBackground(Color.BLACK);
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
        requestFocusInWindow();
        repaint();
    }

    @Override
    public void onHide() {
        // 이 화면은 타이머나 별도 리소스를 사용하지 않아 숨길 때 정리할 작업이 없다.
    }

    private void handleKeyPress(int keyCode) {
        if (keyCode == KeyEvent.VK_ENTER || keyCode == KeyEvent.VK_SPACE) {
            // 순위권 점수라면 이름을 먼저 받고, 아니면 바로 전체 기록을 보여준다.
            if (qualifiesForLeaderboard) {
                router.showScreen(ScreenRouter.NAME_INPUT);
            } else {
                router.showScreen(ScreenRouter.SCOREBOARD);
            }
        } else if (keyCode == KeyEvent.VK_ESCAPE) {
            router.showScreen(ScreenRouter.MAIN_MENU);
        }
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);

        // Swing이 전달한 Graphics 상태를 변경하지 않도록 복사본을 만들고, 그리기 후 해제한다.
        Graphics2D g2d = (Graphics2D) g.create();
        try {
            g2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

            int screenWidth = getWidth();
            int screenHeight = getHeight();

            // 글자 폭(FontMetrics)을 기준으로 제목과 안내 문구를 화면 가운데에 배치한다.
            g2d.setColor(Color.RED);
            g2d.setFont(new Font(Font.MONOSPACED, Font.BOLD, 30));
            String title = "GAME OVER";
            FontMetrics titleMetrics = g2d.getFontMetrics();
            int titleX = (screenWidth - titleMetrics.stringWidth(title)) / 2;
            g2d.drawString(title, titleX, screenHeight / 4);

            if (qualifiesForLeaderboard) {
                g2d.setColor(Color.YELLOW);
                g2d.setFont(new Font(Font.MONOSPACED, Font.BOLD, 14));
                String subtitle = "TOP 10 QUALIFIER!";
                FontMetrics subtitleMetrics = g2d.getFontMetrics();
                int subtitleX = (screenWidth - subtitleMetrics.stringWidth(subtitle)) / 2;
                g2d.drawString(subtitle, subtitleX, (screenHeight / 4) + 35);
            }

            g2d.setFont(new Font(Font.MONOSPACED, Font.BOLD, 16));
            int startY = screenHeight / 2 - 20;
            int lineSpacing = 30;

            drawResultLine(g2d, "FINAL SCORE:", String.format("%06d", finalScore), Color.CYAN,
                    startY, screenWidth);
            drawResultLine(g2d, "LEVEL:", String.valueOf(finalLevel), Color.WHITE,
                    startY + lineSpacing, screenWidth);
            drawResultLine(g2d, "LINES CLEARED:", String.valueOf(finalLinesCleared), Color.WHITE,
                    startY + (lineSpacing * 2), screenWidth);

            g2d.setColor(Color.GRAY);
            g2d.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
            String nextAction = qualifiesForLeaderboard ? "Enter name" : "View scoreboard";
            String helpText = "[ENTER/SPACE] " + nextAction + "  [ESC] Menu";
            FontMetrics helpMetrics = g2d.getFontMetrics();
            int helpX = (screenWidth - helpMetrics.stringWidth(helpText)) / 2;
            g2d.drawString(helpText, helpX, screenHeight - 25);
        } finally {
            g2d.dispose();
        }
    }

    // 항목명은 왼쪽, 값은 오른쪽에 맞춰 화면 크기가 달라도 한눈에 비교되게 배치한다.
    private void drawResultLine(Graphics2D g2d, String label, String value, Color valueColor, int y, int screenWidth) {
        int margin = Math.max(16, screenWidth / 10);
        g2d.setColor(Color.LIGHT_GRAY);
        g2d.drawString(label, margin, y);

        g2d.setColor(valueColor);
        FontMetrics fm = g2d.getFontMetrics();
        int valueX = screenWidth - margin - fm.stringWidth(value);
        g2d.drawString(value, valueX, y);
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
        repaint();
    }
}
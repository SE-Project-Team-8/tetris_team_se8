package com.team.tetris.ui;

import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.util.List;
import java.util.Objects;

import javax.swing.JPanel;

import com.team.tetris.common.Screen;

/**
 * 시작 화면 메뉴 패널.
 * - 게임 시작, 설정, 스코어보드, 게임 종료 메뉴를 보여준다.
 * - 키보드 방향키와 W/S 키로 메뉴 이동이 가능하다.
 * - Enter 또는 Space로 선택된 메뉴를 실행한다.
 */
public class MainMenuScreen extends JPanel implements Screen {
    private final List<MenuItem> menuItems;
    private int selectedIndex = 0;

    public MainMenuScreen(ScreenRouter router) {
    Objects.requireNonNull(router, "router");
    menuItems = List.of(
        new MenuItem("게임 시작", () -> router.showScreen(ScreenRouter.GAME)),
        new MenuItem("설정", () -> router.showScreen(ScreenRouter.SETTINGS)),
        new MenuItem("스코어보드", () -> router.showScreen(ScreenRouter.SCOREBOARD)),
        new MenuItem("게임 종료", () -> System.exit(0))
    );

        setBackground(Color.BLACK);
        setFocusable(true);
        setOpaque(true);

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
        // 화면이 숨겨질 때 추가로 정리할 작업이 없으므로 비워 둔다.
    }

    /**
     * 키보드 입력을 처리한다.
     * - Up / W: 이전 항목 선택
     * - Down / S: 다음 항목 선택
     * - Enter / Space: 선택된 항목 실행
     */
    private void handleKeyPress(int keyCode) {
        switch (keyCode) {
            case KeyEvent.VK_UP, KeyEvent.VK_W -> {
                selectedIndex = (selectedIndex - 1 + menuItems.size()) % menuItems.size();
                repaint();
            }
            case KeyEvent.VK_DOWN, KeyEvent.VK_S -> {
                selectedIndex = (selectedIndex + 1) % menuItems.size();
                repaint();
            }
            case KeyEvent.VK_ENTER, KeyEvent.VK_SPACE ->
                executeSelectedMenu();
            default -> {
            }
        }
    }

    /**
     * 현재 선택된 메뉴에 따라 적절한 화면으로 전환하거나 프로그램을 종료한다.
     */
    private void executeSelectedMenu() {
        menuItems.get(selectedIndex).action().run();
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);

        Graphics2D g2d = (Graphics2D) g.create();
        try {
            g2d.setRenderingHint(
                    RenderingHints.KEY_TEXT_ANTIALIASING,
                    RenderingHints.VALUE_TEXT_ANTIALIAS_ON
            );

            int width = getWidth();
            int height = getHeight();

            drawTitle(g2d, width, height);
            drawMenuItems(g2d, width, height);
            drawHint(g2d, width, height);
        } finally {
            g2d.dispose();
        }
    }

    /**
     * 화면 제목을 그린다.
     */
    private void drawTitle(Graphics2D g2d, int width, int height) {
        g2d.setColor(Color.CYAN);
        g2d.setFont(new Font(Font.MONOSPACED, Font.BOLD, 30));

        String title = "TETRIS";
        FontMetrics titleMetrics = g2d.getFontMetrics();
        int x = (width - titleMetrics.stringWidth(title)) / 2;
        int y = height / 4;

        g2d.drawString(title, x, y);
    }

    /**
     * 선택된 메뉴 아이템과 나머지 메뉴 아이템들을 그린다.
     */
    private void drawMenuItems(Graphics2D g2d, int width, int height) {
        g2d.setFont(new Font(Font.MONOSPACED, Font.BOLD, 20));
        FontMetrics metrics = g2d.getFontMetrics();

        int startY = height / 2;
        int lineSpacing = 52;

        for (int i = 0; i < menuItems.size(); i++) {
            String label = menuItems.get(i).label();
            boolean selected = i == selectedIndex;

            if (selected) {
                g2d.setColor(Color.YELLOW);
                label = "▶ " + label;
            } else {
                g2d.setColor(Color.WHITE);
                label = "  " + label;
            }

            int x = (width - metrics.stringWidth(label)) / 2;
            int y = startY + (i * lineSpacing);
            g2d.drawString(label, x, y);
        }
    }

    /**
     * 하단에 키 조작 안내를 표시한다.
     */
    private void drawHint(Graphics2D g2d, int width, int height) {
        g2d.setColor(Color.GRAY);
        g2d.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));

        String hint = "[↑/↓ 또는 W/S] 이동  [ENTER/SPACE] 선택";
        FontMetrics metrics = g2d.getFontMetrics();
        int x = (width - metrics.stringWidth(hint)) / 2;
        int y = height - 30;

        g2d.drawString(hint, x, y);
    }

    private record MenuItem(String label, Runnable action) {
    }
}
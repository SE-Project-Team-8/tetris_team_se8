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
import java.util.function.Supplier;

import javax.swing.JPanel;

import com.team.tetris.common.Screen;
import com.team.tetris.game.GameAction;
import com.team.tetris.game.GameEngine;
import com.team.tetris.settings.KeyBindings;

// 일시정지 화면
public class PauseScreen extends JPanel implements Screen {
    private final ScreenRouter router;
    private final GameEngine engine;
    private final Supplier<KeyBindings> bindings;

    // 일시정지 메뉴 항목
    private final String[] menuItems = {
        "Resume",
        "Main Menu",
        "Exit Game"
    };

    private int selectedIndex = 0; // 현재 선택된 메뉴 인덱스

    public PauseScreen(ScreenRouter router, GameEngine engine, Supplier<KeyBindings> bindings) {
        this.router = Objects.requireNonNull(router, "router");
        this.engine = Objects.requireNonNull(engine, "engine");
        this.bindings = Objects.requireNonNull(bindings, "bindings");
        setBackground(Color.BLACK);
        setFocusable(true);
        setOpaque(true);

        // 키보드 조작 이벤트 등록
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
    }

    // 키보드 조작 처리
    private void handleKeyPress(int keyCode) {
        GameAction action = bindings.get().actionFor(keyCode).orElse(null);
        if (action == GameAction.PAUSE) {
            router.showScreen(ScreenRouter.GAME);
            return;
        }
        if (action == GameAction.QUIT) {
            quitToMenu();
            return;
        }
        switch (keyCode) {
            case KeyEvent.VK_UP -> {
                selectedIndex = (selectedIndex - 1 + menuItems.length) % menuItems.length;
                repaint();
            }
            case KeyEvent.VK_DOWN -> {
                selectedIndex = (selectedIndex + 1) % menuItems.length;
                repaint();
            }
            case KeyEvent.VK_ENTER ->
                executeSelectedMenu();
            default -> {
            }
        }
    }

    private void executeSelectedMenu() {
        switch (selectedIndex) {
            case 0 ->
                router.showScreen(ScreenRouter.GAME);
            case 1 -> quitToMenu();
            case 2 -> {
                engine.handle(GameAction.QUIT);
                System.exit(0);
            }
            default -> {
            }
        }
    }

    private void quitToMenu() {
        engine.handle(GameAction.QUIT);
        router.showScreen(ScreenRouter.MAIN_MENU);
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

            int screenWidth = getWidth();
            int screenHeight = getHeight();

            // 타이틀 렌더링
            g2d.setColor(Color.YELLOW);
            g2d.setFont(new Font(Font.MONOSPACED, Font.BOLD, 28));
            String title = "PAUSED";
            FontMetrics fmTitle = g2d.getFontMetrics();
            int titleX = (screenWidth - fmTitle.stringWidth(title)) / 2;
            g2d.drawString(title, titleX, screenHeight / 4);

            // 메뉴 항목 렌더링
            g2d.setFont(new Font(Font.MONOSPACED, Font.BOLD, 18));
            FontMetrics fmMenu = g2d.getFontMetrics();
            int startY = screenHeight / 2 - 20;
            int lineSpacing = 40;

            for (int i = 0; i < menuItems.length; i++) {
                String itemText = menuItems[i];

                if (i == selectedIndex) {
                    g2d.setColor(Color.CYAN);
                    itemText = "▶ " + itemText;
                } else {
                    g2d.setColor(Color.WHITE);
                    itemText = "   " + itemText;
                }

                int itemX = (screenWidth - fmMenu.stringWidth(itemText)) / 2;
                g2d.drawString(itemText, itemX, startY + (i * lineSpacing));
            }

            // 하단 안내 문구
            g2d.setColor(Color.GRAY);
            g2d.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
            KeyBindings keys = bindings.get();
            String helpText = "[UP/DOWN] Move  [ENTER] Select  ["
                    + KeyEvent.getKeyText(keys.keyFor(GameAction.PAUSE)) + "] Resume  ["
                    + KeyEvent.getKeyText(keys.keyFor(GameAction.QUIT)) + "] Menu";
            FontMetrics fmHelp = g2d.getFontMetrics();
            int helpX = (screenWidth - fmHelp.stringWidth(helpText)) / 2;
            g2d.drawString(helpText, helpX, screenHeight - 20);
        } finally {
            g2d.dispose();
        }
    }
}

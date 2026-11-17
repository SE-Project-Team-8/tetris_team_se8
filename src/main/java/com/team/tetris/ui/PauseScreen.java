package com.team.tetris.ui;

import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.KeyboardFocusManager;
import java.awt.KeyEventDispatcher;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.util.Objects;
import java.util.function.Supplier;

import javax.swing.JPanel;

import com.team.tetris.common.Screen;
import com.team.tetris.game.GameAction;
import com.team.tetris.game.GameEngine;
import com.team.tetris.settings.KeyBindings;
import com.team.tetris.ui.theme.UiColors;
import com.team.tetris.ui.theme.UiFonts;
import com.team.tetris.ui.theme.UiPainter;

// 일시정지 화면
public class PauseScreen extends JPanel implements Screen {
    private final ScreenRouter router;
    private final GameEngine engine;
    private final Supplier<KeyBindings> bindings;
    private final KeyEventDispatcher resumeKeyDispatcher;

    /** 아래 게임 화면을 어둡게 덮는 반투명 막 (background 토큰을 더 어둡게, 알파 0.72). */
    private static final Color DIMMED_OVERLAY = new Color(0x14, 0x0F, 0x28, 184);
    private static final Color CARD_SHADOW = new Color(0, 0, 0, 70);

    // 일시정지 메뉴 항목
    private final String[] menuItems = {
        "계속하기",
        "메뉴로 나가기",
        "게임 종료"
    };

    private int selectedIndex = 0; // 현재 선택된 메뉴 인덱스

    public PauseScreen(ScreenRouter router, GameEngine engine, Supplier<KeyBindings> bindings) {
        this.router = Objects.requireNonNull(router, "router");
        this.engine = Objects.requireNonNull(engine, "engine");
        this.bindings = Objects.requireNonNull(bindings, "bindings");
        // 포커스가 이 화면에 없어도 설정한 일시정지 키로 재개한다. P를 고정해 두면 키를 바꿔도 P가 재개한다.
        resumeKeyDispatcher = event -> {
            if (event.getID() == KeyEvent.KEY_PRESSED
                    && event.getKeyCode() == this.bindings.get().keyFor(GameAction.PAUSE)
                    && ScreenRouter.PAUSE.equals(router.getCurrentScreenName())) {
                router.showScreen(ScreenRouter.GAME);
                return true;
            }
            return false;
        };
        setBackground(DIMMED_OVERLAY);
        setFocusable(true);
        setOpaque(false);

        // 키보드 조작 이벤트 등록
        addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                handleKeyPress(e.getKeyCode());
                // 일시정지 화면에서 처리한 키가 아래에 남아 있는 게임 화면의 키 바인딩으로 넘어가지 않게 한다.
                // 예: P가 아닌 키로 바꾼 일시정지 키가 재개 직후 다시 일시정지를 거는 문제.
                e.consume();
            }
        });
    }

    @Override
    public JPanel getPanel() {
        return this;
    }

    @Override
    public void onShow() {
        KeyboardFocusManager.getCurrentKeyboardFocusManager()
                .addKeyEventDispatcher(resumeKeyDispatcher);
        requestFocusInWindow();
        repaint();
    }

    @Override
    public void onHide() {
        KeyboardFocusManager.getCurrentKeyboardFocusManager()
                .removeKeyEventDispatcher(resumeKeyDispatcher);
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
            // 시작 메뉴·게임 오버 화면처럼 Enter와 Space 모두 선택 키로 받는다.
            case KeyEvent.VK_ENTER, KeyEvent.VK_SPACE ->
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

    private static String keyName(int keyCode) {
        return keyCode == KeyEvent.VK_ESCAPE ? "Esc" : KeyEvent.getKeyText(keyCode);
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);

        Graphics2D g2d = (Graphics2D) g.create();
        try {
            UiPainter.prepare(g2d);

            int screenWidth = getWidth();
            int screenHeight = getHeight();
            // 수치는 창 크기 L(600px) 기준이며 창 폭 배율을 곱한다.
            float scale = UiPainter.scale(screenWidth);

            // 이 화면은 게임 화면 위 레이어에 겹쳐 뜬다. 반투명으로 덮어 멈춘 판이 어둡게 비쳐 보이게 한다.
            g2d.setColor(DIMMED_OVERLAY);
            g2d.fillRect(0, 0, screenWidth, screenHeight);

            // 가운데 카드: 노란 테두리 패널 + 그림자.
            int buttonHeight = Math.round(46 * scale);
            int buttonGap = Math.round(12 * scale);
            int padding = Math.round(40 * scale);
            int titleSpace = Math.round(86 * scale);
            int footerSpace = Math.round(52 * scale);
            int cardWidth = Math.round(screenWidth * 0.56f);
            int cardHeight = titleSpace + menuItems.length * buttonHeight
                    + (menuItems.length - 1) * buttonGap + footerSpace;
            int cardX = (screenWidth - cardWidth) / 2;
            int cardY = (screenHeight - cardHeight) / 2;
            int radius = Math.round(22 * scale);
            g2d.setColor(CARD_SHADOW);
            g2d.fillRoundRect(cardX - 6, cardY + 8, cardWidth + 12, cardHeight + 12, radius * 2 + 12, radius * 2 + 12);
            UiPainter.drawPanel(g2d, cardX, cardY, cardWidth, cardHeight, radius,
                    UiColors.PANEL, UiColors.ACCENT, 3f);

            g2d.setFont(UiFonts.display(Math.round(30 * scale)));
            g2d.setColor(UiColors.ACCENT);
            UiPainter.drawCentered(g2d, "일시정지", screenWidth / 2, cardY + Math.round(58 * scale));

            int buttonX = cardX + padding;
            int buttonWidth = cardWidth - padding * 2;
            int buttonY = cardY + titleSpace;
            for (int i = 0; i < menuItems.length; i++) {
                UiPainter.ButtonState state = i == selectedIndex
                        ? UiPainter.ButtonState.SELECTED
                        : UiPainter.ButtonState.NORMAL;
                UiPainter.drawPillButton(g2d, buttonX, buttonY, buttonWidth, buttonHeight,
                        menuItems[i], state, false, Math.round(18 * scale));
                buttonY += buttonHeight + buttonGap;
            }

            // 카드 하단 안내 문구. 사용자가 바꾼 키를 그대로 보여 준다.
            KeyBindings keys = bindings.get();
            String helpText = keyName(keys.keyFor(GameAction.PAUSE)) + " 키로 바로 재개  ·  "
                    + keyName(keys.keyFor(GameAction.QUIT)) + " 메뉴로";
            g2d.setFont(UiFonts.body(Math.round(12 * scale)));
            g2d.setColor(UiColors.withAlpha(UiColors.TEXT_MUTED, 0.8f));
            UiPainter.drawCentered(g2d, helpText, screenWidth / 2, cardY + cardHeight - Math.round(22 * scale));
        } finally {
            g2d.dispose();
        }
    }
}

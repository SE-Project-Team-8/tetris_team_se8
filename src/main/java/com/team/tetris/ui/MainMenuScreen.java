package com.team.tetris.ui;

import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.util.List;
import java.util.Objects;

import javax.swing.JPanel;

import com.team.tetris.common.Screen;
import com.team.tetris.ui.theme.PressFeedback;
import com.team.tetris.ui.theme.ThemedDialog;
import com.team.tetris.ui.theme.UiColors;
import com.team.tetris.ui.theme.UiFonts;
import com.team.tetris.ui.theme.UiPainter;

/**
 * 시작 화면 메뉴 패널.
 * - 일반 게임 시작, 아이템 모드 시작, 설정, 스코어보드, 게임 종료 메뉴를 보여준다.
 * - 키보드 방향키와 W/S 키로 메뉴 이동이 가능하다.
 * - Enter 또는 Space로 선택된 메뉴를 실행한다.
 */
public class MainMenuScreen extends JPanel implements Screen {
    /** 로고 타일과 장식 원에 쓰는 블럭색. 팔레트 모드와 무관한 장식이라 일반 팔레트 값을 고정으로 쓴다. */
    private static final Color[] LOGO_COLORS = {
            new Color(0x4FC3E8), new Color(0xFFD34E), new Color(0xB07FE8),
            new Color(0x6FD66F), new Color(0xFF7F7F), new Color(0xFFA94F)
    };

    // 창 크기 L(600px) 기준 수치. 그릴 때 UiPainter.scale()을 곱한다.
    private static final int LOGO_TILE = 52;
    private static final int LOGO_TO_MENU_GAP = 68;
    private static final int BUTTON_HEIGHT = 52;
    private static final int BUTTON_GAP = 14;

    private final List<MenuItem> menuItems;
    private int selectedIndex = 0;
    private final PressFeedback press = new PressFeedback(this);

    public MainMenuScreen(ScreenRouter router, Runnable startNormal, Runnable startItemMode) {
    Objects.requireNonNull(router, "router");
    Objects.requireNonNull(startNormal, "startNormal");
    Objects.requireNonNull(startItemMode, "startItemMode");
    menuItems = List.of(
        new MenuItem("게임 시작", startNormal),
        new MenuItem("아이템 모드", startItemMode),
        new MenuItem("설정", () -> router.showScreen(ScreenRouter.SETTINGS)),
        new MenuItem("스코어보드", () -> router.showScreen(ScreenRouter.SCOREBOARD)),
        new MenuItem("게임 종료", this::confirmExit)
    );

        setBackground(UiColors.BACKGROUND);
        setFocusable(true);
        setOpaque(true);

        addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                handleKeyPress(e.getKeyCode());
                // 메뉴에서 처리한 키를 소비해, 방금 표시된 게임 화면의 키 바인딩(Space = Hard Drop)으로 넘어가지 않게 한다.
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
        requestFocusInWindow();
        repaint();
    }

    @Override
    public void onHide() {
        press.cancel();
    }

    /**
     * 키보드 입력을 처리한다.
     * - Up / W: 이전 항목 선택
     * - Down / S: 다음 항목 선택
     * - Enter / Space: 선택된 항목 실행
     */
    private void handleKeyPress(int keyCode) {
        // 버튼 눌림 표시 중에는 선택을 바꾸거나 다시 실행하지 않는다.
        if (press.isPressing()) return;
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
        press.press(selectedIndex, menuItems.get(selectedIndex).action());
    }

    /** 실수로 Enter를 눌러 바로 꺼지지 않도록 한 번 더 묻는다. 처음 선택은 "취소"다. */
    private void confirmExit() {
        if (ThemedDialog.showOptions(this, "게임 종료", "게임을 종료할까요?",
                ThemedDialog.Tone.WARNING, 1, "종료", "취소") == 0) {
            System.exit(0);
        }
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);

        Graphics2D g2d = (Graphics2D) g.create();
        try {
            UiPainter.prepare(g2d);

            int width = getWidth();
            int height = getHeight();
            // 수치는 창 크기 L(600px) 기준이며 창 폭 배율을 곱해 S/M/L에서 같은 비율을 유지한다.
            float scale = UiPainter.scale(width);

            UiPainter.fillBackground(g2d, width, height);
            drawDecorations(g2d, width, height);
            // 로고와 메뉴를 한 묶음으로 보고 화면 세로 가운데에 둔다. 하단 안내 문구 몫만큼 살짝 위로 올린다.
            int tile = Math.round(LOGO_TILE * scale);
            int blockHeight = tile + Math.round(LOGO_TO_MENU_GAP * scale) + menuHeight(scale);
            int top = Math.max(Math.round(24 * scale), (height - blockHeight) / 2 - Math.round(20 * scale));
            int logoBottom = drawTitle(g2d, width, top, tile);
            drawMenuItems(g2d, width, logoBottom, scale);
            drawHint(g2d, width, height, scale);
        } finally {
            g2d.dispose();
        }
    }

    /**
     * 배경의 은은한 장식 원. 정적 도형이라 애니메이션 비용이 없다.
     */
    private static void drawDecorations(Graphics2D g2d, int width, int height) {
        UiPainter.drawSoftCircle(g2d, Math.round(width * 0.03f), Math.round(height * 0.17f),
                Math.round(width * 0.12f), UiColors.withAlpha(LOGO_COLORS[0], 0.22f));
        UiPainter.drawSoftCircle(g2d, Math.round(width * 0.97f), Math.round(height * 0.52f),
                Math.round(width * 0.16f), UiColors.withAlpha(LOGO_COLORS[4], 0.18f));
        UiPainter.drawSoftCircle(g2d, Math.round(width * 0.84f), Math.round(height * 0.78f),
                Math.round(width * 0.06f), UiColors.withAlpha(LOGO_COLORS[3], 0.16f));
        UiPainter.drawSoftCircle(g2d, Math.round(width * 0.12f), Math.round(height * 0.90f),
                Math.round(width * 0.10f), UiColors.withAlpha(LOGO_COLORS[5], 0.16f));
    }

    /**
     * 글자마다 블럭색 타일을 깐 로고를 그린다.
     *
     * @return 로고 아래 y. 메뉴 버튼은 그 아래부터 놓는다.
     */
    private static int drawTitle(Graphics2D g2d, int width, int top, int tile) {
        return UiPainter.drawLogo(g2d, "TETRIS", width / 2, top, tile, LOGO_COLORS);
    }

    /** 메뉴 버튼 전체 묶음의 높이. */
    private int menuHeight(float scale) {
        int count = menuItems.size();
        return count * Math.round(BUTTON_HEIGHT * scale) + (count - 1) * Math.round(BUTTON_GAP * scale);
    }

    /**
     * 선택된 메뉴는 노란 알약 + `>` 커서 + 살짝 확대, 나머지는 패널색 알약으로 그린다.
     */
    private void drawMenuItems(Graphics2D g2d, int width, int top, float scale) {
        int buttonWidth = Math.round(width * 0.6f);
        int buttonHeight = Math.round(BUTTON_HEIGHT * scale);
        int gap = Math.round(BUTTON_GAP * scale);
        int x = (width - buttonWidth) / 2;
        int y = top + Math.round(LOGO_TO_MENU_GAP * scale);
        float fontSize = Math.round(19 * scale);

        for (int i = 0; i < menuItems.size(); i++) {
            UiPainter.drawPillButton(g2d, x, y, buttonWidth, buttonHeight,
                    menuItems.get(i).label(), press.stateOf(i, selectedIndex), false, fontSize);
            y += buttonHeight + gap;
        }
    }

    /**
     * 하단에 키 조작 안내를 표시한다.
     */
    private static void drawHint(Graphics2D g2d, int width, int height, float scale) {
        g2d.setColor(UiColors.withAlpha(UiColors.TEXT_MUTED, 0.75f));
        g2d.setFont(UiFonts.body(Math.round(13 * scale)));
        UiPainter.drawCentered(g2d, "위/아래 이동  ·  Enter 선택", width / 2, height - Math.round(36 * scale));
    }

    private record MenuItem(String label, Runnable action) {
    }
}

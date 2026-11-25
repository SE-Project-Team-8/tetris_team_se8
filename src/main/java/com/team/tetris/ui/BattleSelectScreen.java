/*
 * 대전 게임 방식 선택 화면 (docs/battle/ui-handoff.md 3단계, 목업 BattleSelectScreen).
 */
package com.team.tetris.ui;

import java.awt.Color;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Supplier;

import javax.swing.JPanel;

import com.team.tetris.common.Screen;
import com.team.tetris.common.battle.Player;
import com.team.tetris.common.battle.VersusMode;
import com.team.tetris.common.constants.GameConstants;
import com.team.tetris.settings.GameSettings;
import com.team.tetris.ui.theme.PressFeedback;
import com.team.tetris.ui.theme.UiColors;
import com.team.tetris.ui.theme.UiFonts;
import com.team.tetris.ui.theme.UiPainter;

/**
 * 일반·아이템·시간제한 카드 셋을 세로로 늘어놓고, 아래에 P1·P2 조작 키와 "대전 시작" 버튼을 둔다.
 * ↑↓로 고르고 Enter로 시작, Esc로 시작 메뉴.
 */
public final class BattleSelectScreen extends JPanel implements Screen {
    /** 선택된 카드 바탕 (목업: panel보다 살짝 밝은 보라). */
    private static final Color SELECTED_CARD = new Color(0x3B2F60);

    private record Choice(VersusMode mode, String title, String description) { }

    private static final List<Choice> CHOICES = List.of(
            new Choice(VersusMode.NORMAL, "일반 대전",
                    "2줄 이상 지우면 상대 보드에 공격줄 추가. 먼저 못 쌓게 되는 쪽이 패배."),
            new Choice(VersusMode.ITEM, "아이템 대전",
                    "일반 대전 + 10줄마다 아이템 등장. 아이템 5종으로 역전 가능."),
            new Choice(VersusMode.TIME_LIMIT, "시간제한 대전",
                    "제한 시간 종료 시 점수가 높은 쪽이 승리. 기본 "
                            + GameConstants.VERSUS_TIME_LIMIT_SECONDS / 60 + "분."));

    private final ScreenRouter router;
    private final Supplier<GameSettings> settings;
    private final Consumer<VersusMode> startVersus;
    private final PressFeedback press = new PressFeedback(this);
    private int selected;

    /**
     * @param startVersus 고른 방식으로 대전 화면을 여는 동작
     */
    public BattleSelectScreen(ScreenRouter router, Supplier<GameSettings> settings, Consumer<VersusMode> startVersus) {
        this.router = Objects.requireNonNull(router, "router");
        this.settings = Objects.requireNonNull(settings, "settings");
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

    /** 지금 고른 방식. */
    VersusMode selectedMode() {
        return CHOICES.get(selected).mode();
    }

    void handleKey(int keyCode) {
        if (press.isPressing()) return;
        switch (keyCode) {
            case KeyEvent.VK_UP, KeyEvent.VK_W -> {
                selected = (selected - 1 + CHOICES.size()) % CHOICES.size();
                repaint();
            }
            case KeyEvent.VK_DOWN, KeyEvent.VK_S -> {
                selected = (selected + 1) % CHOICES.size();
                repaint();
            }
            case KeyEvent.VK_ENTER, KeyEvent.VK_SPACE -> {
                VersusMode mode = selectedMode();
                press.press(0, () -> startVersus.accept(mode));
            }
            case KeyEvent.VK_ESCAPE -> router.showScreen(ScreenRouter.MAIN_MENU);
            default -> { }
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
            UiPainter.fillBackground(g2d, width, height);
            int margin = Math.round(28 * scale);
            int contentWidth = width - margin * 2;

            g2d.setFont(UiFonts.display(Math.round(36 * scale)));
            g2d.setColor(UiColors.CREAM);
            UiPainter.drawCentered(g2d, "대전 모드", width / 2, Math.round(66 * scale));
            g2d.setFont(UiFonts.body(Math.round(15 * scale)));
            g2d.setColor(UiColors.TEXT_SECONDARY);
            UiPainter.drawCentered(g2d, "게임 방식을 선택하세요", width / 2, Math.round(98 * scale));

            int y = Math.round(126 * scale);
            int cardHeight = Math.round(84 * scale);
            int cardGap = Math.round(14 * scale);
            for (int i = 0; i < CHOICES.size(); i++) {
                drawCard(g2d, CHOICES.get(i), i == selected, margin, y, contentWidth, cardHeight, scale);
                y += cardHeight + cardGap;
            }

            y += Math.round(10 * scale);
            drawKeysPanel(g2d, margin, y, contentWidth, Math.round(70 * scale), scale);

            int buttonWidth = Math.round(180 * scale);
            int buttonHeight = Math.round(50 * scale);
            int buttonY = height - Math.round(120 * scale);
            // 대전 시작 버튼은 늘 노란 강조 버튼이고, Enter를 누르면 눌림 모습을 보인 뒤 시작한다.
            UiPainter.drawPillButton(g2d, (width - buttonWidth) / 2, buttonY, buttonWidth, buttonHeight, "대전 시작",
                    press.stateOf(0, 0), true, Math.round(20 * scale));

            g2d.setFont(UiFonts.body(Math.round(13 * scale)));
            g2d.setColor(UiColors.withAlpha(UiColors.TEXT_MUTED, 0.8f));
            UiPainter.drawCentered(g2d, "위/아래 선택  ·  Enter 시작  ·  Esc 뒤로", width / 2,
                    height - Math.round(36 * scale));
        } finally {
            g2d.dispose();
        }
    }

    /** 선택된 카드는 노란 테두리 + 노란 제목과 `>` 커서, 나머지는 패널색 카드. */
    private static void drawCard(Graphics2D g2d, Choice choice, boolean chosen, int x, int y, int width, int height,
                                 float scale) {
        int radius = Math.round(20 * scale);
        if (chosen) {
            g2d.setColor(new Color(0, 0, 0, 60));
            g2d.fillRoundRect(x, y + Math.round(5 * scale), width, height, radius * 2, radius * 2);
            UiPainter.drawPanel(g2d, x, y, width, height, radius, SELECTED_CARD, UiColors.ACCENT, 3f);
        } else {
            UiPainter.drawPanel(g2d, x, y, width, height, radius, UiColors.PANEL, UiColors.PANEL_BORDER, 2f);
        }
        int textX = x + Math.round(24 * scale);
        g2d.setFont(UiFonts.display(Math.round(21 * scale)));
        g2d.setColor(chosen ? UiColors.ACCENT : UiColors.TEXT_PRIMARY);
        UiPainter.drawText(g2d, (chosen ? "> " : "") + choice.title(), textX, y + Math.round(36 * scale));
        g2d.setFont(UiFonts.body(Math.round(13 * scale)));
        g2d.setColor(UiColors.TEXT_SECONDARY);
        UiPainter.drawText(g2d, choice.description(), textX, y + height - Math.round(20 * scale));
    }

    /** 조작 키 패널: 현재 설정값의 P1(왼쪽, P1색)·P2(오른쪽, P2색) 키. */
    private void drawKeysPanel(Graphics2D g2d, int x, int y, int width, int height, float scale) {
        UiPainter.drawPanel(g2d, x, y, width, height, Math.round(16 * scale), UiColors.BOARD, UiColors.PANEL_BORDER, 1f);
        int inner = Math.round(22 * scale);
        g2d.setFont(UiFonts.display(Math.round(13 * scale)));
        g2d.setColor(UiColors.TEXT_MUTED);
        UiPainter.drawText(g2d, "조작", x + inner, y + Math.round(24 * scale));
        g2d.setFont(UiFonts.body(Math.round(12 * scale)));
        int baseline = y + height - Math.round(16 * scale);
        GameSettings current = settings.get();
        g2d.setColor(UiColors.PLAYER1);
        UiPainter.drawText(g2d, "P1 — " + VersusScreen.playerKeys(current, Player.P1), x + inner, baseline);
        String p2 = "P2 — " + VersusScreen.playerKeys(current, Player.P2);
        g2d.setColor(UiColors.PLAYER2);
        FontMetrics metrics = g2d.getFontMetrics();
        UiPainter.drawText(g2d, p2, x + width - inner - UiPainter.textWidth(metrics, p2), baseline);
    }
}

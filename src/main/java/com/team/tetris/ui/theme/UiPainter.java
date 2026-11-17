package com.team.tetris.ui.theme;

import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Stroke;
import java.awt.geom.AffineTransform;
import java.awt.geom.RoundRectangle2D;
import java.util.Locale;

/**
 * 여러 화면이 공유하는 뿌요풍 컴포넌트 드로잉 모음 (디자인 스펙 §6).
 * 상태를 갖지 않으며 호출자가 넘긴 Graphics2D에만 그린다.
 */
public final class UiPainter {
    /** 디자인 기준 폭. 창 크기 M(600px)을 배율 1.0으로 본다. */
    public static final float BASE_WIDTH = 600f;

    private static final float SELECTED_SCALE = 1.05f;
    private static final float DISABLED_ALPHA = 0.55f;

    /** 알약 버튼의 표시 상태. */
    public enum ButtonState {
        /** panel 채움 + panelBorder 테두리. */
        NORMAL,
        /** accent 채움 + `>` 커서 + 살짝 확대. */
        SELECTED,
        /** 클릭·Enter 직후 80ms 동안의 눌린 모습. */
        PRESSED,
        /** 선택할 수 없는 항목. */
        DISABLED
    }

    private UiPainter() {
    }

    /**
     * 도형·글자 안티앨리어싱을 켠다. 각 화면의 paintComponent 시작에서 호출한다.
     *
     * @param g 설정할 그래픽 컨텍스트
     */
    public static void prepare(Graphics2D g) {
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
    }

    /**
     * 화면 폭에 맞춘 배율. S/M/L 창에서 같은 레이아웃을 유지하기 위해 모든 고정 수치에 곱한다.
     *
     * @param width 컴포넌트 폭(px)
     * @return M 크기 기준 배율
     */
    public static float scale(int width) {
        return Math.max(0.5f, width / BASE_WIDTH);
    }

    /**
     * 화면 전체를 background 색으로 채운다.
     */
    public static void fillBackground(Graphics2D g, int width, int height) {
        g.setColor(UiColors.BACKGROUND);
        g.fillRect(0, 0, width, height);
    }

    /**
     * 메뉴·게임오버 배경의 은은한 장식 원을 그린다. 정적 도형이라 리페인트 비용만 든다.
     *
     * @param color 원 색. 알파 0.2 내외를 권장한다.
     */
    public static void drawSoftCircle(Graphics2D g, int centerX, int centerY, int radius, Color color) {
        g.setColor(color);
        g.fillOval(centerX - radius, centerY - radius, radius * 2, radius * 2);
    }

    /**
     * NEXT·SCORE 같은 정보 패널. panel 채움 + panelBorder 1px.
     */
    public static void drawPanel(Graphics2D g, int x, int y, int width, int height, int radius) {
        drawPanel(g, x, y, width, height, radius, UiColors.PANEL, UiColors.PANEL_BORDER, 1f);
    }

    /**
     * 채움·테두리 색과 두께를 지정하는 패널. 선택 강조나 경고 테두리에 쓴다.
     *
     * @param borderWidth 테두리 두께(px). 0 이하이면 테두리를 그리지 않는다.
     */
    public static void drawPanel(Graphics2D g, int x, int y, int width, int height, int radius,
                                 Color fill, Color border, float borderWidth) {
        g.setColor(fill);
        g.fillRoundRect(x, y, width, height, radius * 2, radius * 2);
        if (borderWidth > 0 && border != null) {
            strokeRoundRect(g, x, y, width, height, radius, border, borderWidth);
        }
    }

    /**
     * 선 두께만큼 안쪽으로 들여 라운드 사각 테두리를 그린다. 두꺼운 선이 바깥으로 삐져나오지 않게 한다.
     */
    public static void strokeRoundRect(Graphics2D g, int x, int y, int width, int height, int radius,
                                       Color color, float strokeWidth) {
        Stroke old = g.getStroke();
        g.setColor(color);
        g.setStroke(new BasicStroke(strokeWidth));
        float inset = strokeWidth / 2f;
        g.draw(new RoundRectangle2D.Float(
                x + inset, y + inset, width - strokeWidth, height - strokeWidth,
                Math.max(0, radius * 2 - strokeWidth), Math.max(0, radius * 2 - strokeWidth)));
        g.setStroke(old);
    }

    /**
     * 알약형 버튼(radius = 높이/2)을 그린다.
     *
     * @param label 버튼 글자
     * @param state 표시 상태
     * @param centered true면 글자를 가운데, false면 왼쪽 여백 기준으로 정렬한다
     * @param fontSize 글자 크기(px)
     */
    public static void drawPillButton(Graphics2D g, int x, int y, int width, int height,
                                      String label, ButtonState state, boolean centered, float fontSize) {
        Graphics2D g2 = (Graphics2D) g.create();
        try {
            if (state == ButtonState.SELECTED) {
                // 선택 항목은 중심 기준으로 5% 확대한다.
                AffineTransform t = new AffineTransform();
                double cx = x + width / 2.0;
                double cy = y + height / 2.0;
                t.translate(cx, cy);
                t.scale(SELECTED_SCALE, SELECTED_SCALE);
                t.translate(-cx, -cy);
                g2.transform(t);
            }
            if (state == ButtonState.DISABLED) {
                g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, DISABLED_ALPHA));
            }

            int arc = height;
            int shadow = Math.max(2, Math.round(height * 0.09f));
            boolean filled = state == ButtonState.SELECTED || state == ButtonState.PRESSED;
            Color fill = filled ? UiColors.ACCENT : UiColors.PANEL;
            int top = y;
            if (state == ButtonState.PRESSED) {
                // 눌림: 음영 제거 + 2px 하강 + 8% 어둡게.
                fill = UiColors.darker(fill, 0.08f);
                top += 2;
            } else if (filled) {
                g2.setColor(UiColors.darker(fill, 0.18f));
                g2.fillRoundRect(x, y + shadow, width, height, arc, arc);
            }

            g2.setColor(fill);
            g2.fillRoundRect(x, top, width, height, arc, arc);
            if (!filled) {
                strokeRoundRect(g2, x, top, width, height, height / 2, UiColors.PANEL_BORDER, 2f);
            }

            Color textColor = filled ? UiColors.TEXT_ON_ACCENT : UiColors.TEXT_PRIMARY;
            if (state == ButtonState.DISABLED) {
                textColor = UiColors.TEXT_MUTED;
            }
            String text = filled ? "> " + label : label;
            g2.setFont(UiFonts.display(fontSize));
            g2.setColor(textColor);
            FontMetrics fm = g2.getFontMetrics();
            int textY = centeredBaseline(fm, top, height);
            int textX = centered
                    ? x + (width - fm.stringWidth(text)) / 2
                    : x + Math.round(height * 0.6f);
            g2.drawString(text, textX, textY);
        } finally {
            g2.dispose();
        }
    }

    /**
     * 작은 알약 뱃지(NEW, NORMAL, P1 등)를 그리고 그린 폭을 돌려준다.
     *
     * @param height 뱃지 높이(px). 글자 크기는 높이의 60%다.
     * @return 뱃지 폭(px). 옆에 다른 요소를 이어 붙일 때 쓴다.
     */
    public static int drawBadge(Graphics2D g, int x, int y, int height, String text, Color background, Color foreground) {
        Font font = UiFonts.display(height * 0.6f);
        FontMetrics fm = g.getFontMetrics(font);
        int padding = Math.round(height * 0.6f);
        int width = badgeWidth(g, height, text);
        g.setColor(background);
        g.fillRoundRect(x, y, width, height, height, height);
        g.setFont(font);
        g.setColor(foreground);
        g.drawString(text, x + padding, centeredBaseline(fm, y, height));
        return width;
    }

    /**
     * {@link #drawBadge}가 그릴 뱃지의 폭. 오른쪽 정렬할 때 먼저 잰다.
     */
    public static int badgeWidth(Graphics2D g, int height, String text) {
        FontMetrics fm = g.getFontMetrics(UiFonts.display(height * 0.6f));
        return fm.stringWidth(text) + Math.round(height * 0.6f) * 2;
    }

    /**
     * 난이도 이름에 맞는 뱃지 색을 고른다. HARD는 명도 대비를 위해 글자를 cream으로 쓴다.
     *
     * @param difficulty EASY/NORMAL/HARD (대소문자 무시)
     * @return [배경, 글자] 순서의 색 쌍
     */
    public static Color[] difficultyBadgeColors(String difficulty) {
        String key = difficulty == null ? "" : difficulty.trim().toUpperCase(Locale.ROOT);
        return switch (key) {
            case "EASY" -> new Color[]{UiColors.DIFFICULTY_EASY, UiColors.TEXT_ON_ACCENT};
            case "HARD" -> new Color[]{UiColors.DIFFICULTY_HARD, UiColors.CREAM};
            default -> new Color[]{UiColors.DIFFICULTY_NORMAL, UiColors.TEXT_ON_ACCENT};
        };
    }

    /**
     * 글자마다 블럭색 타일을 깐 로고(TETRIS, GAMEOVER)를 가운데 정렬로 그린다.
     *
     * @param text 로고 글자
     * @param centerX 가로 중심
     * @param top 타일 윗변 y
     * @param tile 타일 한 변(px)
     * @param colors 글자 순서대로 순환할 타일 색
     * @return 타일 아랫변(음영 포함) y. 아래에 부제를 붙일 때 쓴다.
     */
    public static int drawLogo(Graphics2D g, String text, int centerX, int top, int tile, Color[] colors) {
        int gap = Math.max(2, Math.round(tile * 0.08f));
        int count = text.length();
        int totalWidth = count * tile + (count - 1) * gap;
        int x = centerX - totalWidth / 2;
        int radius = Math.round(tile * 0.22f);
        int shadow = Math.max(2, Math.round(tile * 0.1f));
        Font font = UiFonts.display(tile * 0.78f);
        g.setFont(font);
        FontMetrics fm = g.getFontMetrics();
        for (int i = 0; i < count; i++) {
            Color color = colors[i % colors.length];
            int tx = x + i * (tile + gap);
            g.setColor(UiColors.darker(color, 0.2f));
            g.fillRoundRect(tx, top + shadow, tile, tile, radius * 2, radius * 2);
            g.setColor(color);
            g.fillRoundRect(tx, top, tile, tile, radius * 2, radius * 2);
            String letter = String.valueOf(text.charAt(i));
            g.setColor(UiColors.CELL_GLYPH);
            g.drawString(letter, tx + (tile - fm.stringWidth(letter)) / 2,
                    centeredBaseline(fm, top, tile));
        }
        return top + tile + shadow;
    }

    /**
     * 높이 {@code height}인 영역에서 글자가 세로 가운데에 보이도록 하는 기준선 y.
     * Jua처럼 줄 간격(leading)이 큰 글꼴은 getHeight() 기준으로 맞추면 글자가 위로 뜨므로
     * 실제 글자 높이(ascent+descent)만으로 계산한다.
     */
    public static int centeredBaseline(FontMetrics fm, int top, int height) {
        return top + (height + fm.getAscent() - fm.getDescent()) / 2;
    }

    /**
     * 기준선 y에 가로 가운데 정렬로 문자열을 그린다.
     */
    public static void drawCentered(Graphics2D g, String text, int centerX, int baselineY) {
        FontMetrics fm = g.getFontMetrics();
        g.drawString(text, centerX - fm.stringWidth(text) / 2, baselineY);
    }
}

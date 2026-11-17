package com.team.tetris.ui.theme;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Stroke;
import java.awt.geom.RoundRectangle2D;

/**
 * 뿌요풍 보드 셀 드로잉 (디자인 스펙 §2). 모든 수치는 셀 한 변 {@code size}에서 파생하므로
 * 창 크기 S/M/L이나 NEXT 미리보기처럼 크기가 달라도 같은 모양이 나온다.
 *
 * 모든 셀은 문자를 함께 그린다(텍스트 기반 요구사항). 기본 블럭의 입 {@code _}, 공격줄의 {@code >_<},
 * 아이템 칸의 고유 문자가 그 문자다.
 */
public final class CellPainter {
    /**
     * 이보다 작은 셀은 눈을 생략하고 문자만 그린다. 스펙은 20px이지만 S 창(테두리 제외 폭 약 464px)의
     * 보드 셀이 19px이라 표정이 사라지므로, 눈이 2px 이상으로 그려지는 12px까지 낮춘다.
     */
    public static final int MIN_FACE_SIZE = 12;

    private static final String MOUTH = "_";
    private static final Color HIGHLIGHT = new Color(255, 255, 255, 128);
    private static final Color EMPTY_SLOT = new Color(255, 255, 255, 9);
    /** 줄 삭제 팝 표정 색 (스펙 §3.1). */
    private static final Color POP_FACE = new Color(0xFFA94F);

    private CellPainter() {
    }

    /**
     * 블럭 한 칸: 라운드 바탕 + 하단 음영 + 좌상단 하이라이트 + 눈 2개 + 입 문자.
     *
     * @param color 블럭색 (팔레트 모드에 따라 호출자가 고른다)
     */
    public static void drawBlock(Graphics2D g, int x, int y, int size, Color color) {
        fillCandy(g, x, y, size, color);
        drawFace(g, x, y, size, UiColors.CELL_GLYPH, false);
    }

    /**
     * 공격줄 한 칸: garbage 바탕 + {@code > <} 눈 + 입 문자.
     */
    public static void drawGarbage(Graphics2D g, int x, int y, int size) {
        fillCandy(g, x, y, size, UiColors.GARBAGE);
        drawFace(g, x, y, size, UiColors.GARBAGE_GLYPH, true);
    }

    /**
     * 착지 예고(고스트): 바탕 없이 점선 테두리와 같은 색의 눈·입만 그린다.
     */
    public static void drawGhost(Graphics2D g, int x, int y, int size) {
        Stroke old = g.getStroke();
        float stroke = Math.max(1f, size * 0.07f);
        float dash = Math.max(2f, size * 0.16f);
        g.setStroke(new BasicStroke(stroke, BasicStroke.CAP_BUTT, BasicStroke.JOIN_ROUND, 10f,
                new float[]{dash, dash * 0.75f}, 0f));
        g.setColor(UiColors.GHOST_OUTLINE);
        float inset = stroke / 2f;
        float arc = radius(size) * 2f;
        g.draw(new RoundRectangle2D.Float(x + inset, y + inset, size - stroke, size - stroke, arc, arc));
        g.setStroke(old);
        drawFace(g, x, y, size, UiColors.GHOST_OUTLINE, false);
    }

    /**
     * 포함형 아이템 칸(L·S 등): 모체 블럭색 바탕 + cream 테두리 + 고유 문자. 눈은 그리지 않는다.
     *
     * @param color 아이템이 붙은 블럭의 색
     * @param symbol 아이템 문자 (ItemRegistry와 일치)
     */
    public static void drawItem(Graphics2D g, int x, int y, int size, Color color, char symbol) {
        fillCandy(g, x, y, size, color);
        UiPainter.strokeRoundRect(g, x, y, size, size, radius(size), UiColors.CREAM, Math.max(1.5f, size * 0.07f));
        drawLetter(g, x, y, size, symbol, UiColors.CELL_GLYPH, 0.45f);
    }

    /**
     * 줄 삭제 팝 한 칸: cream 단색 + 주황 `^ ^` 눈과 입. 지워지기 직전 블럭이 웃는 모습이다.
     */
    public static void drawPop(Graphics2D g, int x, int y, int size) {
        int arc = radius(size) * 2;
        g.setColor(UiColors.CREAM);
        g.fillRoundRect(x, y, size, size, arc, arc);
        int centerX = x + size / 2;
        g.setColor(POP_FACE);
        if (size >= MIN_FACE_SIZE) {
            g.setFont(UiFonts.mono(Math.max(8f, Math.round(size * 0.30f))));
            FontMetrics fm = g.getFontMetrics();
            int eyeOffset = Math.round(size * 0.13f);
            int baseline = y + Math.round(size * 0.36f) + fm.getAscent() / 2;
            g.drawString("^", centerX - eyeOffset - fm.stringWidth("^") / 2, baseline);
            g.drawString("^", centerX + eyeOffset - fm.stringWidth("^") / 2, baseline);
        }
        g.setFont(UiFonts.mono(Math.max(8f, Math.round(size * 0.38f))));
        FontMetrics fm = g.getFontMetrics();
        g.drawString(MOUTH, centerX - fm.stringWidth(MOUTH) / 2, y + Math.round(size * 0.60f));
    }

    /**
     * 빈 칸 자리 표시. 보드 위에 아주 옅은 라운드 슬롯을 깐다.
     */
    public static void drawEmptySlot(Graphics2D g, int x, int y, int size) {
        g.setColor(EMPTY_SLOT);
        int arc = radius(size) * 2;
        g.fillRoundRect(x, y, size, size, arc, arc);
    }

    /**
     * 셀 가운데에 한 글자를 그린다. 아이템 아이콘 위에 문자를 덧그릴 때도 쓴다.
     *
     * @param scale 셀 크기 대비 글자 크기 비율
     */
    public static void drawLetter(Graphics2D g, int x, int y, int size, char letter, Color color, float scale) {
        g.setFont(UiFonts.mono(Math.max(8f, Math.round(size * scale))));
        FontMetrics fm = g.getFontMetrics();
        String text = String.valueOf(letter);
        g.setColor(color);
        g.drawString(text, x + (size - fm.stringWidth(text)) / 2,
                y + (size - fm.getAscent() - fm.getDescent()) / 2 + fm.getAscent());
    }

    /** 스펙의 모서리 반지름 c×0.26. */
    public static int radius(int size) {
        return Math.max(2, Math.round(size * 0.26f));
    }

    // 하단 음영 띠는 음영색으로 전체를 칠한 뒤 위쪽을 본색으로 덮어 둥근 모서리를 유지한다.
    private static void fillCandy(Graphics2D g, int x, int y, int size, Color color) {
        int arc = radius(size) * 2;
        int shade = Math.max(1, Math.round(size * 0.11f));
        g.setColor(UiColors.darker(color, 0.18f));
        g.fillRoundRect(x, y, size, size, arc, arc);
        g.setColor(color);
        g.fillRoundRect(x, y, size, size - shade, arc, arc);
        if (size >= MIN_FACE_SIZE) {
            g.setColor(HIGHLIGHT);
            g.fillOval(x + Math.round(size * 0.15f), y + Math.round(size * 0.12f),
                    Math.max(1, Math.round(size * 0.26f)), Math.max(1, Math.round(size * 0.17f)));
        }
    }

    private static void drawFace(Graphics2D g, int x, int y, int size, Color color, boolean squint) {
        g.setColor(color);
        int centerX = x + size / 2;
        if (size >= MIN_FACE_SIZE) {
            int eyeOffset = Math.round(size * 0.11f);
            int eyeY = y + Math.round(size * 0.36f);
            if (squint) {
                // 공격줄은 `>` `<` 문자로 찡그린 눈을 그린다.
                g.setFont(UiFonts.mono(Math.max(8f, Math.round(size * 0.30f))));
                FontMetrics fm = g.getFontMetrics();
                int baseline = eyeY + fm.getAscent() / 2 - 1;
                g.drawString(">", centerX - eyeOffset - fm.stringWidth(">") / 2, baseline);
                g.drawString("<", centerX + eyeOffset - fm.stringWidth("<") / 2, baseline);
            } else {
                int eye = Math.max(2, Math.round(size * 0.11f));
                g.fillOval(centerX - eyeOffset - eye / 2, eyeY - eye / 2, eye, eye);
                g.fillOval(centerX + eyeOffset - eye / 2, eyeY - eye / 2, eye, eye);
            }
        }
        // 입은 밑줄 문자다. 글꼴의 밑줄은 기준선 아래에 있으므로 기준선을 셀 60% 높이에 둔다.
        g.setFont(UiFonts.mono(Math.max(8f, Math.round(size * 0.38f))));
        FontMetrics fm = g.getFontMetrics();
        g.drawString(MOUTH, centerX - fm.stringWidth(MOUTH) / 2, y + Math.round(size * 0.60f));
    }
}

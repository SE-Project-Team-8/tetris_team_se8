package com.team.tetris.ui.theme;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Stroke;
import java.awt.geom.AffineTransform;
import java.awt.geom.Area;
import java.awt.geom.Path2D;
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
    private static final Color BOMB_FUSE = new Color(0x4A1B0C);
    private static final Color FLAME_OUTER = new Color(0xD85A30);
    private static final Color FLAME_INNER = new Color(0xFFA94F);
    private static final Color FLAME_LETTER = new Color(0x4A1B0C);

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
     * 폭탄 아이템 칸 (스펙 §4 B): 모체 블럭색 + cream 테두리 위에 검은 구, 좌상단 하이라이트,
     * 우상단 도화선과 노란 불꽃 점, 구 안의 작은 문자.
     */
    public static void drawBombItem(Graphics2D g, int x, int y, int size, Color color, char symbol) {
        fillCandy(g, x, y, size, color);
        UiPainter.strokeRoundRect(g, x, y, size, size, radius(size), UiColors.CREAM, Math.max(1.5f, size * 0.07f));
        int ball = Math.round(size * 0.53f);
        int ballX = x + (size - ball) / 2;
        int ballY = y + (size - ball) / 2 + Math.round(size * 0.04f);
        // 도화선: 구 오른쪽 위에서 비스듬히 뻗은 선 + 끝의 불꽃 점.
        Stroke old = g.getStroke();
        g.setStroke(new BasicStroke(Math.max(1.5f, size * 0.07f), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.setColor(BOMB_FUSE);
        int fuseStartX = ballX + Math.round(ball * 0.78f);
        int fuseStartY = ballY + Math.round(ball * 0.18f);
        int fuseEndX = x + Math.round(size * 0.84f);
        int fuseEndY = y + Math.round(size * 0.14f);
        g.drawLine(fuseStartX, fuseStartY, fuseEndX, fuseEndY);
        g.setStroke(old);
        int spark = Math.max(2, Math.round(size * 0.14f));
        g.setColor(UiColors.ACCENT);
        g.fillOval(fuseEndX - spark / 2, fuseEndY - spark / 2, spark, spark);
        g.setColor(UiColors.BACKGROUND);
        g.fillOval(ballX, ballY, ball, ball);
        g.setColor(new Color(255, 255, 255, 70));
        g.fillOval(ballX + Math.round(ball * 0.18f), ballY + Math.round(ball * 0.14f),
                Math.max(1, Math.round(ball * 0.28f)), Math.max(1, Math.round(ball * 0.2f)));
        drawLetter(g, ballX, ballY, ball, symbol, UiColors.CREAM, 0.6f);
    }

    /**
     * 피버 아이템 칸 (스펙 §4 F): 모체 블럭색 + cream 테두리 위에 위로 뾰족한 물방울 불꽃
     * (45° 돌린 라운드 사각, 바깥 주황빨강·안쪽 주황)과 가운데 문자.
     */
    public static void drawFeverItem(Graphics2D g, int x, int y, int size, Color color, char symbol) {
        fillCandy(g, x, y, size, color);
        UiPainter.strokeRoundRect(g, x, y, size, size, radius(size), UiColors.CREAM, Math.max(1.5f, size * 0.07f));
        double centerX = x + size / 2.0;
        double centerY = y + size * 0.56;
        fillFlame(g, centerX, centerY, size * 0.46, FLAME_OUTER);
        fillFlame(g, centerX, centerY + size * 0.06, size * 0.30, FLAME_INNER);
        g.setFont(UiFonts.mono(Math.max(8f, Math.round(size * 0.31f))));
        FontMetrics fm = g.getFontMetrics();
        g.setColor(FLAME_LETTER);
        String text = String.valueOf(symbol);
        g.drawString(text, (int) Math.round(centerX - fm.stringWidth(text) / 2.0),
                (int) Math.round(centerY + (fm.getAscent() - fm.getDescent()) / 2.0 + size * 0.04));
    }

    // 꼭짓점 하나만 각진 라운드 사각을 45° 돌리면 위로 뾰족한 물방울 모양이 된다.
    private static void fillFlame(Graphics2D g, double centerX, double centerY, double side, Color color) {
        double r = side * 0.5;
        Path2D.Double drop = new Path2D.Double();
        // 왼쪽 위 모서리만 뾰족하고 나머지 세 모서리는 둥근 사각형.
        drop.moveTo(-side / 2, -side / 2);
        drop.lineTo(side / 2 - r, -side / 2);
        drop.quadTo(side / 2, -side / 2, side / 2, -side / 2 + r);
        drop.lineTo(side / 2, side / 2 - r);
        drop.quadTo(side / 2, side / 2, side / 2 - r, side / 2);
        drop.lineTo(-side / 2 + r, side / 2);
        drop.quadTo(-side / 2, side / 2, -side / 2, side / 2 - r);
        drop.closePath();
        AffineTransform transform = new AffineTransform();
        transform.translate(centerX, centerY);
        transform.rotate(Math.toRadians(45));
        g.setColor(color);
        g.fill(transform.createTransformedShape(drop));
    }

    /**
     * 무게추(모루) 실루엣 (스펙 §4 W). 모루 칸들을 한 덩어리로 이어 강철색으로 채우고 cream 외곽선을 두른다.
     * 위가 비어 있는 칸에는 하이라이트를, 가로로 이어진 칸 사이에는 연한 구분선을 넣고,
     * 텍스트 기반 요구에 따라 모든 칸에 문자를 그린다.
     *
     * @param cells 모루가 차지한 칸 [행][열] 마스크
     * @param originX 마스크 (0, 0) 칸의 왼쪽 위 x
     * @param originY 마스크 (0, 0) 칸의 왼쪽 위 y
     * @param pitch 칸 간격
     * @param inset 칸 안쪽 여백 (일반 셀과 같은 값)
     */
    public static void drawAnvil(Graphics2D g, boolean[][] cells, int originX, int originY, int pitch, int inset,
                                 char symbol) {
        int size = pitch - inset * 2;
        int radius = radius(size);
        Area body = new Area();
        for (int row = 0; row < cells.length; row++) {
            for (int col = 0; col < cells[row].length; col++) {
                if (!cells[row][col]) continue;
                int cellX = originX + col * pitch;
                int cellY = originY + row * pitch;
                // 이웃 칸 쪽으로는 모서리 반지름만큼 넘쳐 그려 이음새의 둥근 홈을 메운다.
                double left = filled(cells, row, col - 1) ? cellX - radius : cellX + inset;
                double right = filled(cells, row, col + 1) ? cellX + pitch + radius : cellX + pitch - inset;
                double top = filled(cells, row - 1, col) ? cellY - radius : cellY + inset;
                double bottom = filled(cells, row + 1, col) ? cellY + pitch + radius : cellY + pitch - inset;
                body.add(new Area(new RoundRectangle2D.Double(left, top, right - left, bottom - top,
                        radius * 2.0, radius * 2.0)));
            }
        }
        g.setColor(UiColors.STEEL);
        g.fill(body);

        for (int row = 0; row < cells.length; row++) {
            for (int col = 0; col < cells[row].length; col++) {
                if (!cells[row][col]) continue;
                int cellX = originX + col * pitch;
                int cellY = originY + row * pitch;
                if (!filled(cells, row - 1, col) && size >= MIN_FACE_SIZE) {
                    g.setColor(UiColors.withAlpha(UiColors.STEEL_HIGHLIGHT, 0.9f));
                    g.fillRoundRect(cellX + inset + Math.round(size * 0.18f), cellY + inset + Math.round(size * 0.12f),
                            Math.round(size * 0.64f), Math.max(2, Math.round(size * 0.12f)), size / 4, size / 4);
                }
                if (filled(cells, row, col + 1)) {
                    g.setColor(new Color(255, 255, 255, 41));
                    int lineX = cellX + pitch;
                    g.drawLine(lineX, cellY + inset + size / 5, lineX, cellY + pitch - inset - size / 5);
                }
                drawLetter(g, cellX + inset, cellY + inset, size, symbol, UiColors.CREAM, 0.38f);
            }
        }

        Stroke old = g.getStroke();
        g.setStroke(new BasicStroke(Math.max(1.5f, size * 0.07f)));
        g.setColor(UiColors.CREAM);
        g.draw(body);
        g.setStroke(old);
    }

    private static boolean filled(boolean[][] cells, int row, int col) {
        return row >= 0 && row < cells.length && col >= 0 && col < cells[row].length && cells[row][col];
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

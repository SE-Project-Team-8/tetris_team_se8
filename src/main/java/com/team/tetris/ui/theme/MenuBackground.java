package com.team.tetris.ui.theme;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RadialGradientPaint;
import java.awt.Shape;
import java.awt.geom.AffineTransform;
import java.awt.geom.Area;
import java.awt.geom.Point2D;
import java.awt.geom.RoundRectangle2D;

/**
 * 시작 메뉴·게임 오버 화면이 같이 쓰는 배경.
 * 바탕색 위에 가장자리를 어둡게 누르는 비네트, 화면 가장자리에 반쯤 걸친 고스트 테트로미노 실루엣,
 * 로고 뒤 은은한 보랏빛 조명을 차례로 깐다. 모두 정적 도형이라 애니메이션 비용이 없다.
 */
public final class MenuBackground {
    /** 실루엣에 쓰는 블럭색(I O T S Z J L). 팔레트 모드와 무관한 장식이라 일반 팔레트 값을 고정으로 쓴다. */
    private static final Color[] BLOCK_COLORS = {
            new Color(0x4FC3E8), new Color(0xFFD34E), new Color(0xB07FE8), new Color(0x6FD66F),
            new Color(0xFF7F7F), new Color(0x5C8BF2), new Color(0xFFA94F)
    };
    /** 로고 뒤 은은한 조명색. */
    private static final Color GLOW = new Color(0x5A44A0);
    /** 실루엣 한 칸 크기. 창 크기 L(600px) 기준이며 배율을 곱한다. */
    private static final int GHOST_CELL = 38;

    /**
     * 고스트 테트로미노 실루엣: 칸 좌표, 화면 비율 위치(x, y), 회전 각도(도), 색 번호.
     * 화면 가장자리에 반쯤 걸치게 두어 가운데 메뉴·카드를 가리지 않는다.
     */
    private static final GhostPiece[] GHOST_PIECES = {
            new GhostPiece(new int[][]{{0, 0}, {1, 0}, {2, 0}, {1, 1}}, 0.04f, 0.15f, -16, 2),
            new GhostPiece(new int[][]{{0, 0}, {0, 1}, {0, 2}, {1, 2}}, 0.97f, 0.40f, 14, 6),
            new GhostPiece(new int[][]{{1, 0}, {2, 0}, {0, 1}, {1, 1}}, 0.07f, 0.80f, 10, 3),
            new GhostPiece(new int[][]{{0, 0}, {1, 0}, {2, 0}, {3, 0}}, 0.90f, 0.93f, -22, 0),
    };

    private MenuBackground() {
    }

    /**
     * 화면 전체에 배경을 그린다.
     *
     * @param glowY 조명 중심 y. 보통 로고 가운데다.
     * @param scale {@link UiPainter#scale} 배율
     */
    public static void draw(Graphics2D g, int width, int height, int glowY, float scale) {
        Graphics2D g2 = (Graphics2D) g.create();
        try {
            UiPainter.fillBackground(g2, width, height);

            float vignetteRadius = Math.max(width, height) * 0.78f;
            g2.setPaint(new RadialGradientPaint(new Point2D.Float(width / 2f, height * 0.42f), vignetteRadius,
                    new float[]{0.45f, 1f},
                    new Color[]{UiColors.withAlpha(UiColors.BOARD, 0f), UiColors.withAlpha(UiColors.BOARD, 0.85f)}));
            g2.fillRect(0, 0, width, height);

            for (GhostPiece piece : GHOST_PIECES) {
                drawGhostPiece(g2, piece, width, height, scale);
            }

            g2.setPaint(new RadialGradientPaint(new Point2D.Float(width / 2f, glowY), width * 0.6f,
                    new float[]{0f, 1f},
                    new Color[]{UiColors.withAlpha(GLOW, 0.45f), UiColors.withAlpha(GLOW, 0f)}));
            g2.fillRect(0, 0, width, height);
        } finally {
            g2.dispose();
        }
    }

    /** 칸을 이어 붙인 테트로미노를 블럭색으로 아주 옅게 채우고 윤곽만 또렷하게 그린다. */
    private static void drawGhostPiece(Graphics2D g, GhostPiece piece, int width, int height, float scale) {
        float cell = GHOST_CELL * scale;
        float arc = cell * 0.48f;
        Area shape = new Area();
        int columns = 0;
        int rows = 0;
        for (int[] c : piece.cells()) {
            shape.add(new Area(new RoundRectangle2D.Float(c[0] * cell, c[1] * cell, cell, cell, arc, arc)));
            columns = Math.max(columns, c[0] + 1);
            rows = Math.max(rows, c[1] + 1);
        }
        AffineTransform placement = new AffineTransform();
        placement.translate(width * piece.x(), height * piece.y());
        placement.rotate(Math.toRadians(piece.angle()));
        placement.translate(-columns * cell / 2, -rows * cell / 2);

        Color color = BLOCK_COLORS[piece.colorIndex()];
        Shape placed = placement.createTransformedShape(shape);
        g.setColor(UiColors.withAlpha(color, 0.07f));
        g.fill(placed);
        // 칸 사이 경계는 가늘고 옅게, 바깥 윤곽은 조금 더 또렷하게 그린다.
        g.setColor(UiColors.withAlpha(color, 0.12f));
        g.setStroke(new BasicStroke(Math.max(1f, scale)));
        for (int[] c : piece.cells()) {
            g.draw(placement.createTransformedShape(
                    new RoundRectangle2D.Float(c[0] * cell, c[1] * cell, cell, cell, arc, arc)));
        }
        g.setColor(UiColors.withAlpha(color, 0.30f));
        g.setStroke(new BasicStroke(Math.max(1f, 1.6f * scale)));
        g.draw(placed);
    }

    private record GhostPiece(int[][] cells, float x, float y, int angle, int colorIndex) {
    }
}

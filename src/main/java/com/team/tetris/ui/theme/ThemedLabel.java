package com.team.tetris.ui.theme;

import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Insets;

import javax.swing.JLabel;
import javax.swing.SwingConstants;
import javax.swing.plaf.basic.BasicHTML;

/**
 * Jua 본문 글자용 라벨. JLabel은 번들 서체에 없는 글자(·, ← 등)를 네모로 그리므로,
 * 그런 글자가 섞인 한 줄 문자열은 그 글자만 시스템 서체로 바꿔 직접 그린다.
 * 서체가 모든 글자를 그릴 수 있거나 HTML 문자열이면 JLabel이 그대로 그린다.
 */
public class ThemedLabel extends JLabel {
    public ThemedLabel(String text) {
        super(text);
    }

    public ThemedLabel(String text, int horizontalAlignment) {
        super(text, horizontalAlignment);
    }

    private boolean needsFallback() {
        String text = getText();
        return text != null && !text.isEmpty() && !BasicHTML.isHTMLString(text)
                && getFont() != null && getFont().canDisplayUpTo(text) != -1;
    }

    @Override
    public Dimension getPreferredSize() {
        Dimension size = super.getPreferredSize();
        if (isPreferredSizeSet() || !needsFallback()) {
            return size;
        }
        Insets insets = getInsets();
        size.width = UiPainter.textWidth(getFontMetrics(getFont()), getText()) + insets.left + insets.right;
        return size;
    }

    @Override
    protected void paintComponent(Graphics g) {
        if (!needsFallback()) {
            super.paintComponent(g);
            return;
        }
        Graphics2D g2d = (Graphics2D) g.create();
        try {
            if (isOpaque()) {
                g2d.setColor(getBackground());
                g2d.fillRect(0, 0, getWidth(), getHeight());
            }
            UiPainter.prepare(g2d);
            g2d.setFont(getFont());
            g2d.setColor(getForeground());
            FontMetrics metrics = g2d.getFontMetrics();
            Insets insets = getInsets();
            int textWidth = UiPainter.textWidth(metrics, getText());
            int innerWidth = getWidth() - insets.left - insets.right;
            int x = switch (getHorizontalAlignment()) {
                case SwingConstants.CENTER -> insets.left + (innerWidth - textWidth) / 2;
                case SwingConstants.RIGHT, SwingConstants.TRAILING -> getWidth() - insets.right - textWidth;
                default -> insets.left;
            };
            // JLabel과 같이 줄 높이(getHeight) 기준으로 세로 가운데에 둔다.
            int innerHeight = getHeight() - insets.top - insets.bottom;
            int baseline = insets.top + (innerHeight - metrics.getHeight()) / 2 + metrics.getAscent();
            UiPainter.drawText(g2d, getText(), x, baseline);
        } finally {
            g2d.dispose();
        }
    }
}

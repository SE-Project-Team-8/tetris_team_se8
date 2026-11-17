package com.team.tetris.ui.theme;

import java.awt.Graphics;
import java.awt.Graphics2D;

import javax.swing.JButton;

/**
 * 알약형 Swing 버튼. 마우스 클릭과 키보드가 같은 ActionListener를 쓰는 화면(이름 입력, 스코어보드)에서 쓴다.
 * 크기와 글자 크기는 창 배율에 맞춰 소유 화면이 {@link #setPreferredSize}와 {@link #setFontSize}로 정한다.
 */
public class PillButton extends JButton {
    private boolean primary;
    private float fontSize = 18f;

    /**
     * @param label 버튼 글자
     * @param primary true면 노란 강조 버튼(Enter가 실행하는 동작), false면 패널색 보조 버튼
     */
    public PillButton(String label, boolean primary) {
        super(label);
        this.primary = primary;
        setContentAreaFilled(false);
        setBorderPainted(false);
        setFocusPainted(false);
        setOpaque(false);
    }

    /** 강조 여부를 바꾼다. 대화상자처럼 포커스를 받은 버튼을 노랗게 보여 줄 때 쓴다. */
    public void setPrimary(boolean primary) {
        this.primary = primary;
        repaint();
    }

    public void setFontSize(float fontSize) {
        this.fontSize = fontSize;
        repaint();
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2d = (Graphics2D) g.create();
        try {
            UiPainter.prepare(g2d);
            UiPainter.ButtonState state;
            if (!isEnabled()) {
                state = UiPainter.ButtonState.DISABLED;
            } else if (getModel().isPressed()) {
                state = UiPainter.ButtonState.PRESSED;
            } else {
                state = primary ? UiPainter.ButtonState.SELECTED : UiPainter.ButtonState.NORMAL;
            }
            // 선택 상태의 5% 확대와 하단 음영이 버튼 영역 밖으로 잘리지 않게 약간 안쪽에 그린다.
            int inset = Math.round(getHeight() * 0.08f);
            UiPainter.drawPillButton(g2d, inset, 0, getWidth() - inset * 2, getHeight() - inset,
                    getText(), state, true, fontSize);
        } finally {
            g2d.dispose();
        }
    }
}

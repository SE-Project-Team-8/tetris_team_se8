package com.team.tetris.ui.theme;

import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.event.ActionEvent;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.util.List;

import javax.swing.AbstractAction;
import javax.swing.JButton;
import javax.swing.KeyStroke;

/**
 * 알약형 Swing 버튼. 마우스 클릭과 키보드가 같은 ActionListener를 쓰는 화면(이름 입력, 스코어보드)에서 쓴다.
 * 크기와 글자 크기는 창 배율에 맞춰 소유 화면이 {@link #setPreferredSize}와 {@link #setFontSize}로 정한다.
 */
public class PillButton extends JButton {
    private boolean primary;
    private float fontSize = 18f;
    /** 눌린 뒤 동작을 실행하기 직전의 확정 플래시 중인지. */
    private boolean confirming;

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

    /**
     * 키보드 포커스를 받은 동안만 노란 강조 버튼으로 그리고 Enter로도 누를 수 있게 한다.
     * 버튼 여러 개가 나란한 화면에서 강조가 "지금 Enter가 누를 버튼" 하나에만 보이게 할 때 쓴다.
     */
    public void enableFocusHighlight() {
        addFocusListener(new FocusAdapter() {
            @Override public void focusGained(FocusEvent event) { setPrimary(true); }
            @Override public void focusLost(FocusEvent event) { setPrimary(false); }
        });
        // JButton은 기본으로 Space만 받으므로 같은 눌림 동작을 Enter에도 연결한다.
        getInputMap(WHEN_FOCUSED).put(KeyStroke.getKeyStroke("ENTER"), "pressed");
        getInputMap(WHEN_FOCUSED).put(KeyStroke.getKeyStroke("released ENTER"), "released");
    }

    /**
     * 나란한 버튼들 사이를 ←→로 오가게 한다. 양 끝에서는 더 움직이지 않는다.
     */
    public static void linkArrowKeys(List<PillButton> row) {
        for (int index = 0; index < row.size(); index++) {
            PillButton button = row.get(index);
            PillButton previous = row.get(Math.max(0, index - 1));
            PillButton next = row.get(Math.min(row.size() - 1, index + 1));
            button.getInputMap(WHEN_FOCUSED).put(KeyStroke.getKeyStroke("LEFT"), "focusPrevious");
            button.getInputMap(WHEN_FOCUSED).put(KeyStroke.getKeyStroke("RIGHT"), "focusNext");
            button.getActionMap().put("focusPrevious", new AbstractAction() {
                @Override public void actionPerformed(ActionEvent event) { previous.requestFocusInWindow(); }
            });
            button.getActionMap().put("focusNext", new AbstractAction() {
                @Override public void actionPerformed(ActionEvent event) { next.requestFocusInWindow(); }
            });
        }
    }

    /**
     * 눌림(마우스·Space·Enter)이 끝나면 확정 플래시(크림 테두리 + ✦)를 잠깐 보여 준 뒤 동작을 실행한다.
     * 플래시 중에 다시 눌러도 동작은 한 번만 실행된다.
     */
    @Override
    protected void fireActionPerformed(ActionEvent event) {
        int flash = PressFeedback.confirmMillis();
        if (flash <= 0) {
            super.fireActionPerformed(event);
            return;
        }
        if (confirming) return;
        confirming = true;
        repaint();
        javax.swing.Timer timer = new javax.swing.Timer(flash, done -> {
            confirming = false;
            repaint();
            super.fireActionPerformed(event);
        });
        timer.setRepeats(false);
        timer.start();
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2d = (Graphics2D) g.create();
        try {
            UiPainter.prepare(g2d);
            UiPainter.ButtonState state;
            if (!isEnabled()) {
                state = UiPainter.ButtonState.DISABLED;
            } else if (confirming) {
                state = UiPainter.ButtonState.CONFIRMED;
            } else if (getModel().isPressed()) {
                state = UiPainter.ButtonState.PRESSED;
            } else {
                state = primary ? UiPainter.ButtonState.SELECTED : UiPainter.ButtonState.NORMAL;
            }
            // 선택 상태의 5% 확대와 하단 음영, 확정 플래시의 오른쪽 위 ✦가 버튼 영역 밖으로 잘리지 않게 안쪽에 그린다.
            int inset = Math.round(getHeight() * 0.08f);
            int top = Math.round(getHeight() * 0.06f);
            UiPainter.drawPillButton(g2d, inset, top, getWidth() - inset * 2, getHeight() - inset - top,
                    getText(), state, true, fontSize);
        } finally {
            g2d.dispose();
        }
    }
}

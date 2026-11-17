package com.team.tetris.ui.theme;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dialog;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GraphicsDevice;
import java.awt.GraphicsEnvironment;
import java.awt.Window;
import java.awt.event.ActionEvent;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.util.ArrayList;
import java.util.List;

import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.KeyStroke;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;

/**
 * 뿌요풍 디자인의 모달 대화상자. JOptionPane 대신 써서 오류·확인 창도 게임 화면과 같은 모양으로 띄운다.
 * <p>
 * 키보드: ←→ 버튼 이동, Enter 선택, Esc 취소. 포커스가 있는 버튼이 노란 강조 버튼이 된다.
 * 모든 메서드는 EDT에서 호출하며, 창이 닫힐 때까지 반환하지 않는다(JOptionPane과 같다).
 */
public final class ThemedDialog {
    /** 대화상자 성격. 테두리와 제목 색을 정한다. */
    public enum Tone {
        INFO(UiColors.ACCENT),
        WARNING(UiColors.ACCENT),
        ERROR(UiColors.PLAYER2);

        private final Color color;

        Tone(Color color) {
            this.color = color;
        }
    }

    /** Esc나 창 닫기로 아무 버튼도 고르지 않았을 때 {@link #showOptions}가 돌려주는 값. */
    public static final int CLOSED = -1;

    private ThemedDialog() {
    }

    /**
     * 확인 버튼 하나짜리 알림.
     *
     * @param parent 대화상자를 띄울 기준 컴포넌트. null이면 화면 가운데에 띄운다.
     */
    public static void showMessage(Component parent, String title, String message, Tone tone) {
        showOptions(parent, title, message, tone, 0, "확인");
    }

    /**
     * 예/아니오 확인.
     *
     * @return 확인 버튼을 골랐으면 true. 취소·Esc는 false.
     */
    public static boolean showConfirm(Component parent, String title, String message, Tone tone,
                                      String confirmLabel, String cancelLabel) {
        return showOptions(parent, title, message, tone, 0, confirmLabel, cancelLabel) == 0;
    }

    /**
     * 버튼 여러 개 중 하나를 고르게 한다.
     *
     * @param defaultIndex 처음 포커스를 줄 버튼
     * @param options 왼쪽부터 놓을 버튼 글자
     * @return 고른 버튼의 번호. Esc로 닫으면 {@link #CLOSED}.
     */
    public static int showOptions(Component parent, String title, String message, Tone tone,
                                  int defaultIndex, String... options) {
        Window owner = parent == null ? null
                : parent instanceof Window window ? window : SwingUtilities.getWindowAncestor(parent);
        float scale = UiPainter.scale(owner != null ? owner.getWidth() : (int) UiPainter.BASE_WIDTH);
        JDialog dialog = new JDialog(owner, title, Dialog.ModalityType.APPLICATION_MODAL);
        dialog.setUndecorated(true);
        dialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
        if (supportsTranslucency()) {
            // 둥근 카드 바깥이 투명하게 보이도록 창 배경을 비운다.
            dialog.setBackground(new Color(0, 0, 0, 0));
        }

        int[] result = {CLOSED};
        Card card = new Card(tone.color, Math.round(20 * scale), !supportsTranslucency());
        int padding = Math.round(28 * scale);
        card.setBorder(BorderFactory.createEmptyBorder(Math.round(24 * scale), padding, Math.round(22 * scale), padding));
        card.setLayout(new BorderLayout(0, Math.round(14 * scale)));

        JLabel titleLabel = new JLabel(title, SwingConstants.CENTER);
        titleLabel.setFont(UiFonts.displayOrFallback(title, Math.round(22 * scale)));
        titleLabel.setForeground(tone.color);
        card.add(titleLabel, BorderLayout.NORTH);

        JLabel messageLabel = new JLabel(wrapHtml(message, Math.round(320 * scale)), SwingConstants.CENTER);
        messageLabel.setFont(UiFonts.body(Math.round(14 * scale)));
        messageLabel.setForeground(UiColors.TEXT_PRIMARY);
        card.add(messageLabel, BorderLayout.CENTER);

        JPanel buttonRow = new JPanel(new FlowLayout(FlowLayout.CENTER, Math.round(10 * scale), 0));
        buttonRow.setOpaque(false);
        List<PillButton> buttons = new ArrayList<>();
        float fontSize = Math.round(16 * scale);
        for (int index = 0; index < options.length; index++) {
            int choice = index;
            PillButton button = new PillButton(options[index], false);
            button.setFontSize(fontSize);
            FontMetrics metrics = button.getFontMetrics(UiFonts.display(fontSize));
            button.setPreferredSize(new Dimension(
                    Math.max(Math.round(110 * scale), metrics.stringWidth("> " + options[index]) + Math.round(44 * scale)),
                    Math.round(44 * scale)));
            button.addActionListener(event -> {
                result[0] = choice;
                dialog.dispose();
            });
            // 포커스가 있는 버튼이 Enter로 실행될 버튼이므로 노랗게 강조한다.
            button.addFocusListener(new FocusAdapter() {
                @Override public void focusGained(FocusEvent event) { button.setPrimary(true); }
                @Override public void focusLost(FocusEvent event) { button.setPrimary(false); }
            });
            buttons.add(button);
            buttonRow.add(button);
        }
        card.add(buttonRow, BorderLayout.SOUTH);
        bindKeys(card, dialog, buttons, result);

        dialog.setContentPane(card);
        dialog.pack();
        dialog.setLocationRelativeTo(owner);
        int initial = Math.max(0, Math.min(defaultIndex, buttons.size() - 1));
        if (!buttons.isEmpty()) {
            PillButton first = buttons.get(initial);
            first.setPrimary(true);
            SwingUtilities.invokeLater(first::requestFocusInWindow);
        }
        dialog.setVisible(true);
        return result[0];
    }

    private static void bindKeys(JComponent root, JDialog dialog, List<PillButton> buttons, int[] result) {
        var inputs = root.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW);
        var actions = root.getActionMap();
        inputs.put(KeyStroke.getKeyStroke("ESCAPE"), "close");
        actions.put("close", new AbstractAction() {
            @Override public void actionPerformed(ActionEvent event) {
                result[0] = CLOSED;
                dialog.dispose();
            }
        });
        inputs.put(KeyStroke.getKeyStroke("ENTER"), "choose");
        actions.put("choose", new AbstractAction() {
            @Override public void actionPerformed(ActionEvent event) {
                for (PillButton button : buttons) {
                    if (button.isFocusOwner()) {
                        button.doClick();
                        return;
                    }
                }
                if (!buttons.isEmpty()) buttons.get(0).doClick();
            }
        });
        inputs.put(KeyStroke.getKeyStroke("LEFT"), "previous");
        actions.put("previous", new AbstractAction() {
            @Override public void actionPerformed(ActionEvent event) {
                moveFocus(buttons, -1);
            }
        });
        inputs.put(KeyStroke.getKeyStroke("RIGHT"), "next");
        actions.put("next", new AbstractAction() {
            @Override public void actionPerformed(ActionEvent event) {
                moveFocus(buttons, 1);
            }
        });
    }

    private static void moveFocus(List<PillButton> buttons, int direction) {
        for (int index = 0; index < buttons.size(); index++) {
            if (buttons.get(index).isFocusOwner()) {
                int next = Math.max(0, Math.min(buttons.size() - 1, index + direction));
                buttons.get(next).requestFocusInWindow();
                return;
            }
        }
    }

    // JLabel은 HTML이면 줄바꿈을 지원한다. 사용자 문자열이 섞일 수 있으므로 특수 문자를 이스케이프한다.
    private static String wrapHtml(String message, int width) {
        String escaped = message.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\n", "<br>");
        return "<html><div style='text-align:center;width:" + width + "px'>" + escaped + "</div></html>";
    }

    private static boolean supportsTranslucency() {
        try {
            GraphicsDevice device = GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice();
            return device.isWindowTranslucencySupported(GraphicsDevice.WindowTranslucency.PERPIXEL_TRANSLUCENT);
        } catch (RuntimeException unsupported) {
            return false;
        }
    }

    /** 패널색 둥근 카드 + 성격 색 테두리. */
    private static final class Card extends JPanel {
        private final Color border;
        private final int radius;
        private final boolean fillCorners;

        /** @param fillCorners 창 투명도를 못 쓰는 환경에서 둥근 모서리 바깥을 배경색으로 칠할지 */
        Card(Color border, int radius, boolean fillCorners) {
            this.border = border;
            this.radius = radius;
            this.fillCorners = fillCorners;
            setOpaque(fillCorners);
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2d = (Graphics2D) g.create();
            try {
                UiPainter.prepare(g2d);
                if (fillCorners) {
                    g2d.setColor(UiColors.BACKGROUND);
                    g2d.fillRect(0, 0, getWidth(), getHeight());
                }
                UiPainter.drawPanel(g2d, 0, 0, getWidth(), getHeight(), radius, UiColors.PANEL, border, 3f);
            } finally {
                g2d.dispose();
            }
        }
    }
}

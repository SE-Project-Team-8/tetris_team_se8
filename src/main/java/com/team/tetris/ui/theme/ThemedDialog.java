package com.team.tetris.ui.theme;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dialog;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GraphicsDevice;
import java.awt.GraphicsEnvironment;
import java.awt.KeyEventDispatcher;
import java.awt.KeyboardFocusManager;
import java.awt.Window;
import java.awt.event.ActionEvent;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

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
        float scale = dialogScale(owner);
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

        JLabel titleLabel = new ThemedLabel(title, SwingConstants.CENTER);
        titleLabel.setFont(UiFonts.display(Math.round(22 * scale)));
        titleLabel.setForeground(tone.color);
        card.add(titleLabel, BorderLayout.NORTH);

        Font messageFont = UiFonts.body(Math.round(15 * scale));
        JLabel messageLabel = new JLabel(wrapHtml(message, Math.round(320 * scale), messageFont), SwingConstants.CENTER);
        messageLabel.setFont(messageFont);
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
            button.enableFocusHighlight();
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

    /** {@link #captureKey}로 받은 키: 키 코드와 위치(왼쪽·오른쪽 Shift 구분). */
    public record KeyPress(int keyCode, int location) { }

    /**
     * 새 조작 키를 하나 받는다. 설정 화면의 "키 변경" 팝업이다.
     * <p>
     * Esc를 뺀 아무 키나 누르면 그 키를 돌려준다. Tab·Enter·Space·방향키·Shift도 받는다. 팝업을 연 키(Enter·Space)를
     * 누르고 있는 동안 들어오는 자동 반복은 새 키로 치지 않는다. {@code problem}이 문제를 알려 주면 팝업 안에 그 글을 빨갛게
     * 띄우고 다시 기다린다. Esc나 "취소" 버튼은 null을 돌려준다.
     *
     * @param openingKey 팝업을 연 키 코드. 마우스로 열었으면 {@link KeyEvent#VK_UNDEFINED}
     * @param problem 받은 키에 문제가 있으면 안내 글, 없으면 null
     */
    public static KeyPress captureKey(Component parent, String title, String message, int openingKey,
                                      Function<KeyPress, String> problem) {
        Window owner = parent == null ? null
                : parent instanceof Window window ? window : SwingUtilities.getWindowAncestor(parent);
        float scale = dialogScale(owner);
        JDialog dialog = new JDialog(owner, title, Dialog.ModalityType.APPLICATION_MODAL);
        dialog.setUndecorated(true);
        dialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
        if (supportsTranslucency()) dialog.setBackground(new Color(0, 0, 0, 0));

        KeyPress[] result = {null};
        Card card = new Card(Tone.INFO.color, Math.round(20 * scale), !supportsTranslucency());
        int padding = Math.round(28 * scale);
        card.setBorder(BorderFactory.createEmptyBorder(Math.round(24 * scale), padding, Math.round(22 * scale), padding));
        card.setLayout(new BorderLayout(0, Math.round(12 * scale)));

        JLabel titleLabel = new ThemedLabel(title, SwingConstants.CENTER);
        titleLabel.setFont(UiFonts.display(Math.round(22 * scale)));
        titleLabel.setForeground(Tone.INFO.color);
        card.add(titleLabel, BorderLayout.NORTH);

        Font messageFont = UiFonts.body(Math.round(15 * scale));
        int textWidth = Math.round(300 * scale);
        JLabel messageLabel = new JLabel(wrapHtml(message, textWidth, messageFont), SwingConstants.CENTER);
        messageLabel.setFont(messageFont);
        messageLabel.setForeground(UiColors.TEXT_PRIMARY);
        // 문제 안내 줄은 처음부터 자리를 잡아 두어 글이 생겨도 팝업 크기가 바뀌지 않게 한다.
        JLabel problemLabel = new JLabel(wrapHtml(" ", textWidth, messageFont), SwingConstants.CENTER);
        problemLabel.setFont(messageFont);
        problemLabel.setForeground(UiColors.PLAYER2);
        JPanel body = new JPanel(new BorderLayout(0, Math.round(8 * scale)));
        body.setOpaque(false);
        body.add(messageLabel, BorderLayout.CENTER);
        body.add(problemLabel, BorderLayout.SOUTH);
        card.add(body, BorderLayout.CENTER);

        // 취소 버튼은 카드 아래쪽에 붙여 둔다. 버튼 위에 여백을 두어 본문과 떨어뜨린다.
        JPanel cancelRow = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 0));
        cancelRow.setOpaque(false);
        cancelRow.setBorder(BorderFactory.createEmptyBorder(Math.round(10 * scale), 0, 0, 0));
        PillButton cancel = new PillButton("취소", false);
        float fontSize = Math.round(16 * scale);
        cancel.setFontSize(fontSize);
        cancel.setPreferredSize(new Dimension(Math.round(110 * scale), Math.round(44 * scale)));
        // 버튼이 포커스를 가져가면 키를 받을 수 없으므로 마우스로만 누른다.
        cancel.setFocusable(false);
        cancel.addActionListener(event -> dialog.dispose());
        cancelRow.add(cancel);
        card.add(cancelRow, BorderLayout.SOUTH);

        // Tab도 조작 키로 받도록 포커스 이동 키를 끈 카드가 키를 받는다.
        card.setFocusable(true);
        card.setFocusTraversalKeysEnabled(false);
        boolean[] openingHeld = {openingKey != KeyEvent.VK_UNDEFINED};
        card.addKeyListener(new KeyAdapter() {
            @Override public void keyPressed(KeyEvent event) {
                event.consume();
                int keyCode = event.getKeyCode();
                if (keyCode == KeyEvent.VK_ESCAPE) {
                    dialog.dispose();
                    return;
                }
                if (keyCode <= KeyEvent.VK_UNDEFINED || keyCode > 0xFFFF) return;
                if (openingHeld[0] && keyCode == openingKey) return;   // 팝업을 연 키의 자동 반복
                KeyPress press = new KeyPress(keyCode, event.getKeyLocation());
                String issue = problem.apply(press);
                if (issue != null) {
                    problemLabel.setText(wrapHtml(issue, textWidth, messageFont));
                    return;
                }
                result[0] = press;
                dialog.dispose();
            }

            @Override public void keyReleased(KeyEvent event) {
                event.consume();
            }
        });
        // 팝업을 연 키를 떼는 이벤트는 팝업이 포커스를 받기 전에 올 수도 있어서 창과 상관없이 지켜본다.
        KeyEventDispatcher openingReleased = event -> {
            if (event.getID() == KeyEvent.KEY_RELEASED && event.getKeyCode() == openingKey) openingHeld[0] = false;
            return false;
        };

        dialog.setContentPane(card);
        dialog.pack();
        dialog.setLocationRelativeTo(owner);
        SwingUtilities.invokeLater(card::requestFocusInWindow);
        KeyboardFocusManager focus = KeyboardFocusManager.getCurrentKeyboardFocusManager();
        focus.addKeyEventDispatcher(openingReleased);
        try {
            dialog.setVisible(true);
        } finally {
            focus.removeKeyEventDispatcher(openingReleased);
        }
        return result[0];
    }

    /** 1인 창 L(600×800)을 1.0으로 보는 배율. 폭과 높이 중 작은 쪽을 따라, 넓은 대전 창에서 팝업만 커지지 않게 한다. */
    static float dialogScale(Window owner) {
        if (owner == null || owner.getWidth() <= 0 || owner.getHeight() <= 0) return 1f;
        float byWidth = UiPainter.scale(owner.getWidth());
        float byHeight = Math.max(0.5f, owner.getHeight() / 800f);
        return Math.min(byWidth, byHeight);
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
                        button.doClick(PressFeedback.PRESS_MS);
                        return;
                    }
                }
                if (!buttons.isEmpty()) buttons.get(0).doClick(PressFeedback.PRESS_MS);
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
    // 본문 서체(Jua)에 없는 글자는 시스템 서체 span으로 감싸 네모로 보이지 않게 한다.
    private static String wrapHtml(String message, int width, Font font) {
        String fallbackFamily = UiFonts.system(font.getSize2D()).getFamily();
        StringBuilder html = new StringBuilder("<html><div style='text-align:center;width:" + width + "px'>");
        int index = 0;
        while (index < message.length()) {
            int codePoint = message.codePointAt(index);
            index += Character.charCount(codePoint);
            if (codePoint == '\n') {
                html.append("<br>");
                continue;
            }
            String text = Character.toString(codePoint)
                    .replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
            if (font.canDisplay(codePoint)) {
                html.append(text);
            } else {
                html.append("<span style='font-family:").append(fallbackFamily).append("'>")
                        .append(text).append("</span>");
            }
        }
        return html.append("</div></html>").toString();
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

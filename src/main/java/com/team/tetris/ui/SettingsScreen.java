package com.team.tetris.ui;

import com.team.tetris.common.Screen;
import com.team.tetris.common.Difficulty;
import com.team.tetris.game.GameAction;
import com.team.tetris.scoreboard.ScoreboardRepository;
import com.team.tetris.settings.GameSettings;
import com.team.tetris.settings.KeyBindings;
import com.team.tetris.settings.SettingsRepository;
import com.team.tetris.ui.theme.PillButton;
import com.team.tetris.ui.theme.UiColors;
import com.team.tetris.ui.theme.UiFonts;
import com.team.tetris.ui.theme.UiPainter;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.KeyStroke;
import javax.swing.SwingConstants;
import javax.swing.SwingWorker;
import javax.swing.plaf.basic.BasicScrollBarUI;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.KeyboardFocusManager;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.beans.PropertyChangeListener;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * User preferences, including editable game keys and independent score reset.
 * 선택한 값은 저장 버튼을 누를 때 한꺼번에 적용한다.
 */
public final class SettingsScreen extends JPanel implements Screen {
    private final ScreenRouter router;
    private final SettingsRepository settingsRepository;
    private final ScoreboardRepository scoreboardRepository;
    private final Supplier<GameSettings> currentSettings;
    private final Consumer<GameSettings> settingsUpdated;
    private final Runnable scoresReset;
    private final SegmentedControl<GameSettings.ScreenSize> size = new SegmentedControl<>(
            GameSettings.ScreenSize.values(), value -> switch (value) {
                case SMALL -> "S";
                case MEDIUM -> "M";
                case LARGE -> "L";
            });
    private final SegmentedControl<GameSettings.ColorBlindMode> color = new SegmentedControl<>(
            GameSettings.ColorBlindMode.values(), value -> switch (value) {
                case OFF -> "끔";
                case RED_GREEN -> "적록";
                case BLUE_YELLOW -> "청황";
            });
    private final SegmentedControl<Difficulty> difficulty =
            new SegmentedControl<>(Difficulty.values(), Difficulty::name);
    private final EnumMap<GameAction, KeyChip> keyFields = new EnumMap<>(GameAction.class);
    private final EnumMap<GameAction, Integer> selectedKeys = new EnumMap<>(GameAction.class);
    // 배율이 바뀔 때 글자 크기를 다시 맞출 라벨·버튼 목록.
    private final List<JLabel> rowLabels = new ArrayList<>();
    private final List<JLabel> keyLabels = new ArrayList<>();
    private final List<PillButton> buttons = new ArrayList<>();
    private final List<SettingCard> cards = new ArrayList<>();
    private final JLabel title = new JLabel("설정");
    private final JLabel keySection = new JLabel("조작 키  ·  칸을 누른 뒤 새 키를 입력하세요");
    private final JLabel hint = new JLabel("←→ 값 변경  ·  Tab 다음 항목  ·  저장을 눌러야 적용됩니다  ·  Esc 메뉴로",
            SwingConstants.CENTER);
    private final JPanel form = new JPanel();
    private final PropertyChangeListener focusRepaint = event -> repaint();
    private float appliedScale = -1f;

    public SettingsScreen(ScreenRouter router, SettingsRepository settingsRepository,
                          ScoreboardRepository scoreboardRepository, Supplier<GameSettings> currentSettings,
                          Consumer<GameSettings> settingsUpdated, Runnable scoresReset) {
        this.router = Objects.requireNonNull(router, "router");
        this.settingsRepository = Objects.requireNonNull(settingsRepository, "settingsRepository");
        this.scoreboardRepository = Objects.requireNonNull(scoreboardRepository, "scoreboardRepository");
        this.currentSettings = Objects.requireNonNull(currentSettings, "currentSettings");
        this.settingsUpdated = Objects.requireNonNull(settingsUpdated, "settingsUpdated");
        this.scoresReset = Objects.requireNonNull(scoresReset, "scoresReset");
        setLayout(new BorderLayout(0, 14));
        setBackground(UiColors.BACKGROUND);
        title.setForeground(UiColors.TEXT_PRIMARY);
        add(title, BorderLayout.NORTH);

        form.setLayout(new BoxLayout(form, BoxLayout.Y_AXIS));
        form.setOpaque(false);
        addSettingRow("화면 크기", size);
        addSettingRow("난이도", difficulty);
        addSettingRow("색맹 모드", color);

        keySection.setForeground(UiColors.TEXT_MUTED);
        keySection.setAlignmentX(Component.LEFT_ALIGNMENT);
        form.add(Box.createVerticalStrut(6));
        form.add(keySection);
        form.add(Box.createVerticalStrut(8));
        JPanel keyGrid = new JPanel(new GridLayout(0, 2, 10, 10)) {
            // BoxLayout이 남는 세로 공간까지 늘리지 않게 내용 높이로 묶는다.
            @Override public Dimension getMaximumSize() {
                return new Dimension(Integer.MAX_VALUE, getPreferredSize().height);
            }
        };
        keyGrid.setOpaque(false);
        keyGrid.setAlignmentX(Component.LEFT_ALIGNMENT);
        for (GameAction action : GameAction.values()) {
            KeyChip field = new KeyChip();
            field.addKeyListener(new KeyAdapter() {
                @Override public void keyPressed(KeyEvent event) {
                    int keyCode = event.getKeyCode();
                    // Tab은 다음 항목으로 넘어가는 데 쓰므로 조작 키로 받지 않는다.
                    if (keyCode <= KeyEvent.VK_UNDEFINED || keyCode > 0xFFFF || keyCode == KeyEvent.VK_TAB) return;
                    for (var entry : selectedKeys.entrySet()) {
                        if (entry.getKey() != action && entry.getValue() == keyCode) {
                            showMessage("이미 다른 동작에 쓰이는 키입니다.", "키 중복",
                                    JOptionPane.WARNING_MESSAGE);
                            // 충돌한 키(예: ESC)가 설정 화면의 단축키로 넘어가 화면을 닫지 않게 한다.
                            event.consume();
                            return;
                        }
                    }
                    selectedKeys.put(action, keyCode);
                    field.setKey(keyCode);
                    event.consume();
                }
            });
            keyFields.put(action, field);
            JLabel label = new CardLabel(actionLabel(action));
            label.setForeground(UiColors.TEXT_PRIMARY);
            keyLabels.add(label);
            SettingCard card = new SettingCard(label, field);
            cards.add(card);
            keyGrid.add(card);
        }
        form.add(keyGrid);

        JScrollPane formScroll = new JScrollPane(form);
        formScroll.setBorder(BorderFactory.createEmptyBorder());
        formScroll.setOpaque(false);
        formScroll.getViewport().setOpaque(false);
        formScroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        formScroll.getVerticalScrollBar().setUI(new ThinScrollBarUI());
        formScroll.getVerticalScrollBar().setOpaque(false);
        formScroll.getVerticalScrollBar().setPreferredSize(new Dimension(8, 0));
        formScroll.getVerticalScrollBar().setUnitIncrement(16);
        add(formScroll, BorderLayout.CENTER);

        JPanel footer = new JPanel(new BorderLayout(0, 10));
        footer.setOpaque(false);
        JPanel buttonRow = new JPanel(new FlowLayout(FlowLayout.CENTER, 8, 0));
        buttonRow.setOpaque(false);
        PillButton save = addButton(buttonRow, "저장", true);
        save.addActionListener(event -> saveSelected());
        addButton(buttonRow, "기본값 복원", false).addActionListener(event -> resetSettings());
        addButton(buttonRow, "점수 초기화", false).addActionListener(event -> resetScores());
        addButton(buttonRow, "뒤로", false).addActionListener(event -> router.showScreen(ScreenRouter.MAIN_MENU));
        hint.setForeground(UiColors.withAlpha(UiColors.TEXT_MUTED, 0.8f));
        footer.add(buttonRow, BorderLayout.NORTH);
        footer.add(hint, BorderLayout.SOUTH);
        add(footer, BorderLayout.SOUTH);

        getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke("ESCAPE"), "back");
        getActionMap().put("back", new javax.swing.AbstractAction() {
            @Override public void actionPerformed(java.awt.event.ActionEvent event) {
                router.showScreen(ScreenRouter.MAIN_MENU);
            }
        });
        applyScale(1f);
    }

    @Override public JPanel getPanel() { return this; }

    @Override public void onShow() {
        display(currentSettings.get());
        // 포커스가 옮겨 갈 때마다 선택 강조(노란 테두리)를 다시 그린다.
        KeyboardFocusManager.getCurrentKeyboardFocusManager()
                .addPropertyChangeListener("permanentFocusOwner", focusRepaint);
        size.requestFocusInWindow();
    }

    @Override public void onHide() {
        KeyboardFocusManager.getCurrentKeyboardFocusManager()
                .removePropertyChangeListener("permanentFocusOwner", focusRepaint);
    }

    @Override
    public void doLayout() {
        // 창 크기(S/M/L)가 바뀌면 글자·카드 크기를 창 폭 배율에 맞춘다.
        float scale = UiPainter.scale(Math.max(1, getWidth()));
        if (scale != appliedScale) applyScale(scale);
        super.doLayout();
    }

    private void applyScale(float scale) {
        appliedScale = scale;
        int margin = Math.round(40 * scale);
        setBorder(BorderFactory.createEmptyBorder(Math.round(36 * scale), margin, Math.round(22 * scale), margin));
        title.setFont(UiFonts.display(Math.round(30 * scale)));
        keySection.setFont(UiFonts.body(Math.round(13 * scale)));
        hint.setFont(UiFonts.body(Math.round(12 * scale)));
        for (JLabel label : rowLabels) label.setFont(UiFonts.display(Math.round(18 * scale)));
        for (JLabel label : keyLabels) label.setFont(UiFonts.display(Math.round(15 * scale)));
        for (SegmentedControl<?> control : List.of(size, difficulty, color)) control.applyScale(scale);
        keyFields.values().forEach(chip -> chip.applyScale(scale));
        for (SettingCard card : cards) card.applyScale(scale);
        for (PillButton button : buttons) {
            button.setFontSize(Math.round(15 * scale));
            FontMetrics metrics = button.getFontMetrics(UiFonts.display(Math.round(15 * scale)));
            button.setPreferredSize(new Dimension(metrics.stringWidth("> " + button.getText()) + Math.round(40 * scale),
                    Math.round(42 * scale)));
        }
    }

    private void display(GameSettings settings) {
        size.setSelected(settings.screenSize());
        color.setSelected(settings.colorBlindMode());
        difficulty.setSelected(settings.difficulty());
        selectedKeys.clear();
        selectedKeys.putAll(settings.keyBindings().keys());
        keyFields.forEach((action, field) -> field.setKey(selectedKeys.get(action)));
    }

    private void saveSelected() {
        try {
            save(new GameSettings(size.getSelected(), color.getSelected(), new KeyBindings(selectedKeys),
                    difficulty.getSelected()));
        } catch (IllegalArgumentException invalid) {
            showMessage("동작마다 서로 다른 키를 지정해 주세요.", "키 설정 오류",
                    JOptionPane.WARNING_MESSAGE);
        }
    }

    private void save(GameSettings settings) {
        new SwingWorker<Void, Void>() {
            @Override protected Void doInBackground() throws Exception {
                settingsRepository.save(settings);
                return null;
            }

            @Override protected void done() {
                try {
                    get();
                    display(settings);
                    settingsUpdated.accept(settings);
                    router.showScreen(ScreenRouter.MAIN_MENU);
                } catch (Exception failure) {
                    showMessage("설정을 저장하지 못했습니다. 다시 시도해 주세요.", "설정 오류",
                            JOptionPane.ERROR_MESSAGE);
                }
            }
        }.execute();
    }

    private void resetSettings() {
        new SwingWorker<GameSettings, Void>() {
            @Override protected GameSettings doInBackground() throws Exception {
                return settingsRepository.reset();
            }

            @Override protected void done() {
                try {
                    GameSettings defaults = get();
                    display(defaults);
                    settingsUpdated.accept(defaults);
                    router.showScreen(ScreenRouter.MAIN_MENU);
                } catch (Exception failure) {
                    JOptionPane.showMessageDialog(SettingsScreen.this,
                            "기본 설정으로 되돌리지 못했습니다. 다시 시도해 주세요.");
                }
            }
        }.execute();
    }

    private void resetScores() {
        if (JOptionPane.showConfirmDialog(this, "저장된 점수를 모두 삭제할까요?", "점수 초기화",
                JOptionPane.YES_NO_OPTION) != JOptionPane.YES_OPTION) return;
        new SwingWorker<Void, Void>() {
            @Override protected Void doInBackground() throws Exception {
                scoreboardRepository.reset();
                return null;
            }

            @Override protected void done() {
                try {
                    get();
                    scoresReset.run();
                    showMessage("점수를 초기화했습니다.", "점수 초기화", JOptionPane.INFORMATION_MESSAGE);
                } catch (Exception failure) {
                    showMessage("점수를 초기화하지 못했습니다. 다시 시도해 주세요.", "스코어보드 오류",
                            JOptionPane.ERROR_MESSAGE);
                }
            }
        }.execute();
    }

    private void addSettingRow(String labelText, JComponent control) {
        JLabel label = new CardLabel(labelText);
        label.setForeground(UiColors.TEXT_PRIMARY);
        rowLabels.add(label);
        SettingCard card = new SettingCard(label, control);
        card.setAlignmentX(Component.LEFT_ALIGNMENT);
        cards.add(card);
        form.add(card);
        form.add(Box.createVerticalStrut(10));
    }

    private PillButton addButton(JPanel row, String label, boolean primary) {
        PillButton button = new PillButton(label, primary);
        buttons.add(button);
        row.add(button);
        return button;
    }

    private static String actionLabel(GameAction action) {
        return switch (action) {
            case MOVE_LEFT -> "왼쪽 이동";
            case MOVE_RIGHT -> "오른쪽 이동";
            case SOFT_DROP -> "소프트 드롭";
            case ROTATE_CLOCKWISE -> "회전";
            case HARD_DROP -> "하드 드롭";
            case PAUSE -> "일시정지";
            case QUIT -> "나가기";
        };
    }

    private static String keyName(int keyCode) {
        return switch (keyCode) {
            case KeyEvent.VK_LEFT -> "←";
            case KeyEvent.VK_RIGHT -> "→";
            case KeyEvent.VK_UP -> "↑";
            case KeyEvent.VK_DOWN -> "↓";
            case KeyEvent.VK_ESCAPE -> "Esc";
            case KeyEvent.VK_SPACE -> "Space";
            default -> KeyEvent.getKeyText(keyCode);
        };
    }

    private void showMessage(String message, String title, int messageType) {
        JOptionPane.showMessageDialog(this, message, title, messageType);
    }

    /** 글자 높이 기준으로 세로 가운데에 그리는 라벨. JLabel 기본 정렬은 Jua의 큰 줄 간격 때문에 위로 뜬다. */
    private static final class CardLabel extends JLabel {
        CardLabel(String text) {
            super(text);
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2d = (Graphics2D) g.create();
            try {
                UiPainter.prepare(g2d);
                g2d.setFont(getFont());
                g2d.setColor(getForeground());
                g2d.drawString(getText(), 0, UiPainter.centeredBaseline(g2d.getFontMetrics(), 0, getHeight()));
            } finally {
                g2d.dispose();
            }
        }
    }

    /**
     * 설정 한 줄을 감싸는 둥근 카드. 안의 컨트롤에 포커스가 있으면 노란 테두리와 `>`로 선택을 표시한다.
     */
    private static final class SettingCard extends JPanel {
        private final JLabel label;
        private final String text;
        private final JComponent control;
        private int radius = 14;

        SettingCard(JLabel label, JComponent control) {
            super(new BorderLayout(12, 0));
            this.label = label;
            this.text = label.getText();
            this.control = control;
            setOpaque(false);
            add(label, BorderLayout.CENTER);
            add(control, BorderLayout.EAST);
            // 카드 아무 곳이나 눌러도 그 줄의 컨트롤을 고른다.
            addMouseListener(new MouseAdapter() {
                @Override public void mousePressed(MouseEvent event) {
                    control.requestFocusInWindow();
                }
            });
        }

        void applyScale(float scale) {
            radius = Math.round(14 * scale);
            int vertical = Math.round(10 * scale);
            int horizontal = Math.round(20 * scale);
            setBorder(BorderFactory.createEmptyBorder(vertical, horizontal, vertical, Math.round(14 * scale)));
        }

        @Override
        public Dimension getMaximumSize() {
            // BoxLayout 안에서 가로로만 늘어나고 세로는 내용 높이를 유지한다.
            return new Dimension(Integer.MAX_VALUE, getPreferredSize().height);
        }

        @Override
        protected void paintComponent(Graphics g) {
            boolean focused = control.isFocusOwner();
            label.setText(focused ? "> " + text : text);
            Graphics2D g2d = (Graphics2D) g.create();
            try {
                UiPainter.prepare(g2d);
                if (focused) {
                    UiPainter.drawPanel(g2d, 0, 0, getWidth(), getHeight(), radius,
                            UiColors.PANEL, UiColors.ACCENT, 2.5f);
                } else {
                    UiPainter.drawPanel(g2d, 0, 0, getWidth(), getHeight(), radius,
                            UiColors.PANEL, UiColors.PANEL_BORDER, 1f);
                }
            } finally {
                g2d.dispose();
            }
        }
    }

    /**
     * 값 몇 개 중 하나를 고르는 알약 버튼 묶음. 마우스 클릭이나 ←→ 키로 바꾼다.
     */
    private static final class SegmentedControl<T> extends JComponent {
        private final T[] values;
        private final Function<T, String> label;
        private int selected;
        private float fontSize = 15f;
        private int gap = 6;

        SegmentedControl(T[] values, Function<T, String> label) {
            this.values = values.clone();
            this.label = label;
            setFocusable(true);
            addMouseListener(new MouseAdapter() {
                @Override public void mousePressed(MouseEvent event) {
                    requestFocusInWindow();
                    int index = indexAt(event.getX());
                    if (index >= 0) setSelectedIndex(index);
                }
            });
            addKeyListener(new KeyAdapter() {
                @Override public void keyPressed(KeyEvent event) {
                    if (event.getKeyCode() == KeyEvent.VK_LEFT) {
                        setSelectedIndex(Math.max(0, selected - 1));
                        event.consume();
                    } else if (event.getKeyCode() == KeyEvent.VK_RIGHT) {
                        setSelectedIndex(Math.min(values.length - 1, selected + 1));
                        event.consume();
                    }
                }
            });
            addFocusListener(new FocusAdapter() {
                @Override public void focusGained(FocusEvent event) { repaint(); }
                @Override public void focusLost(FocusEvent event) { repaint(); }
            });
        }

        void applyScale(float scale) {
            fontSize = Math.round(15 * scale);
            gap = Math.round(6 * scale);
            revalidate();
        }

        T getSelected() {
            return values[selected];
        }

        void setSelected(T value) {
            for (int index = 0; index < values.length; index++) {
                if (values[index] == value) setSelectedIndex(index);
            }
        }

        private void setSelectedIndex(int index) {
            selected = index;
            repaint();
        }

        private int itemWidth(FontMetrics metrics, int index) {
            return Math.max(pillHeight(), metrics.stringWidth(label.apply(values[index]))
                    + Math.round(fontSize * 1.4f));
        }

        private int indexAt(int x) {
            FontMetrics metrics = getFontMetrics(UiFonts.display(fontSize));
            int left = 0;
            for (int index = 0; index < values.length; index++) {
                int width = itemWidth(metrics, index);
                if (x >= left && x < left + width) return index;
                left += width + gap;
            }
            return -1;
        }

        @Override
        public Dimension getPreferredSize() {
            FontMetrics metrics = getFontMetrics(UiFonts.display(fontSize));
            int width = gap * (values.length - 1);
            for (int index = 0; index < values.length; index++) width += itemWidth(metrics, index);
            // 선택 항목의 하단 음영이 잘리지 않게 여유를 둔다.
            return new Dimension(width + 4, pillHeight() + 4);
        }

        private int pillHeight() {
            return Math.round(fontSize * 1.9f);
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2d = (Graphics2D) g.create();
            try {
                UiPainter.prepare(g2d);
                g2d.setFont(UiFonts.display(fontSize));
                FontMetrics metrics = g2d.getFontMetrics();
                int height = pillHeight();
                // 카드가 더 높게 늘려도 알약은 자기 높이로 세로 가운데에 그린다.
                g2d.translate(0, Math.max(0, (getHeight() - 4 - height) / 2));
                int x = 2;
                for (int index = 0; index < values.length; index++) {
                    int width = itemWidth(metrics, index);
                    String text = label.apply(values[index]);
                    if (index == selected) {
                        g2d.setColor(UiColors.darker(UiColors.ACCENT, 0.18f));
                        g2d.fillRoundRect(x, 3, width, height, height, height);
                        g2d.setColor(UiColors.ACCENT);
                        g2d.fillRoundRect(x, 0, width, height, height, height);
                        g2d.setColor(UiColors.TEXT_ON_ACCENT);
                    } else {
                        UiPainter.drawPanel(g2d, x, 0, width, height, height / 2,
                                UiColors.PANEL, UiColors.PANEL_BORDER, 2f);
                        g2d.setColor(UiColors.TEXT_PRIMARY);
                    }
                    UiPainter.drawCentered(g2d, text, x + width / 2,
                            UiPainter.centeredBaseline(metrics, 0, height));
                    x += width + gap;
                }
            } finally {
                g2d.dispose();
            }
        }
    }

    /** 조작 키 한 개를 보여 주는 칩. 포커스를 받은 뒤 누른 키가 새 조작 키가 된다. */
    private static final class KeyChip extends JComponent {
        private String text = "";
        private float fontSize = 15f;

        KeyChip() {
            setFocusable(true);
            setToolTipText("누른 뒤 새 키를 입력하세요");
            addMouseListener(new MouseAdapter() {
                @Override public void mousePressed(MouseEvent event) {
                    requestFocusInWindow();
                }
            });
            addFocusListener(new FocusAdapter() {
                @Override public void focusGained(FocusEvent event) { repaint(); }
                @Override public void focusLost(FocusEvent event) { repaint(); }
            });
        }

        void setKey(int keyCode) {
            text = keyName(keyCode);
            revalidate();
            repaint();
        }

        void applyScale(float scale) {
            fontSize = Math.round(15 * scale);
            revalidate();
        }

        // 높이는 글자 크기로 고정한다. 화살표처럼 대체 서체로 그리는 글자는 ascent가 커서 그 기준이면 칩이 부푼다.
        private int chipHeight() {
            return Math.round(fontSize * 2.0f);
        }

        @Override
        public Dimension getPreferredSize() {
            FontMetrics metrics = getFontMetrics(UiFonts.monoOrFallback(text, fontSize));
            int height = chipHeight();
            return new Dimension(Math.max(height * 2, metrics.stringWidth(text) + height), height);
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2d = (Graphics2D) g.create();
            try {
                UiPainter.prepare(g2d);
                // 카드가 더 높게 늘려도 칩은 자기 높이로 세로 가운데에 그린다.
                int height = Math.min(getHeight(), chipHeight());
                int top = (getHeight() - height) / 2;
                UiPainter.drawPanel(g2d, 0, top, getWidth(), height, height / 2, UiColors.BOARD,
                        isFocusOwner() ? UiColors.ACCENT : UiColors.PANEL_BORDER, 2f);
                g2d.setFont(UiFonts.monoOrFallback(text, fontSize));
                g2d.setColor(isFocusOwner() ? UiColors.ACCENT : UiColors.CREAM);
                UiPainter.drawCentered(g2d, text, getWidth() / 2,
                        UiPainter.centeredBaseline(g2d.getFontMetrics(), top, height));
            } finally {
                g2d.dispose();
            }
        }
    }

    /** 어두운 배경에 맞춘 얇은 스크롤바. 작은 창에서 조작 키 목록이 넘칠 때만 보인다. */
    private static final class ThinScrollBarUI extends BasicScrollBarUI {
        @Override protected void configureScrollBarColors() {
            thumbColor = UiColors.PANEL_BORDER;
            trackColor = UiColors.BACKGROUND;
        }

        @Override protected JButton createDecreaseButton(int orientation) { return zeroButton(); }
        @Override protected JButton createIncreaseButton(int orientation) { return zeroButton(); }

        private static JButton zeroButton() {
            JButton button = new JButton();
            button.setPreferredSize(new Dimension(0, 0));
            return button;
        }
    }
}

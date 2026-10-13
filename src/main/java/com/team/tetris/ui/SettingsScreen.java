package com.team.tetris.ui;

import com.team.tetris.common.Screen;
import com.team.tetris.game.GameAction;
import com.team.tetris.scoreboard.ScoreboardRepository;
import com.team.tetris.settings.GameSettings;
import com.team.tetris.settings.KeyBindings;
import com.team.tetris.settings.SettingsRepository;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.DefaultListCellRenderer;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.SwingWorker;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.util.EnumMap;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

/** User preferences, including editable game keys and independent score reset. */
public final class SettingsScreen extends JPanel implements Screen {
    private static final Color BACKGROUND = Color.BLACK;
    private static final Color PANEL_BACKGROUND = new Color(18, 22, 30);
    private static final Color ACCENT = Color.CYAN;
    private static final Color MUTED = new Color(155, 165, 180);
    private static final Color CONTROL_BACKGROUND = new Color(30, 36, 46);
    private static final Font CONTROL_FONT = new Font(Font.MONOSPACED, Font.BOLD, 15);

    private final ScreenRouter router;
    private final SettingsRepository settingsRepository;
    private final ScoreboardRepository scoreboardRepository;
    private final Supplier<GameSettings> currentSettings;
    private final Consumer<GameSettings> settingsUpdated;
    private final Runnable scoresReset;
    private final JComboBox<GameSettings.ScreenSize> size =
            new JComboBox<>(GameSettings.ScreenSize.values());
    private final JComboBox<GameSettings.ColorBlindMode> color =
            new JComboBox<>(GameSettings.ColorBlindMode.values());
    private final EnumMap<GameAction, JTextField> keyFields = new EnumMap<>(GameAction.class);
    private final EnumMap<GameAction, Integer> selectedKeys = new EnumMap<>(GameAction.class);

    public SettingsScreen(ScreenRouter router, SettingsRepository settingsRepository,
                          ScoreboardRepository scoreboardRepository, Supplier<GameSettings> currentSettings,
                          Consumer<GameSettings> settingsUpdated, Runnable scoresReset) {
        this.router = Objects.requireNonNull(router, "router");
        this.settingsRepository = Objects.requireNonNull(settingsRepository, "settingsRepository");
        this.scoreboardRepository = Objects.requireNonNull(scoreboardRepository, "scoreboardRepository");
        this.currentSettings = Objects.requireNonNull(currentSettings, "currentSettings");
        this.settingsUpdated = Objects.requireNonNull(settingsUpdated, "settingsUpdated");
        this.scoresReset = Objects.requireNonNull(scoresReset, "scoresReset");
        setLayout(new BorderLayout(16, 16));
        setBackground(BACKGROUND);
        setBorder(BorderFactory.createEmptyBorder(22, 24, 18, 24));
        add(createHeader(), BorderLayout.NORTH);

        styleComboBox(size);
        size.setRenderer(new ValueRenderer<GameSettings.ScreenSize>(value -> switch (value) {
            case SMALL -> "Small  |  480 x 640";
            case MEDIUM -> "Medium |  600 x 800";
            case LARGE -> "Large  |  750 x 1000";
        }));
        styleComboBox(color);
        color.setRenderer(new ValueRenderer<GameSettings.ColorBlindMode>(value -> switch (value) {
            case OFF -> "Off";
            case RED_GREEN -> "Red / Green";
            case BLUE_YELLOW -> "Blue / Yellow";
        }));

        JPanel form = new JPanel(new GridBagLayout());
        form.setBackground(PANEL_BACKGROUND);
        form.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(45, 58, 72)),
                BorderFactory.createEmptyBorder(14, 18, 14, 18)));
        int row = 0;
        addSettingRow(form, "WINDOW SIZE", size, row++);
        addSettingRow(form, "COLOR MODE", color, row++);
        for (GameAction action : GameAction.values()) {
            JTextField field = new JTextField();
            field.setEditable(false);
            field.setFocusable(true);
            field.setToolTipText("Click and press a new key");
            field.setFont(CONTROL_FONT);
            field.setForeground(Color.WHITE);
            field.setCaretColor(ACCENT);
            field.setBackground(CONTROL_BACKGROUND);
            field.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(new Color(65, 78, 94)),
                    BorderFactory.createEmptyBorder(7, 10, 7, 10)));
            field.addKeyListener(new KeyAdapter() {
                @Override public void keyPressed(KeyEvent event) {
                    int keyCode = event.getKeyCode();
                    if (keyCode <= KeyEvent.VK_UNDEFINED || keyCode > 0xFFFF) return;
                    for (var entry : selectedKeys.entrySet()) {
                        if (entry.getKey() != action && entry.getValue() == keyCode) {
                            showMessage("That key is already in use.", "Key Conflict",
                                    JOptionPane.WARNING_MESSAGE);
                            return;
                        }
                    }
                    selectedKeys.put(action, keyCode);
                    field.setText(KeyEvent.getKeyText(keyCode));
                    event.consume();
                }
            });
            keyFields.put(action, field);
            addSettingRow(form, action.name().replace('_', ' '), field, row++);
        }
        JScrollPane formScroll = new JScrollPane(form);
        formScroll.setBorder(BorderFactory.createEmptyBorder());
        formScroll.setBackground(BACKGROUND);
        formScroll.getViewport().setBackground(BACKGROUND);
        add(formScroll, BorderLayout.CENTER);

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 8));
        buttons.setBackground(BACKGROUND);
        buttons.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, new Color(45, 58, 72)));
        JButton save = new JButton("Save");
        styleButton(save, true);
        save.addActionListener(event -> saveSelected());
        JButton defaults = new JButton("Restore Defaults");
        styleButton(defaults, false);
        defaults.addActionListener(event -> resetSettings());
        JButton clearScores = new JButton("Reset Scores");
        styleButton(clearScores, false);
        clearScores.addActionListener(event -> resetScores());
        JButton back = new JButton("Back");
        styleButton(back, false);
        back.addActionListener(event -> router.showScreen(ScreenRouter.MAIN_MENU));
        buttons.add(save);
        buttons.add(defaults);
        buttons.add(clearScores);
        buttons.add(back);
        add(buttons, BorderLayout.SOUTH);
        getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke("ESCAPE"), "back");
        getActionMap().put("back", new javax.swing.AbstractAction() {
            @Override public void actionPerformed(java.awt.event.ActionEvent event) {
                router.showScreen(ScreenRouter.MAIN_MENU);
            }
        });
    }

    @Override public JPanel getPanel() { return this; }

    @Override public void onShow() {
        display(currentSettings.get());
    }

    @Override public void onHide() { }

    private void display(GameSettings settings) {
        size.setSelectedItem(settings.screenSize());
        color.setSelectedItem(settings.colorBlindMode());
        selectedKeys.clear();
        selectedKeys.putAll(settings.keyBindings().keys());
        keyFields.forEach((action, field) -> field.setText(KeyEvent.getKeyText(selectedKeys.get(action))));
    }

    private void saveSelected() {
        try {
            save(new GameSettings((GameSettings.ScreenSize) size.getSelectedItem(),
                    (GameSettings.ColorBlindMode) color.getSelectedItem(), new KeyBindings(selectedKeys)));
        } catch (IllegalArgumentException invalid) {
            showMessage("Please assign a different key to every action.", "Invalid Key Bindings",
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
                    showMessage("Could not save settings. Please retry.", "Settings Error",
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
                            "Could not restore default settings. Please retry.");
                }
            }
        }.execute();
    }

    private void resetScores() {
        if (JOptionPane.showConfirmDialog(this, "Delete all saved scores?", "Reset scores",
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
                    showMessage("Scores reset.", "Scores Reset", JOptionPane.INFORMATION_MESSAGE);
                } catch (Exception failure) {
                    showMessage("Could not reset scores. Please retry.", "Scoreboard Error",
                            JOptionPane.ERROR_MESSAGE);
                }
            }
        }.execute();
    }

    private JPanel createHeader() {
        JPanel header = new JPanel(new BorderLayout(0, 4));
        header.setBackground(BACKGROUND);
        JLabel title = new JLabel("SETTINGS", JLabel.CENTER);
        title.setForeground(Color.YELLOW);
        title.setFont(new Font(Font.MONOSPACED, Font.BOLD, 28));
        JLabel subtitle = new JLabel("CUSTOMIZE YOUR GAME", JLabel.CENTER);
        subtitle.setForeground(MUTED);
        subtitle.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        header.add(title, BorderLayout.CENTER);
        header.add(subtitle, BorderLayout.SOUTH);
        return header;
    }

    private void addSettingRow(JPanel form, String labelText, JComponent control, int row) {
        GridBagConstraints labelConstraints = new GridBagConstraints();
        labelConstraints.gridx = 0;
        labelConstraints.gridy = row;
        labelConstraints.weightx = 0;
        labelConstraints.fill = GridBagConstraints.HORIZONTAL;
        labelConstraints.anchor = GridBagConstraints.WEST;
        labelConstraints.insets = new Insets(6, 4, 6, 18);
        JLabel label = new JLabel(labelText);
        label.setForeground(ACCENT);
        label.setFont(new Font(Font.MONOSPACED, Font.BOLD, 12));
        form.add(label, labelConstraints);

        GridBagConstraints controlConstraints = new GridBagConstraints();
        controlConstraints.gridx = 1;
        controlConstraints.gridy = row;
        controlConstraints.weightx = 1;
        controlConstraints.fill = GridBagConstraints.HORIZONTAL;
        controlConstraints.insets = new Insets(5, 0, 5, 0);
        form.add(control, controlConstraints);
    }

    private void styleComboBox(JComboBox<?> comboBox) {
        comboBox.setFont(CONTROL_FONT);
        comboBox.setForeground(Color.WHITE);
        comboBox.setBackground(CONTROL_BACKGROUND);
        comboBox.setFocusable(true);
    }

    private void styleButton(JButton button, boolean primary) {
        button.setFont(new Font(Font.MONOSPACED, Font.BOLD, 12));
        button.setForeground(primary ? Color.BLACK : ACCENT);
        button.setBackground(primary ? Color.YELLOW : CONTROL_BACKGROUND);
        button.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(primary ? Color.YELLOW : new Color(65, 78, 94)),
                BorderFactory.createEmptyBorder(8, 14, 8, 14)));
        button.setFocusPainted(false);
        button.setOpaque(true);
    }

    private void showMessage(String message, String title, int messageType) {
        JOptionPane.showMessageDialog(this, message, title, messageType);
    }

    private static final class ValueRenderer<T> extends DefaultListCellRenderer {
        private final Function<T, String> text;

        private ValueRenderer(Function<T, String> text) {
            this.text = text;
            setFont(CONTROL_FONT);
            setBorder(BorderFactory.createEmptyBorder(5, 8, 5, 8));
        }

        @Override
        @SuppressWarnings("unchecked")
        public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                                                      boolean isSelected, boolean cellHasFocus) {
            super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
            setText(value == null ? "" : text.apply((T) value));
            setForeground(isSelected ? Color.BLACK : Color.WHITE);
            setBackground(isSelected ? Color.CYAN : CONTROL_BACKGROUND);
            return this;
        }
    }
}

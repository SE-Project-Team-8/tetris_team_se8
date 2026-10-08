package com.team.tetris.ui;

import com.team.tetris.common.Screen;
import com.team.tetris.game.GameAction;
import com.team.tetris.scoreboard.ScoreboardRepository;
import com.team.tetris.settings.GameSettings;
import com.team.tetris.settings.KeyBindings;
import com.team.tetris.settings.SettingsRepository;

import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.SwingWorker;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.GridLayout;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.util.EnumMap;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** User preferences, including editable game keys and independent score reset. */
public final class SettingsScreen extends JPanel implements Screen {
    private final ScreenRouter router;
    private final SettingsRepository settingsRepository;
    private final ScoreboardRepository scoreboardRepository;
    private final Supplier<GameSettings> currentSettings;
    private final Consumer<GameSettings> settingsUpdated;
    private final JComboBox<GameSettings.ScreenSize> size =
            new JComboBox<>(GameSettings.ScreenSize.values());
    private final JComboBox<GameSettings.ColorBlindMode> color =
            new JComboBox<>(GameSettings.ColorBlindMode.values());
    private final EnumMap<GameAction, JTextField> keyFields = new EnumMap<>(GameAction.class);
    private final EnumMap<GameAction, Integer> selectedKeys = new EnumMap<>(GameAction.class);

    public SettingsScreen(ScreenRouter router, SettingsRepository settingsRepository,
                          ScoreboardRepository scoreboardRepository, Supplier<GameSettings> currentSettings,
                          Consumer<GameSettings> settingsUpdated) {
        this.router = Objects.requireNonNull(router, "router");
        this.settingsRepository = Objects.requireNonNull(settingsRepository, "settingsRepository");
        this.scoreboardRepository = Objects.requireNonNull(scoreboardRepository, "scoreboardRepository");
        this.currentSettings = Objects.requireNonNull(currentSettings, "currentSettings");
        this.settingsUpdated = Objects.requireNonNull(settingsUpdated, "settingsUpdated");
        setLayout(new BorderLayout(12, 12));
        setBackground(Color.BLACK);
        JPanel form = new JPanel(new GridLayout(0, 2, 8, 8));
        form.add(new JLabel("Window size"));
        form.add(size);
        form.add(new JLabel("Color mode"));
        form.add(color);
        for (GameAction action : GameAction.values()) {
            JTextField field = new JTextField();
            field.setEditable(false);
            field.setFocusable(true);
            field.setToolTipText("Click and press a new key");
            field.addKeyListener(new KeyAdapter() {
                @Override public void keyPressed(KeyEvent event) {
                    int keyCode = event.getKeyCode();
                    if (keyCode <= KeyEvent.VK_UNDEFINED || keyCode > 0xFFFF) return;
                    for (var entry : selectedKeys.entrySet()) {
                        if (entry.getKey() != action && entry.getValue() == keyCode) {
                            JOptionPane.showMessageDialog(SettingsScreen.this, "That key is already in use.");
                            return;
                        }
                    }
                    selectedKeys.put(action, keyCode);
                    field.setText(KeyEvent.getKeyText(keyCode));
                    event.consume();
                }
            });
            keyFields.put(action, field);
            form.add(new JLabel(action.name().replace('_', ' ')));
            form.add(field);
        }
        add(new JScrollPane(form), BorderLayout.CENTER);
        JPanel buttons = new JPanel();
        JButton save = new JButton("Save");
        save.addActionListener(event -> saveSelected());
        JButton defaults = new JButton("Restore Defaults");
        defaults.addActionListener(event -> resetSettings());
        JButton clearScores = new JButton("Reset Scores");
        clearScores.addActionListener(event -> resetScores());
        JButton back = new JButton("Back");
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
            JOptionPane.showMessageDialog(this, "Please assign a different key to every action.");
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
                    JOptionPane.showMessageDialog(SettingsScreen.this, "Could not save settings. Please retry.");
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
                    JOptionPane.showMessageDialog(SettingsScreen.this, "Scores reset.");
                } catch (Exception failure) {
                    JOptionPane.showMessageDialog(SettingsScreen.this, "Could not reset scores. Please retry.");
                }
            }
        }.execute();
    }
}

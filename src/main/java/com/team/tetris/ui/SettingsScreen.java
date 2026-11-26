package com.team.tetris.ui;

import com.team.tetris.common.Screen;
import com.team.tetris.common.Difficulty;
import com.team.tetris.game.GameAction;
import com.team.tetris.scoreboard.ScoreboardRepository;
import com.team.tetris.settings.GameSettings;
import com.team.tetris.settings.KeyBindings;
import com.team.tetris.settings.VersusKeyBindings;
import com.team.tetris.common.battle.Player;
import com.team.tetris.game.versus.SidedKey;
import com.team.tetris.settings.SettingsRepository;
import com.team.tetris.ui.theme.PillButton;
import com.team.tetris.ui.theme.ThemedDialog;
import com.team.tetris.ui.theme.ThemedLabel;
import com.team.tetris.ui.theme.UiColors;
import com.team.tetris.ui.theme.UiFonts;
import com.team.tetris.ui.theme.UiPainter;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.KeyStroke;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
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
import java.awt.Rectangle;
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
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.IntConsumer;
import java.util.function.Supplier;

/**
 * User preferences, including editable game keys and independent score reset.
 * 선택한 값은 저장 버튼을 누를 때 한꺼번에 적용한다.
 */
public final class SettingsScreen extends JPanel implements Screen {
    /**
     * 조작 키 칸을 늘어놓는 순서 (두 열 격자, 왼쪽→오른쪽·위→아래).
     * 이동 / 드롭 / 회전 / 일시정지·나가기를 한 줄씩 짝지어 둔다.
     */
    private static final List<GameAction> KEY_ORDER = List.of(
            GameAction.MOVE_LEFT, GameAction.MOVE_RIGHT,
            GameAction.SOFT_DROP, GameAction.HARD_DROP,
            GameAction.ROTATE_CLOCKWISE, GameAction.ROTATE_COUNTERCLOCKWISE,
            GameAction.PAUSE, GameAction.QUIT);

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
    /** 조작 키 영역에 보여 줄 대상 (인계서 7단계). 대전 P1·P2는 자기 키 다섯 개만 보인다. */
    private enum KeyTarget { SINGLE, P1, P2 }
    private final SegmentedControl<KeyTarget> keyTarget = new SegmentedControl<>(
            KeyTarget.values(), value -> switch (value) {
                case SINGLE -> "1인용";
                case P1 -> "대전 P1";
                case P2 -> "대전 P2";
            });
    /** 대전 키 칸을 늘어놓는 순서 (두 열 격자): 이동 / 드롭 / 회전. 1인용 칸과 같은 자리다. */
    private static final List<GameAction> VERSUS_KEY_ORDER = List.of(
            GameAction.MOVE_LEFT, GameAction.MOVE_RIGHT,
            GameAction.SOFT_DROP, GameAction.HARD_DROP,
            GameAction.ROTATE_CLOCKWISE, GameAction.ROTATE_COUNTERCLOCKWISE);
    private final EnumMap<GameAction, KeyChip> versusFields = new EnumMap<>(GameAction.class);
    /** 플레이어별로 고른 대전 키 ({@link SidedKey} 값). 저장하기 전까지 화면에만 있다. */
    private final EnumMap<Player, EnumMap<GameAction, Integer>> selectedVersus = new EnumMap<>(Player.class);
    private JPanel singleGrid;
    private KeyHeader keyHeader;
    private JPanel versusGrid;
    // 배율이 바뀔 때 글자 크기를 다시 맞출 라벨·버튼 목록.
    private final List<JLabel> rowLabels = new ArrayList<>();
    private final List<JLabel> keyLabels = new ArrayList<>();
    private final List<PillButton> buttons = new ArrayList<>();
    private final List<SettingCard> cards = new ArrayList<>();
    private final JLabel title = new JLabel("설정");
    private final JLabel keySection = new ThemedLabel("조작 키  ·  칸에서 Enter를 눌러 키를 바꾸세요");
    private static final String SAVE_NOTICE = "변경한 내용은 저장을 눌러야 적용됩니다  ·  Esc 나가기";
    private static final String PAUSED_NOTICE = "저장을 눌러야 적용되며, 난이도는 다음 게임부터 바뀝니다";
    private final JLabel notice = new ThemedLabel(SAVE_NOTICE, SwingConstants.CENTER);
    /** 뒤로·저장·Esc로 돌아갈 화면. 일시정지 화면에서 열었으면 그리로 돌아가 게임을 이어 한다. */
    private String returnScreen = ScreenRouter.MAIN_MENU;
    private final JPanel form = new JPanel();
    private final PropertyChangeListener focusRepaint = event -> repaint();
    private float appliedScale = -1f;

    /** 새 조작 키를 받는 팝업. 테스트는 팝업 대신 정해 둔 키를 돌려주는 것으로 바꾼다. */
    interface KeyPrompt {
        ThemedDialog.KeyPress ask(Component parent, String title, String message, int openingKey,
                                  Function<ThemedDialog.KeyPress, String> problem);
    }

    private static KeyPrompt keyPrompt = ThemedDialog::captureKey;

    static void setKeyPromptForTests(KeyPrompt prompt) {
        keyPrompt = prompt == null ? ThemedDialog::captureKey : prompt;
    }

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
        keyTarget.setOnChange(this::showKeyTarget);
        keyTarget.highlightPillsOnFocus();

        // 조작 키 대상 선택(1인용 / 대전 P1 / 대전 P2)은 카드 한 줄을 더 쓰지 않고 안내 줄 오른쪽에 둔다.
        // 카드를 따로 두면 S 창에서 1인용 키 목록까지 스크롤해야 한다.
        keySection.setForeground(UiColors.TEXT_MUTED);
        keyHeader = new KeyHeader(keySection, keyTarget);
        keyHeader.setAlignmentX(Component.LEFT_ALIGNMENT);
        form.add(keyHeader);
        form.add(Box.createVerticalStrut(8));
        JPanel keyGrid = newKeyGrid();
        singleGrid = keyGrid;
        for (GameAction action : KEY_ORDER) {
            KeyChip field = new KeyChip();
            field.setOnActivate(openingKey -> changeSingleKey(action, field, openingKey));
            keyFields.put(action, field);
            JLabel label = new CardLabel(actionLabel(action));
            label.setForeground(UiColors.TEXT_PRIMARY);
            keyLabels.add(label);
            SettingCard card = new SettingCard(label, field);
            cards.add(card);
            keyGrid.add(card);
        }
        form.add(keyGrid);
        versusGrid = newKeyGrid();
        for (GameAction action : VERSUS_KEY_ORDER) {
            KeyChip field = new KeyChip();
            field.setOnActivate(openingKey -> changeVersusKey(action, field, openingKey));
            versusFields.put(action, field);
            JLabel label = new CardLabel(actionLabel(action));
            label.setForeground(UiColors.TEXT_PRIMARY);
            keyLabels.add(label);
            SettingCard card = new SettingCard(label, field);
            cards.add(card);
            versusGrid.add(card);
        }
        versusGrid.setVisible(false);
        form.add(versusGrid);

        JScrollPane formScroll = new JScrollPane(form);
        formScroll.setBorder(BorderFactory.createEmptyBorder());
        formScroll.setOpaque(false);
        formScroll.getViewport().setOpaque(false);
        formScroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        formScroll.getVerticalScrollBar().setUI(new ThinScrollBarUI());
        formScroll.getVerticalScrollBar().setOpaque(false);
        formScroll.getVerticalScrollBar().setPreferredSize(new Dimension(8, 0));
        formScroll.getVerticalScrollBar().setUnitIncrement(16);
        // 스크롤 영역은 방향키를 스크롤에 쓰므로 그 바인딩을 끈다. 방향키는 항목 이동에 쓴다 (스크롤은 이동할 때 맞춘다).
        formScroll.setInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT, new javax.swing.InputMap());
        add(formScroll, BorderLayout.CENTER);

        JPanel footer = new JPanel(new BorderLayout(0, 8));
        footer.setOpaque(false);
        JPanel buttonRow = new JPanel(new FlowLayout(FlowLayout.CENTER, 8, 0));
        buttonRow.setOpaque(false);
        addButton(buttonRow, "저장").addActionListener(event -> saveSelected());
        addButton(buttonRow, "기본값 복원").addActionListener(event -> resetSettings());
        addButton(buttonRow, "점수 초기화").addActionListener(event -> resetScores());
        addButton(buttonRow, "뒤로").addActionListener(event -> router.showScreen(returnScreen));
        PillButton.linkArrowKeys(buttons);
        // 저장 안내는 버튼 바로 아래에, 조작 방법은 맨 아래에 따로 둔다.
        // 버튼은 화면 아래쪽에 붙이고, 그 밑에 저장 안내 한 줄만 작게 둔다.
        notice.setForeground(UiColors.withAlpha(UiColors.TEXT_MUTED, 0.85f));
        footer.add(buttonRow, BorderLayout.CENTER);
        footer.add(notice, BorderLayout.SOUTH);
        add(footer, BorderLayout.SOUTH);

        // 방향키로 줄과 칸을 옮긴다. 각 항목이 직접 받는다.
        // 알약 줄(화면 크기·난이도·색맹 모드·조작 키 대상)의 ←→는 값 바꾸기, 아래 버튼 줄의 ←→는 버튼 이동이라 ↑↓만 건다.
        for (JComponent control : List.of(size, difficulty, color, keyTarget)) bindArrows(control, false);
        for (KeyChip chip : keyFields.values()) bindArrows(chip, true);
        for (KeyChip chip : versusFields.values()) bindArrows(chip, true);
        for (PillButton button : buttons) bindArrows(button, false);
        // 포커스가 어느 항목에도 없을 때(예: 클릭으로 빈 곳을 눌렀을 때) 방향키를 누르면 첫 줄부터 고른다.
        // 항목에 포커스가 있으면 그 항목의 방향키 처리가 먼저 키를 쓰므로 여기까지 오지 않는다.
        for (String key : List.of("UP", "DOWN", "LEFT", "RIGHT")) {
            getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(key), "settingsStart");
        }
        getActionMap().put("settingsStart", new javax.swing.AbstractAction() {
            @Override public void actionPerformed(java.awt.event.ActionEvent event) {
                focus(size);
            }
        });
        getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke("ESCAPE"), "back");
        getActionMap().put("back", new javax.swing.AbstractAction() {
            @Override public void actionPerformed(java.awt.event.ActionEvent event) {
                router.showScreen(returnScreen);
            }
        });
        applyScale(1f);
    }

    @Override public JPanel getPanel() { return this; }

    @Override public void onShow() {
        boolean fromPause = ScreenRouter.PAUSE.equals(router.getPreviousScreenName());
        returnScreen = fromPause ? ScreenRouter.PAUSE : ScreenRouter.MAIN_MENU;
        // 난이도는 게임을 시작할 때 정해지므로, 게임 중에 열었으면 다음 게임부터 바뀐다고 알린다.
        notice.setText(fromPause ? PAUSED_NOTICE : SAVE_NOTICE);
        display(currentSettings.get());
        // 포커스가 옮겨 갈 때마다 선택 강조(노란 테두리)를 다시 그린다.
        KeyboardFocusManager.getCurrentKeyboardFocusManager()
                .addPropertyChangeListener("permanentFocusOwner", focusRepaint);
        // 화면을 바꾸는 쪽(ScreenRouter)이 onShow 뒤에 패널에 포커스를 주므로, 그다음에 첫 줄(화면 크기)을 고른다.
        // 그래야 설정을 열자마자 방향키로 움직일 수 있다.
        SwingUtilities.invokeLater(() -> {
            if (isShowing()) size.requestFocusInWindow();
        });
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
        setBorder(BorderFactory.createEmptyBorder(Math.round(36 * scale), margin, Math.round(14 * scale), margin));
        title.setFont(UiFonts.display(Math.round(30 * scale)));
        keySection.setFont(UiFonts.body(Math.round(13 * scale)));
        notice.setFont(UiFonts.body(Math.round(12 * scale)));
        for (JLabel label : rowLabels) label.setFont(UiFonts.display(Math.round(18 * scale)));
        for (JLabel label : keyLabels) label.setFont(UiFonts.display(Math.round(15 * scale)));
        for (SegmentedControl<?> control : List.of(size, difficulty, color, keyTarget)) control.applyScale(scale);
        keyFields.values().forEach(chip -> chip.applyScale(scale));
        versusFields.values().forEach(chip -> chip.applyScale(scale));
        for (SettingCard card : cards) card.applyScale(scale);
        if (keyHeader != null) keyHeader.applyScale(scale);
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
        for (Player player : Player.values()) {
            selectedVersus.put(player, new EnumMap<>(settings.versusKeys().keys(player)));
        }
        showKeyTarget();
    }

    /** 고른 대상의 키 칸만 보여 준다. 대전 P1·P2는 같은 칸 다섯 개에 그 플레이어의 키를 채운다. */
    private void showKeyTarget() {
        KeyTarget target = keyTarget.getSelected();
        boolean versus = target != KeyTarget.SINGLE;
        singleGrid.setVisible(!versus);
        versusGrid.setVisible(versus);
        if (versus && !selectedVersus.isEmpty()) {
            Map<GameAction, Integer> keys = selectedVersus.get(playerOf(target));
            versusFields.forEach((action, field) -> field.setLabel(VersusScreen.versusKeyName(keys.get(action))));
        }
        form.revalidate();
        form.repaint();
    }

    private static Player playerOf(KeyTarget target) {
        return target == KeyTarget.P2 ? Player.P2 : Player.P1;
    }

    private static JPanel newKeyGrid() {
        JPanel grid = new JPanel(new GridLayout(0, 2, 10, 10)) {
            // BoxLayout이 남는 세로 공간까지 늘리지 않게 내용 높이로 묶는다.
            @Override public Dimension getMaximumSize() {
                return new Dimension(Integer.MAX_VALUE, getPreferredSize().height);
            }
        };
        grid.setOpaque(false);
        grid.setAlignmentX(Component.LEFT_ALIGNMENT);
        return grid;
    }

    /** 그 항목에 포커스가 있을 때 방향키로 다른 항목으로 옮기게 한다. */
    private void bindArrows(JComponent control, boolean sideways) {
        bindArrow(control, "UP", -1, 0);
        bindArrow(control, "DOWN", 1, 0);
        if (sideways) {
            bindArrow(control, "LEFT", 0, -1);
            bindArrow(control, "RIGHT", 0, 1);
        }
    }

    private void bindArrow(JComponent control, String key, int rowStep, int columnStep) {
        String name = "settingsMove" + key;
        control.getInputMap(JComponent.WHEN_FOCUSED).put(KeyStroke.getKeyStroke(key), name);
        control.getActionMap().put(name, new javax.swing.AbstractAction() {
            @Override public void actionPerformed(java.awt.event.ActionEvent event) {
                focus(navigationTarget(control, rowStep, columnStep));
            }
        });
    }

    /**
     * 방향키로 고를 수 있는 자리를 줄마다 모은다: 화면 크기, 난이도, 색맹 모드, 조작 키 대상, 키 칸(두 칸씩), 아래 버튼.
     * 키 칸은 지금 보이는 대상(1인용 / 대전)의 칸만 넣는다.
     */
    List<List<JComponent>> navigationRows() {
        List<List<JComponent>> rows = new ArrayList<>();
        for (JComponent control : List.of(size, difficulty, color, keyTarget)) rows.add(List.of(control));
        boolean versus = keyTarget.getSelected() != KeyTarget.SINGLE;
        List<GameAction> order = versus ? VERSUS_KEY_ORDER : KEY_ORDER;
        Map<GameAction, KeyChip> chips = versus ? versusFields : keyFields;
        for (int index = 0; index < order.size(); index += 2) {
            List<JComponent> row = new ArrayList<>();
            for (int column = index; column < Math.min(order.size(), index + 2); column++) {
                row.add(chips.get(order.get(column)));
            }
            rows.add(row);
        }
        rows.add(List.copyOf(buttons));
        return rows;
    }

    /**
     * 방향키를 눌렀을 때 옮겨 갈 자리. 줄을 옮기면 같은 열(없으면 그 줄의 마지막 칸)로 가고, 옆 칸이 없으면 그대로다.
     * 이동할 수 있는 자리 밖에 포커스가 있으면 첫 줄(화면 크기)로 간다.
     */
    JComponent navigationTarget(Component owner, int rowStep, int columnStep) {
        List<List<JComponent>> rows = navigationRows();
        for (int row = 0; row < rows.size(); row++) {
            int column = rows.get(row).indexOf(owner);
            if (column < 0) continue;
            int nextRow = Math.max(0, Math.min(rows.size() - 1, row + rowStep));
            int nextColumn = column + columnStep;
            if (rowStep != 0) nextColumn = Math.min(column, rows.get(nextRow).size() - 1);
            if (nextColumn < 0 || nextColumn >= rows.get(nextRow).size()) return rows.get(row).get(column);
            return rows.get(nextRow).get(nextColumn);
        }
        return size;
    }

    /** 방향키로 마지막에 옮겨 간 항목. 테스트가 창 포커스 없이 이동을 확인할 때 쓴다. */
    JComponent lastMoveTarget;

    private void focus(JComponent target) {
        lastMoveTarget = target;
        target.requestFocusInWindow();
        // 작은 창에서 스크롤 밖에 있는 칸으로 가면 그 카드가 보이게 스크롤한다.
        if (target.getParent() instanceof JComponent holder && SwingUtilities.isDescendingFrom(holder, form)) {
            holder.scrollRectToVisible(new Rectangle(0, 0, holder.getWidth(), holder.getHeight()));
        }
    }

    /** 1인용 키 칸: 팝업으로 새 키를 받는다. 다른 동작의 키와 겹치면 팝업 안에서 알리고 다시 받는다. */
    private void changeSingleKey(GameAction action, KeyChip field, int openingKey) {
        ThemedDialog.KeyPress press = keyPrompt.ask(this, "키 변경", promptMessage(actionLabel(action)), openingKey,
                pressed -> {
                    for (var entry : selectedKeys.entrySet()) {
                        if (entry.getKey() != action && entry.getValue() == pressed.keyCode()) {
                            return "이미 '" + actionLabel(entry.getKey()) + "'에 쓰이는 키입니다";
                        }
                    }
                    return null;
                });
        if (press == null) return;
        selectedKeys.put(action, press.keyCode());
        field.setKey(press.keyCode());
    }

    /**
     * 대전 키 칸: 키를 {@code SidedKey.of(코드, 위치)}로 받아 왼쪽·오른쪽 Shift를 구분해 저장한다.
     * 일시정지·나가기 키나 다른 대전 키(상대 포함)와 겹치면 팝업 안에서 알리고 다시 받는다.
     */
    private void changeVersusKey(GameAction action, KeyChip field, int openingKey) {
        Player player = playerOf(keyTarget.getSelected());
        ThemedDialog.KeyPress press = keyPrompt.ask(this, "키 변경",
                promptMessage(player.name() + " " + actionLabel(action)), openingKey,
                pressed -> versusKeyProblem(player, action, SidedKey.of(pressed.keyCode(), pressed.location())));
        if (press == null) return;
        int key = SidedKey.of(press.keyCode(), press.location());
        selectedVersus.get(player).put(action, key);
        field.setLabel(VersusScreen.versusKeyName(key));
    }

    /** 그 대전 키를 쓸 수 없는 이유. 쓸 수 있으면 null. */
    private String versusKeyProblem(Player player, GameAction action, int key) {
        for (GameAction shared : VersusKeyBindings.SHARED_ACTIONS) {
            if (SidedKey.overlaps(key, selectedKeys.get(shared))) return "일시정지·나가기 키와 겹치는 키입니다";
        }
        for (Player owner : Player.values()) {
            for (var entry : selectedVersus.get(owner).entrySet()) {
                if (owner == player && entry.getKey() == action) continue;
                if (SidedKey.overlaps(key, entry.getValue())) {
                    return "이미 '" + owner.name() + " " + actionLabel(entry.getKey()) + "'에 쓰이는 키입니다";
                }
            }
        }
        return null;
    }

    private static String promptMessage(String actionName) {
        return "'" + actionName + "'에 쓸 키를 누르세요";
    }

    private void saveSelected() {
        try {
            GameSettings settings = new GameSettings(size.getSelected(), color.getSelected(),
                    new KeyBindings(selectedKeys), difficulty.getSelected(),
                    new VersusKeyBindings(selectedVersus.get(Player.P1), selectedVersus.get(Player.P2)));
            // 1인용 일시정지·나가기 키를 대전 키와 같게 바꾸는 것은 막지 않고 알려만 준다 (인계서 7단계).
            if (!settings.versusKeyClashes().isEmpty()) {
                showMessage("일시정지·나가기 키가 대전 키와 겹칩니다.\n대전 중에는 그 키가 일시정지·나가기로만 쓰입니다.",
                        "키 겹침", ThemedDialog.Tone.WARNING);
            }
            save(settings);
        } catch (IllegalArgumentException invalid) {
            showMessage("동작마다 서로 다른 키를 지정해 주세요.", "키 설정 오류",
                    ThemedDialog.Tone.WARNING);
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
                    router.showScreen(returnScreen);
                } catch (Exception failure) {
                    showMessage("설정을 저장하지 못했습니다. 다시 시도해 주세요.", "설정 오류",
                            ThemedDialog.Tone.ERROR);
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
                    router.showScreen(returnScreen);
                } catch (Exception failure) {
                    showMessage("기본 설정으로 되돌리지 못했습니다. 다시 시도해 주세요.", "설정 오류",
                            ThemedDialog.Tone.ERROR);
                }
            }
        }.execute();
    }

    private void resetScores() {
        // 되돌릴 수 없는 작업이라 Enter를 바로 눌러도 지워지지 않게 처음 포커스를 "취소"에 둔다.
        if (ThemedDialog.showOptions(this, "점수 초기화", "저장된 점수를 모두 삭제할까요?\n되돌릴 수 없습니다.",
                ThemedDialog.Tone.WARNING, 1, "삭제", "취소") != 0) return;
        new SwingWorker<Void, Void>() {
            @Override protected Void doInBackground() throws Exception {
                scoreboardRepository.reset();
                return null;
            }

            @Override protected void done() {
                try {
                    get();
                    scoresReset.run();
                    showMessage("점수를 초기화했습니다.", "점수 초기화", ThemedDialog.Tone.INFO);
                } catch (Exception failure) {
                    showMessage("점수를 초기화하지 못했습니다. 다시 시도해 주세요.", "스코어보드 오류",
                            ThemedDialog.Tone.ERROR);
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

    // 노란 강조는 키보드 포커스가 있는 버튼 하나에만 보인다. 위쪽 설정 칸에 포커스가 있으면 모두 꺼진다.
    private PillButton addButton(JPanel row, String label) {
        PillButton button = new PillButton(label, false);
        button.enableFocusHighlight();
        buttons.add(button);
        row.add(button);
        return button;
    }

    private static String actionLabel(GameAction action) {
        return switch (action) {
            case MOVE_LEFT -> "왼쪽 이동";
            case MOVE_RIGHT -> "오른쪽 이동";
            case SOFT_DROP -> "소프트 드롭";
            case ROTATE_CLOCKWISE -> "오른쪽 회전";
            case ROTATE_COUNTERCLOCKWISE -> "왼쪽 회전";
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

    private void showMessage(String message, String title, ThemedDialog.Tone tone) {
        ThemedDialog.showMessage(this, title, message, tone);
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
     * 조작 키 안내 줄과 대상 선택(1인용 / 대전 P1 / 대전 P2). 바탕 없이 안내 글자만 보이고,
     * 대상 선택에 포커스가 있으면 방향키로 고른 알약에 노란 링이 생긴다 ({@link SegmentedControl#highlightPillsOnFocus()}).
     */
    private static final class KeyHeader extends JPanel {
        KeyHeader(JLabel label, JComponent control) {
            super(new BorderLayout(8, 0));
            setOpaque(false);
            add(label, BorderLayout.CENTER);
            add(control, BorderLayout.EAST);
            // 안내 줄 아무 곳이나 눌러도 대상 선택을 고른다.
            addMouseListener(new MouseAdapter() {
                @Override public void mousePressed(MouseEvent event) {
                    control.requestFocusInWindow();
                }
            });
        }

        void applyScale(float scale) {
            int vertical = Math.round(4 * scale);
            setBorder(BorderFactory.createEmptyBorder(vertical, 0, vertical, Math.round(2 * scale)));
        }

        @Override
        public Dimension getMaximumSize() {
            return new Dimension(Integer.MAX_VALUE, getPreferredSize().height);
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

        private Runnable onChange = () -> { };
        /** 카드 테두리로 포커스를 보여 줄 수 없는 자리(조작 키 대상 선택)에서는 고른 알약에 노란 링을 둘러 보여 준다. */
        private boolean highlightPillsOnFocus;

        void highlightPillsOnFocus() {
            highlightPillsOnFocus = true;
            revalidate();
        }

        private int ringPadding() {
            return highlightPillsOnFocus ? 5 : 2;
        }

        void setOnChange(Runnable onChange) {
            this.onChange = Objects.requireNonNull(onChange, "onChange");
        }

        private void setSelectedIndex(int index) {
            boolean changed = selected != index;
            selected = index;
            repaint();
            if (changed) onChange.run();
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
            // 포커스 링을 그리는 칸은 링이 잘리지 않게 사방 여백을 더 둔다.
            int pad = ringPadding();
            return new Dimension(width + pad * 2, pillHeight() + pad * 2);
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
                int pad = ringPadding();
                g2d.translate(0, Math.max(pad, (getHeight() - height) / 2 - 1));
                int x = pad;
                boolean ring = highlightPillsOnFocus && isFocusOwner();
                for (int index = 0; index < values.length; index++) {
                    int width = itemWidth(metrics, index);
                    String text = label.apply(values[index]);
                    if (index == selected) {
                        g2d.setColor(UiColors.darker(UiColors.ACCENT, 0.18f));
                        g2d.fillRoundRect(x, 3, width, height, height, height);
                        g2d.setColor(UiColors.ACCENT);
                        g2d.fillRoundRect(x, 0, width, height, height, height);
                        if (ring) {
                            // 방향키로 고른 알약 하나에만 틈을 두고 노란 링을 둘러 지금 위치를 보여 준다.
                            UiPainter.strokeRoundRect(g2d, x - 4, -4, width + 8, height + 8, (height + 8) / 2,
                                    UiColors.ACCENT, 2f);
                        }
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

    /** 조작 키 한 개를 보여 주는 칩. Enter·Space나 클릭으로 키 변경 팝업을 연다. */
    private static final class KeyChip extends JComponent {
        private String text = "";
        private float fontSize = 15f;
        private IntConsumer onActivate = openingKey -> { };

        KeyChip() {
            setFocusable(true);
            setToolTipText("Enter나 클릭으로 키를 바꿉니다");
            addMouseListener(new MouseAdapter() {
                @Override public void mousePressed(MouseEvent event) {
                    requestFocusInWindow();
                    onActivate.accept(KeyEvent.VK_UNDEFINED);
                }
            });
            addKeyListener(new KeyAdapter() {
                @Override public void keyPressed(KeyEvent event) {
                    int keyCode = event.getKeyCode();
                    if (keyCode != KeyEvent.VK_ENTER && keyCode != KeyEvent.VK_SPACE) return;
                    event.consume();
                    // 팝업을 닫은 뒤에도 Enter를 누르고 있으면 자동 반복으로 팝업이 다시 열리지 않게 한다.
                    if (HeldKeys.isAutoRepeat()) return;
                    onActivate.accept(keyCode);
                }
            });
            addFocusListener(new FocusAdapter() {
                @Override public void focusGained(FocusEvent event) { repaint(); }
                @Override public void focusLost(FocusEvent event) { repaint(); }
            });
        }

        void setOnActivate(IntConsumer onActivate) {
            this.onActivate = Objects.requireNonNull(onActivate, "onActivate");
        }

        void setLabel(String label) {
            text = Objects.requireNonNull(label, "label");
            revalidate();
            repaint();
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
            // 크기 0인 화살표 버튼이 Tab 순서에 끼어 "나가기" 칸과 저장 버튼 사이에 보이지 않는 칸 두 개가 생기던 문제를 막는다.
            button.setFocusable(false);
            return button;
        }
    }
}

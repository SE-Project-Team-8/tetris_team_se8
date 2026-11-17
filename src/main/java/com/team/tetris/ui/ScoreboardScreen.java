package com.team.tetris.ui;

import com.team.tetris.common.Difficulty;
import com.team.tetris.common.GameMode;
import com.team.tetris.common.Screen;
import com.team.tetris.scoreboard.ScoreRecord;
import com.team.tetris.scoreboard.ScoreboardRepository;

import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.KeyStroke;
import javax.swing.SwingConstants;
import javax.swing.SwingWorker;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.JTableHeader;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.GridLayout;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Persistent ranking view. Disk reads run off the event dispatch thread.
 * One mode and difficulty ranking is shown at a time; the arrow keys switch between them.
 */
public final class ScoreboardScreen extends JPanel implements Screen {
    private static final Color BACKGROUND = Color.BLACK;
    private static final Color PANEL_BACKGROUND = new Color(18, 22, 30);
    private static final Color ROW_ALTERNATE = new Color(23, 28, 38);
    private static final Color HIGHLIGHT_BACKGROUND = new Color(0, 65, 75);
    private static final Color TAB_BACKGROUND = new Color(30, 36, 46);
    private static final Color ACCENT = Color.CYAN;
    private static final Color MUTED = new Color(155, 165, 180);
    private static final int CELL_PADDING = 6;
    private static final Font BODY_FONT = new Font(Font.MONOSPACED, Font.BOLD, 15);
    private static final Font HEADER_FONT = new Font(Font.MONOSPACED, Font.BOLD, 15);
    private static final Font TAB_FONT = new Font(Font.MONOSPACED, Font.BOLD, 13);

    private final ScreenRouter router;
    private final ScoreboardRepository repository;
    private final ScoreTableModel tableModel = new ScoreTableModel();
    private final JTable results = new JTable(tableModel);
    private final CardLayout contentLayout = new CardLayout();
    private final JPanel content = new JPanel(contentLayout);
    private final JLabel status = new JLabel("", SwingConstants.CENTER);
    private final List<JLabel> modeTabs = new ArrayList<>();
    private final List<JLabel> difficultyTabs = new ArrayList<>();
    private GameMode selectedMode = GameMode.NORMAL;
    private Difficulty selectedDifficulty = Difficulty.NORMAL;
    private int loadGeneration;
    private UUID highlightedId;

    public ScoreboardScreen(ScreenRouter router, ScoreboardRepository repository) {
        this.router = Objects.requireNonNull(router, "router");
        this.repository = Objects.requireNonNull(repository, "repository");
        setLayout(new BorderLayout(16, 16));
        setBackground(BACKGROUND);
        setBorder(BorderFactory.createEmptyBorder(22, 24, 18, 24));
        add(createHeader(), BorderLayout.NORTH);

        configureTable();
        JScrollPane resultScroll = new JScrollPane(results);
        resultScroll.setBorder(BorderFactory.createLineBorder(new Color(45, 58, 72)));
        resultScroll.getViewport().setBackground(PANEL_BACKGROUND);
        content.setBackground(PANEL_BACKGROUND);
        content.add(resultScroll, "table");

        status.setForeground(MUTED);
        status.setFont(new Font(Font.MONOSPACED, Font.BOLD, 14));
        status.setOpaque(true);
        status.setBackground(PANEL_BACKGROUND);
        content.add(status, "status");
        add(content, BorderLayout.CENTER);

        JPanel footer = new JPanel();
        footer.setLayout(new BoxLayout(footer, BoxLayout.Y_AXIS));
        footer.setPreferredSize(new Dimension(0, 100));
        footer.setBackground(BACKGROUND);
        footer.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, new Color(45, 58, 72)));
        JPanel backRow = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 8));
        backRow.setBackground(BACKGROUND);
        JButton back = new JButton("Main Menu");
        back.setFont(new Font(Font.MONOSPACED, Font.BOLD, 12));
        back.setForeground(ACCENT);
        back.setBackground(TAB_BACKGROUND);
        back.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(65, 78, 94)),
                BorderFactory.createEmptyBorder(8, 18, 8, 18)));
        back.setFocusPainted(false);
        back.setOpaque(true);
        back.addActionListener(event -> router.showScreen(ScreenRouter.MAIN_MENU));
        backRow.add(back);
        JPanel keysRow = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 0));
        keysRow.setBackground(BACKGROUND);
        JLabel keys = new JLabel("←→ DIFFICULTY  ↑↓ MODE");
        keys.setForeground(MUTED);
        keys.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 11));
        keysRow.add(keys);
        footer.add(Box.createVerticalGlue());
        footer.add(backRow);
        footer.add(keysRow);
        footer.add(Box.createVerticalGlue());
        add(footer, BorderLayout.SOUTH);

        bindKey("ESCAPE", "back", () -> router.showScreen(ScreenRouter.MAIN_MENU));
        bindKey("LEFT", "previousDifficulty",
                () -> select(selectedMode, step(Difficulty.values(), selectedDifficulty, -1)));
        bindKey("RIGHT", "nextDifficulty",
                () -> select(selectedMode, step(Difficulty.values(), selectedDifficulty, 1)));
        bindKey("UP", "previousMode", () -> select(step(GameMode.values(), selectedMode, -1), selectedDifficulty));
        bindKey("DOWN", "nextMode", () -> select(step(GameMode.values(), selectedMode, 1), selectedDifficulty));
        updateTabs();
    }

    public void highlight(UUID recordId) {
        highlightedId = recordId;
        results.repaint();
    }

    /** 보여 줄 순위를 고른다. 게임이 끝난 뒤에는 방금 플레이한 모드·난이도를 고른다. */
    public void select(GameMode mode, Difficulty difficulty) {
        selectedMode = Objects.requireNonNull(mode, "mode");
        selectedDifficulty = Objects.requireNonNull(difficulty, "difficulty");
        updateTabs();
        if (isShowing()) loadScores();
    }

    @Override public JPanel getPanel() { return this; }

    @Override public void onShow() {
        loadScores();
    }

    @Override public void onHide() {
        // 화면을 떠난 뒤 끝난 로드는 반영하지 않는다.
        loadGeneration++;
        highlightedId = null;
    }

    private void loadScores() {
        int generation = ++loadGeneration;
        GameMode mode = selectedMode;
        Difficulty difficulty = selectedDifficulty;
        status.setText("LOADING SCORES...");
        contentLayout.show(content, "status");
        new SwingWorker<List<ScoreRecord>, Void>() {
            @Override protected List<ScoreRecord> doInBackground() throws Exception {
                return repository.load(difficulty, mode);
            }

            @Override protected void done() {
                // 순위를 빠르게 바꾸면 이전 순위의 로드가 늦게 끝날 수 있다. 마지막 요청만 반영한다.
                if (generation != loadGeneration) return;
                try {
                    List<ScoreRecord> records = get();
                    tableModel.setRecords(records);
                    if (records.isEmpty()) {
                        status.setText("NO SCORES YET");
                        contentLayout.show(content, "status");
                    } else {
                        contentLayout.show(content, "table");
                    }
                } catch (Exception failure) {
                    status.setText("COULD NOT LOAD SCORES. RETURN TO THE MENU AND TRY AGAIN.");
                    contentLayout.show(content, "status");
                }
            }
        }.execute();
    }

    private void bindKey(String key, String name, Runnable action) {
        getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(key), name);
        getActionMap().put(name, new AbstractAction() {
            @Override public void actionPerformed(java.awt.event.ActionEvent event) {
                action.run();
            }
        });
    }

    /** 끝에서 넘어가면 반대쪽 끝으로 돌아간다. */
    private static <T extends Enum<T>> T step(T[] values, T current, int direction) {
        return values[Math.floorMod(current.ordinal() + direction, values.length)];
    }

    private JPanel createHeader() {
        JPanel header = new JPanel(new BorderLayout(0, 4));
        header.setBackground(BACKGROUND);
        JLabel title = new JLabel("LEADERBOARD", JLabel.CENTER);
        title.setForeground(Color.YELLOW);
        title.setFont(new Font(Font.MONOSPACED, Font.BOLD, 28));
        header.add(title, BorderLayout.NORTH);

        // 두 줄을 왼쪽 정렬로 묶고 그 묶음을 가운데에 둬서 탭 시작 위치를 맞춘다.
        JPanel rows = new JPanel(new GridLayout(2, 1, 0, 2));
        rows.setBackground(BACKGROUND);
        rows.add(tabRow("MODE", GameMode.values(), GameMode::label,
                mode -> select(mode, selectedDifficulty), modeTabs));
        rows.add(tabRow("DIFFICULTY", Difficulty.values(), Difficulty::label,
                difficulty -> select(selectedMode, difficulty), difficultyTabs));
        JPanel selectors = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 0));
        selectors.setBackground(BACKGROUND);
        selectors.add(rows);
        header.add(selectors, BorderLayout.CENTER);
        return header;
    }

    /** 이름표와 값 탭 한 줄. 두 줄의 이름표 폭을 같게 해 탭 위치를 맞춘다. */
    private <T> JPanel tabRow(String name, T[] values, Function<T, String> label, Consumer<T> choose,
                              List<JLabel> tabs) {
        JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        row.setBackground(BACKGROUND);
        JLabel caption = new JLabel(name, SwingConstants.RIGHT);
        caption.setForeground(MUTED);
        caption.setFont(TAB_FONT);
        FontMetrics metrics = caption.getFontMetrics(TAB_FONT);
        caption.setPreferredSize(new Dimension(metrics.stringWidth("DIFFICULTY") + 8, metrics.getHeight() + 4));
        row.add(caption);
        for (T value : values) {
            JLabel tab = new JLabel(label.apply(value).toUpperCase(java.util.Locale.ROOT), SwingConstants.CENTER);
            tab.setFont(TAB_FONT);
            tab.setOpaque(true);
            tab.setBorder(BorderFactory.createEmptyBorder(2, 8, 2, 8));
            tab.addMouseListener(new MouseAdapter() {
                @Override public void mouseClicked(MouseEvent event) {
                    choose.accept(value);
                }
            });
            tabs.add(tab);
            row.add(tab);
        }
        return row;
    }

    private void updateTabs() {
        paintTabs(modeTabs, selectedMode.ordinal());
        paintTabs(difficultyTabs, selectedDifficulty.ordinal());
    }

    private static void paintTabs(List<JLabel> tabs, int selected) {
        for (int index = 0; index < tabs.size(); index++) {
            boolean active = index == selected;
            tabs.get(index).setBackground(active ? ACCENT : TAB_BACKGROUND);
            tabs.get(index).setForeground(active ? Color.BLACK : MUTED);
        }
    }

    private void configureTable() {
        results.setFillsViewportHeight(true);
        results.setRowHeight(36);
        results.setShowGrid(false);
        results.setIntercellSpacing(new Dimension(0, 0));
        results.setBackground(PANEL_BACKGROUND);
        results.setForeground(Color.WHITE);
        results.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 15));
        results.setSelectionBackground(PANEL_BACKGROUND);
        results.setSelectionForeground(Color.WHITE);
        results.setFocusable(false);
        results.setRowSelectionAllowed(false);
        results.setCellSelectionEnabled(false);
        results.setAutoCreateRowSorter(false);

        JTableHeader header = results.getTableHeader();
        header.setReorderingAllowed(false);
        header.setResizingAllowed(false);
        header.setPreferredSize(new Dimension(0, 42));
        header.setBackground(new Color(27, 33, 44));
        header.setForeground(ACCENT);
        header.setFont(HEADER_FONT);
        header.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, new Color(55, 70, 88)));

        for (int column = 0; column < tableModel.getColumnCount(); column++) {
            int alignment = alignmentOf(column);
            var tableColumn = results.getColumnModel().getColumn(column);
            tableColumn.setCellRenderer(new ScoreCellRenderer(alignment));
            tableColumn.setHeaderRenderer(new HeaderRenderer(alignment));
            if (column == ScoreTableModel.PLAYER) {
                // 이름 열이 남는 폭을 모두 받고, 좁은 창(480px)에서는 이 열만 줄어든다.
                tableColumn.setMinWidth(60);
                tableColumn.setPreferredWidth(400);
            } else {
                int width = fittedWidth(column);
                tableColumn.setMinWidth(width);
                tableColumn.setPreferredWidth(width);
                tableColumn.setMaxWidth(width + 40);
            }
        }
    }

    /** 머리글과 가장 긴 값이 잘리지 않는 폭. 좁은 창에서도 RANK·SCORE가 보이게 한다. */
    private int fittedWidth(int column) {
        String longest = column == ScoreTableModel.SCORE ? "999999" : "▶ 10";
        FontMetrics body = results.getFontMetrics(BODY_FONT);
        FontMetrics header = results.getFontMetrics(HEADER_FONT);
        int widest = Math.max(header.stringWidth(tableModel.getColumnName(column)), body.stringWidth(longest));
        return widest + CELL_PADDING * 2 + 2;
    }

    /** RANK는 가운데, PLAYER는 왼쪽, SCORE는 오른쪽 정렬이다. */
    private static int alignmentOf(int column) {
        return switch (column) {
            case ScoreTableModel.PLAYER -> SwingConstants.LEFT;
            case ScoreTableModel.SCORE -> SwingConstants.RIGHT;
            default -> SwingConstants.CENTER;
        };
    }

    private final class ScoreCellRenderer extends DefaultTableCellRenderer {
        private ScoreCellRenderer(int alignment) {
            setHorizontalAlignment(alignment);
            setFont(new Font(Font.MONOSPACED, Font.PLAIN, 15));
        }

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean selected,
                                                       boolean focused, int row, int column) {
            super.getTableCellRendererComponent(table, value, false, false, row, column);
            ScoreRecord record = tableModel.recordAt(row);
            boolean highlighted = record.id().equals(highlightedId);
            setBackground(highlighted ? HIGHLIGHT_BACKGROUND
                    : row % 2 == 0 ? PANEL_BACKGROUND : ROW_ALTERNATE);
            setForeground(highlighted ? ACCENT : column == 0 ? MUTED : Color.WHITE);
            setFont(new Font(Font.MONOSPACED, highlighted ? Font.BOLD : Font.PLAIN, 15));
            setHorizontalAlignment(alignmentOf(column));
            setBorder(BorderFactory.createEmptyBorder(0, CELL_PADDING, 0, CELL_PADDING));
            if (column == 0) {
                setText((highlighted ? "▶ " : "") + (row + 1));
            }
            return this;
        }
    }

    private static final class HeaderRenderer extends DefaultTableCellRenderer {
        private HeaderRenderer(int alignment) {
            setHorizontalAlignment(alignment);
            setForeground(ACCENT);
            setBackground(new Color(27, 33, 44));
            setFont(HEADER_FONT);
        }

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean selected,
                                                       boolean focused, int row, int column) {
            super.getTableCellRendererComponent(table, value, false, false, row, column);
            setForeground(ACCENT);
            setBackground(new Color(27, 33, 44));
            setFont(HEADER_FONT);
            setHorizontalAlignment(alignmentOf(column));
            setBorder(BorderFactory.createEmptyBorder(0, CELL_PADDING, 0, CELL_PADDING));
            return this;
        }
    }

    private static final class ScoreTableModel extends AbstractTableModel {
        private static final int PLAYER = 1;
        private static final int SCORE = 2;
        private static final String[] COLUMNS = {"RANK", "PLAYER", "SCORE"};
        private List<ScoreRecord> records = List.of();

        private void setRecords(List<ScoreRecord> records) {
            this.records = List.copyOf(records);
            fireTableDataChanged();
        }

        private ScoreRecord recordAt(int row) {
            return records.get(row);
        }

        @Override public int getRowCount() { return records.size(); }
        @Override public int getColumnCount() { return COLUMNS.length; }
        @Override public String getColumnName(int column) { return COLUMNS[column]; }

        @Override
        public Object getValueAt(int row, int column) {
            ScoreRecord record = records.get(row);
            return switch (column) {
                case 0 -> row + 1;
                case PLAYER -> record.name();
                case SCORE -> record.score();
                default -> throw new IndexOutOfBoundsException("Unknown scoreboard column: " + column);
            };
        }
    }
}

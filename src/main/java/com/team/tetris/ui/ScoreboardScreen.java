package com.team.tetris.ui;

import com.team.tetris.common.Difficulty;
import com.team.tetris.common.GameMode;
import com.team.tetris.common.Screen;
import com.team.tetris.scoreboard.ScoreRecord;
import com.team.tetris.scoreboard.ScoreboardRepository;

import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
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
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.FontMetrics;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Persistent ranking view. Disk reads run off the event dispatch thread. */
public final class ScoreboardScreen extends JPanel implements Screen {
    private static final Color BACKGROUND = Color.BLACK;
    private static final Color PANEL_BACKGROUND = new Color(18, 22, 30);
    private static final Color ROW_ALTERNATE = new Color(23, 28, 38);
    private static final Color HIGHLIGHT_BACKGROUND = new Color(0, 65, 75);
    private static final Color ACCENT = Color.CYAN;
    private static final Color MUTED = new Color(155, 165, 180);
    private static final int CELL_PADDING = 6;
    private static final Font BODY_FONT = new Font(Font.MONOSPACED, Font.BOLD, 15);
    private static final Font HEADER_FONT = new Font(Font.MONOSPACED, Font.BOLD, 15);
    private static final Font NARROW_HEADER_FONT = new Font(Font.MONOSPACED, Font.BOLD, 12);

    private final ScreenRouter router;
    private final ScoreboardRepository repository;
    private final ScoreTableModel tableModel = new ScoreTableModel();
    private final JTable results = new JTable(tableModel);
    private final CardLayout contentLayout = new CardLayout();
    private final JPanel content = new JPanel(contentLayout);
    private final JLabel status = new JLabel("", SwingConstants.CENTER);
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

        JPanel footer = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 8));
        footer.setBackground(BACKGROUND);
        footer.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, new Color(45, 58, 72)));
        JButton back = new JButton("Main Menu");
        back.setFont(new Font(Font.MONOSPACED, Font.BOLD, 12));
        back.setForeground(ACCENT);
        back.setBackground(new Color(30, 36, 46));
        back.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(65, 78, 94)),
                BorderFactory.createEmptyBorder(8, 18, 8, 18)));
        back.setFocusPainted(false);
        back.setOpaque(true);
        back.addActionListener(event -> router.showScreen(ScreenRouter.MAIN_MENU));
        footer.add(back);
        add(footer, BorderLayout.SOUTH);
        getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke("ESCAPE"), "back");
        getActionMap().put("back", new AbstractAction() {
            @Override public void actionPerformed(java.awt.event.ActionEvent event) {
                router.showScreen(ScreenRouter.MAIN_MENU);
            }
        });
    }

    public void highlight(UUID recordId) {
        highlightedId = recordId;
        results.repaint();
    }

    @Override public JPanel getPanel() { return this; }

    @Override public void onShow() {
        status.setText("LOADING SCORES...");
        contentLayout.show(content, "status");
        new SwingWorker<List<ScoreRecord>, Void>() {
            @Override protected List<ScoreRecord> doInBackground() throws Exception {
                return repository.load();
            }

            @Override protected void done() {
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

    @Override public void onHide() { highlightedId = null; }

    private JPanel createHeader() {
        JPanel header = new JPanel(new BorderLayout(0, 4));
        header.setBackground(BACKGROUND);
        JLabel title = new JLabel("LEADERBOARD", JLabel.CENTER);
        title.setForeground(Color.YELLOW);
        title.setFont(new Font(Font.MONOSPACED, Font.BOLD, 28));
        JLabel subtitle = new JLabel("TOP SCORES", JLabel.CENTER);
        subtitle.setForeground(MUTED);
        subtitle.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        header.add(title, BorderLayout.CENTER);
        header.add(subtitle, BorderLayout.SOUTH);
        return header;
    }

    private void configureTable() {
        results.setFillsViewportHeight(true);
        results.setRowHeight(36);
        results.setShowGrid(false);
        results.setIntercellSpacing(new java.awt.Dimension(0, 0));
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
        header.setPreferredSize(new java.awt.Dimension(0, 42));
        header.setBackground(new Color(27, 33, 44));
        header.setForeground(ACCENT);
        header.setFont(new Font(Font.MONOSPACED, Font.BOLD, 15));
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

    /** 머리글과 가장 긴 값이 잘리지 않는 폭. 좁은 창에서도 MODE·DIFFICULTY·SCORE가 보이게 한다. */
    private int fittedWidth(int column) {
        List<String> values = switch (column) {
            case ScoreTableModel.MODE -> Arrays.stream(GameMode.values()).map(GameMode::label).toList();
            case ScoreTableModel.DIFFICULTY -> Arrays.stream(Difficulty.values()).map(Difficulty::label).toList();
            case ScoreTableModel.SCORE -> List.of("999999");
            default -> List.of("▶ 10");
        };
        FontMetrics body = results.getFontMetrics(BODY_FONT);
        FontMetrics header = results.getFontMetrics(headerFontOf(column));
        int widest = header.stringWidth(tableModel.getColumnName(column));
        for (String value : values) widest = Math.max(widest, body.stringWidth(value));
        return widest + CELL_PADDING * 2 + 2;
    }

    private static Font headerFontOf(int column) {
        return column == ScoreTableModel.MODE || column == ScoreTableModel.DIFFICULTY
                ? NARROW_HEADER_FONT : HEADER_FONT;
    }

    /** RANK·MODE·DIFFICULTY는 가운데, PLAYER는 왼쪽, SCORE는 오른쪽 정렬이다. */
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
            setFont(new Font(Font.MONOSPACED, Font.BOLD, 15));
        }

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean selected,
                                                       boolean focused, int row, int column) {
            super.getTableCellRendererComponent(table, value, false, false, row, column);
            setForeground(ACCENT);
            setBackground(new Color(27, 33, 44));
            setFont(headerFontOf(column));
            setHorizontalAlignment(alignmentOf(column));
            setBorder(BorderFactory.createEmptyBorder(0, CELL_PADDING, 0, CELL_PADDING));
            return this;
        }
    }

    private static final class ScoreTableModel extends AbstractTableModel {
        private static final int PLAYER = 1;
        private static final int MODE = 2;
        private static final int DIFFICULTY = 3;
        private static final int SCORE = 4;
        private static final String[] COLUMNS = {"RANK", "PLAYER", "MODE", "DIFFICULTY", "SCORE"};
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
                case MODE -> record.mode().label();
                case DIFFICULTY -> record.difficulty().label();
                case SCORE -> record.score();
                default -> throw new IndexOutOfBoundsException("Unknown scoreboard column: " + column);
            };
        }
    }
}

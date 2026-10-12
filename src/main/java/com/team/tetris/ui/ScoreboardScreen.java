package com.team.tetris.ui;

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

        results.getColumnModel().getColumn(0).setPreferredWidth(76);
        results.getColumnModel().getColumn(0).setMaxWidth(88);
        results.getColumnModel().getColumn(1).setPreferredWidth(240);
        results.getColumnModel().getColumn(2).setPreferredWidth(116);
        results.getColumnModel().getColumn(0).setCellRenderer(new ScoreCellRenderer(SwingConstants.CENTER));
        results.getColumnModel().getColumn(1).setCellRenderer(new ScoreCellRenderer(SwingConstants.LEFT));
        results.getColumnModel().getColumn(2).setCellRenderer(new ScoreCellRenderer(SwingConstants.RIGHT));
        results.getColumnModel().getColumn(2).setHeaderRenderer(new HeaderRenderer(SwingConstants.RIGHT));
        results.getColumnModel().getColumn(0).setHeaderRenderer(new HeaderRenderer(SwingConstants.CENTER));
        results.getColumnModel().getColumn(1).setHeaderRenderer(new HeaderRenderer(SwingConstants.LEFT));
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
            setHorizontalAlignment(column == 0 ? SwingConstants.CENTER
                    : column == 1 ? SwingConstants.LEFT : SwingConstants.RIGHT);
            setBorder(BorderFactory.createEmptyBorder(0, 14, 0, 14));
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
            setFont(new Font(Font.MONOSPACED, Font.BOLD, 15));
            setHorizontalAlignment(column == 0 ? SwingConstants.CENTER
                    : column == 1 ? SwingConstants.LEFT : SwingConstants.RIGHT);
            setBorder(BorderFactory.createEmptyBorder(0, 14, 0, 14));
            return this;
        }
    }

    private static final class ScoreTableModel extends AbstractTableModel {
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
                case 1 -> record.name();
                case 2 -> record.score();
                default -> throw new IndexOutOfBoundsException("Unknown scoreboard column: " + column);
            };
        }
    }
}

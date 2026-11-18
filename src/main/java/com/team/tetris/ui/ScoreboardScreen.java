package com.team.tetris.ui;

import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.font.TextAttribute;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Function;

import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
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

import com.team.tetris.common.Difficulty;
import com.team.tetris.common.GameMode;
import com.team.tetris.common.Screen;
import com.team.tetris.scoreboard.ScoreRecord;
import com.team.tetris.scoreboard.ScoreboardRepository;
import com.team.tetris.ui.theme.PillButton;
import com.team.tetris.ui.theme.ThemedLabel;
import com.team.tetris.ui.theme.UiColors;
import com.team.tetris.ui.theme.UiFonts;
import com.team.tetris.ui.theme.UiPainter;

/**
 * Persistent ranking view. Disk reads run off the event dispatch thread.
 * One mode and difficulty ranking is shown at a time; the arrow keys switch between them.
 *
 * 표(JTable)는 유지하고, 셀 렌더러가 행 전체를 덮는 둥근 카드의 자기 조각을 그려 행마다 카드처럼 보이게 한다.
 */
public final class ScoreboardScreen extends JPanel implements Screen {
    private static final Color HIGHLIGHT_FILL = new Color(0x3A3060);
    private static final Color[] MEDAL_COLORS = {new Color(0xFFD34E), new Color(0xC9CBD6), new Color(0xE0A060)};
    /** 창 크기 L(600px) 기준 수치. applyScale에서 배율을 곱한다. */
    private static final int BASE_ROW_HEIGHT = 52;
    private static final int BASE_CELL_PADDING = 6;
    private static final int BASE_EDGE_PADDING = 18;
    /** 한 순위표에 보이는 기록 수 (요구사항: 상위 10명). */
    private static final int VISIBLE_ROWS = 10;

    private final ScreenRouter router;
    private final ScoreboardRepository repository;
    private final ScoreTableModel tableModel = new ScoreTableModel();
    private final JTable results = new JTable(tableModel);
    private final CardLayout contentLayout = new CardLayout();
    private final JPanel content = new JPanel(contentLayout);
    private final JLabel status = new ThemedLabel("", SwingConstants.CENTER);
    private final JLabel title = new JLabel("스코어보드");
    private final JLabel keys = new ThemedLabel("←→ 난이도  ·  ↑↓ 모드  ·  Esc 나가기",
            SwingConstants.CENTER);
    private final PillButton back = new PillButton("메인 화면으로", false);
    private final List<JLabel> modeTabs = new ArrayList<>();
    private final List<JLabel> difficultyTabs = new ArrayList<>();
    private GameMode selectedMode = GameMode.NORMAL;
    private Difficulty selectedDifficulty = Difficulty.NORMAL;
    private int loadGeneration;
    private UUID highlightedId;
    private float appliedScale = -1f;
    // 배율이 바뀔 때 applyScale이 다시 계산하는 렌더링 수치.
    private int cellPadding = BASE_CELL_PADDING;
    private int edgePadding = BASE_EDGE_PADDING;
    private int rowGap = 8;
    private Font bodyFont = UiFonts.mono(17f);
    private Font headerFont = UiFonts.body(11f);

    public ScoreboardScreen(ScreenRouter router, ScoreboardRepository repository) {
        this.router = Objects.requireNonNull(router, "router");
        this.repository = Objects.requireNonNull(repository, "repository");
        setLayout(new BorderLayout(0, 14));
        setBackground(UiColors.BACKGROUND);
        add(createHeader(), BorderLayout.NORTH);

        configureTable();
        JScrollPane resultScroll = new JScrollPane(results);
        resultScroll.setBorder(BorderFactory.createEmptyBorder());
        resultScroll.setOpaque(false);
        resultScroll.getViewport().setBackground(UiColors.BACKGROUND);
        // 순위는 최대 10개다. 스크롤 대신 보이는 높이를 10등분해 행 높이를 정한다.
        resultScroll.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_NEVER);
        resultScroll.getViewport().addComponentListener(new ComponentAdapter() {
            @Override public void componentResized(ComponentEvent event) {
                fitRows(event.getComponent().getHeight());
            }
        });
        content.setOpaque(false);
        content.add(resultScroll, "table");

        status.setForeground(UiColors.TEXT_MUTED);
        status.setOpaque(false);
        content.add(status, "status");
        add(content, BorderLayout.CENTER);

        JPanel footer = new JPanel(new BorderLayout(0, 10));
        footer.setOpaque(false);
        JPanel backRow = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 0));
        backRow.setOpaque(false);
        back.addActionListener(event -> router.showScreen(ScreenRouter.MAIN_MENU));
        backRow.add(back);
        keys.setForeground(UiColors.withAlpha(UiColors.TEXT_MUTED, 0.8f));
        footer.add(backRow, BorderLayout.NORTH);
        footer.add(keys, BorderLayout.SOUTH);
        add(footer, BorderLayout.SOUTH);

        bindKey("ESCAPE", "back", () -> router.showScreen(ScreenRouter.MAIN_MENU));
        bindKey("LEFT", "previousDifficulty",
                () -> select(selectedMode, step(Difficulty.values(), selectedDifficulty, -1)));
        bindKey("RIGHT", "nextDifficulty",
                () -> select(selectedMode, step(Difficulty.values(), selectedDifficulty, 1)));
        bindKey("UP", "previousMode", () -> select(step(GameMode.values(), selectedMode, -1), selectedDifficulty));
        bindKey("DOWN", "nextMode", () -> select(step(GameMode.values(), selectedMode, 1), selectedDifficulty));
        updateTabs();
        applyScale(1f);
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

    @Override
    public void doLayout() {
        // 창 크기(S/M/L)가 바뀌면 글자·행 높이·열 폭을 창 폭 배율에 맞춘다.
        float scale = UiPainter.scale(Math.max(1, getWidth()));
        if (scale != appliedScale) applyScale(scale);
        super.doLayout();
    }

    private void applyScale(float scale) {
        appliedScale = scale;
        int margin = Math.round(40 * scale);
        setBorder(BorderFactory.createEmptyBorder(Math.round(36 * scale), margin, Math.round(24 * scale), margin));
        title.setFont(UiFonts.display(Math.round(30 * scale)));
        status.setFont(UiFonts.body(Math.round(15 * scale)));
        keys.setFont(UiFonts.body(Math.round(12 * scale)));
        back.setFontSize(Math.round(17 * scale));
        // 누르는 동안 앞에 "> "가 붙으므로 그만큼 여유를 둔다.
        int backTextWidth = back.getFontMetrics(UiFonts.display(Math.round(17 * scale))).stringWidth("> " + back.getText());
        back.setPreferredSize(new Dimension(Math.max(Math.round(130 * scale), backTextWidth + Math.round(44 * scale)),
                Math.round(44 * scale)));
        Font tabFont = UiFonts.display(Math.round(15 * scale));
        for (JLabel tab : modeTabs) tab.setFont(tabFont);
        for (JLabel tab : difficultyTabs) tab.setFont(UiFonts.display(Math.round(13 * scale)));

        cellPadding = Math.round(BASE_CELL_PADDING * scale);
        edgePadding = Math.round(BASE_EDGE_PADDING * scale);
        rowGap = Math.round(8 * scale);
        bodyFont = UiFonts.mono(Math.round(17 * scale));
        headerFont = UiFonts.body(Math.round(11 * scale)).deriveFont(Map.of(TextAttribute.TRACKING, 0.15f));
        results.setRowHeight(Math.round(BASE_ROW_HEIGHT * scale));
        // 뷰포트 크기가 그대로면 크기 변경 알림이 오지 않으므로 여기서도 10행에 맞춘다.
        if (results.getParent() != null) fitRows(results.getParent().getHeight());
        results.getTableHeader().setPreferredSize(new Dimension(0, Math.round(26 * scale)));
        sizeColumns();
    }

    /** 보이는 높이에 10행이 모두 들어가도록 행 높이를 줄인다. 넉넉하면 기준 높이를 넘지 않는다. */
    private void fitRows(int viewportHeight) {
        if (viewportHeight <= 0) return;
        int rowHeight = Math.max(24, Math.min(Math.round(BASE_ROW_HEIGHT * appliedScale),
                viewportHeight / VISIBLE_ROWS));
        if (rowHeight == results.getRowHeight()) return;
        results.setRowHeight(rowHeight);
        // 순위 메달 크기가 행 높이를 따르므로 순위 열 폭도 다시 맞춘다.
        sizeColumns();
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
                        status.setText("아직 기록이 없어요");
                        contentLayout.show(content, "status");
                    } else {
                        contentLayout.show(content, "table");
                    }
                } catch (Exception failure) {
                    status.setText("기록을 불러오지 못했어요  ·  메인 화면에서 다시 시도해 주세요");
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

    /** 제목 + 모드 탭(왼쪽)과 난이도 탭(오른쪽). */
    private JPanel createHeader() {
        JPanel header = new JPanel(new BorderLayout(0, 18));
        header.setOpaque(false);
        title.setForeground(UiColors.TEXT_PRIMARY);
        header.add(title, BorderLayout.NORTH);

        JPanel tabs = new JPanel(new BorderLayout());
        tabs.setOpaque(false);
        tabs.add(tabRow(GameMode.values(), ScoreboardScreen::modeLabel,
                mode -> select(mode, selectedDifficulty), modeTabs, mode -> UiColors.ACCENT), BorderLayout.WEST);
        tabs.add(tabRow(Difficulty.values(), difficulty -> difficulty.name(),
                difficulty -> select(selectedMode, difficulty), difficultyTabs,
                difficulty -> UiPainter.difficultyBadgeColors(difficulty.name())[0]), BorderLayout.EAST);
        header.add(tabs, BorderLayout.CENTER);
        return header;
    }

    private static String modeLabel(GameMode mode) {
        return mode == GameMode.ITEM ? "아이템 모드" : "일반 모드";
    }

    /** 클릭으로도 고를 수 있는 알약 탭 한 줄. */
    private static <T> JPanel tabRow(T[] values, Function<T, String> label, Consumer<T> choose,
                                     List<JLabel> tabs, Function<T, Color> activeColor) {
        JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        row.setOpaque(false);
        for (T value : values) {
            PillTab tab = new PillTab(label.apply(value), activeColor.apply(value));
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
            ((PillTab) tabs.get(index)).setActive(index == selected);
        }
    }

    private void configureTable() {
        results.setFillsViewportHeight(true);
        results.setShowGrid(false);
        results.setIntercellSpacing(new Dimension(0, 0));
        results.setOpaque(false);
        results.setBackground(UiColors.BACKGROUND);
        results.setForeground(UiColors.CREAM);
        results.setSelectionBackground(UiColors.BACKGROUND);
        results.setSelectionForeground(UiColors.CREAM);
        results.setFocusable(false);
        results.setRowSelectionAllowed(false);
        results.setCellSelectionEnabled(false);
        results.setAutoCreateRowSorter(false);

        JTableHeader header = results.getTableHeader();
        header.setReorderingAllowed(false);
        header.setResizingAllowed(false);
        header.setBackground(UiColors.BACKGROUND);
        header.setBorder(BorderFactory.createEmptyBorder());

        for (int column = 0; column < tableModel.getColumnCount(); column++) {
            var tableColumn = results.getColumnModel().getColumn(column);
            tableColumn.setCellRenderer(new ScoreCellRenderer());
            tableColumn.setHeaderRenderer(new HeaderRenderer());
        }
    }

    private void sizeColumns() {
        for (int column = 0; column < tableModel.getColumnCount(); column++) {
            var tableColumn = results.getColumnModel().getColumn(column);
            if (column == ScoreTableModel.PLAYER) {
                // 이름 열이 남는 폭을 모두 받고, 좁은 창(480px)에서는 이 열만 줄어든다.
                tableColumn.setMinWidth(60);
                tableColumn.setPreferredWidth(400);
                tableColumn.setMaxWidth(Integer.MAX_VALUE);
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
        FontMetrics header = results.getFontMetrics(headerFont);
        int content = column == ScoreTableModel.SCORE
                ? results.getFontMetrics(bodyFont).stringWidth("999,999")
                : rankContentWidth();
        int widest = Math.max(header.stringWidth(tableModel.getColumnName(column)), content);
        return widest + leftPadding(column) + rightPadding(column) + 2;
    }

    /** 순위 칸 내용 폭: 강조 화살표 + 메달 원. */
    private int rankContentWidth() {
        return results.getFontMetrics(arrowFont()).stringWidth("▶ ") + medalSize();
    }

    /** IBM Plex Mono에는 ▶ 글리프가 없어 화살표만 본문 서체로 그린다. */
    private Font arrowFont() {
        return UiFonts.system(bodyFont.getSize2D());
    }

    private int medalSize() {
        return Math.round(results.getRowHeight() * 0.56f);
    }

    // 행 카드 안쪽 여백: 카드 양 끝 열은 넓게, 가운데 열 사이는 좁게.
    private int leftPadding(int column) {
        return column == 0 ? edgePadding : cellPadding;
    }

    private int rightPadding(int column) {
        return column == ScoreTableModel.SCORE ? edgePadding : cellPadding;
    }

    /** RANK는 가운데, PLAYER는 왼쪽, SCORE는 오른쪽 정렬이다. */
    private static int alignmentOf(int column) {
        return switch (column) {
            case ScoreTableModel.PLAYER -> SwingConstants.LEFT;
            case ScoreTableModel.SCORE -> SwingConstants.RIGHT;
            default -> SwingConstants.CENTER;
        };
    }

    /**
     * 한 칸을 그린다. 각 칸이 행 전체 폭의 카드를 자기 위치만큼 왼쪽으로 옮겨 그리므로,
     * 잘린 조각이 이어져 행 하나가 둥근 카드 한 장으로 보인다.
     */
    private final class ScoreCellRenderer extends DefaultTableCellRenderer {
        private int row;
        private int column;
        private boolean highlighted;

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean selected,
                                                       boolean focused, int row, int column) {
            super.getTableCellRendererComponent(table, value, false, false, row, column);
            this.row = row;
            this.column = column;
            ScoreRecord record = tableModel.recordAt(row);
            highlighted = record.id().equals(highlightedId);
            setOpaque(false);
            setHorizontalAlignment(alignmentOf(column));
            setBorder(BorderFactory.createEmptyBorder(0, leftPadding(column), 0, rightPadding(column)));
            switch (column) {
                case 0 -> {
                    setText((highlighted ? "▶ " : "") + (row + 1));
                    setFont(bodyFont);
                }
                case ScoreTableModel.PLAYER -> {
                    setText(record.name());
                    setFont(UiFonts.monoOrFallback(record.name(), bodyFont.getSize2D()));
                    setForeground(UiColors.CREAM);
                }
                default -> {
                    setText(String.format(Locale.US, "%,d", record.score()));
                    setFont(bodyFont);
                    setForeground(UiColors.ACCENT);
                }
            }
            return this;
        }

        @Override
        public Dimension getPreferredSize() {
            if (column != 0) return super.getPreferredSize();
            Dimension size = super.getPreferredSize();
            return new Dimension(rankContentWidth() + leftPadding(0) + rightPadding(0), size.height);
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2d = (Graphics2D) g.create();
            try {
                UiPainter.prepare(g2d);
                Rectangle cell = results.getCellRect(row, column, false);
                int cardTop = rowGap / 2;
                int cardHeight = getHeight() - rowGap;
                int radius = Math.round(cardHeight * 0.3f);
                if (highlighted) {
                    UiPainter.drawPanel(g2d, -cell.x, cardTop, results.getWidth(), cardHeight, radius,
                            HIGHLIGHT_FILL, UiColors.ACCENT, 2.5f);
                } else {
                    UiPainter.drawPanel(g2d, -cell.x, cardTop, results.getWidth(), cardHeight, radius,
                            UiColors.PANEL, UiColors.PANEL_BORDER, 1f);
                }
                if (column == 0) {
                    paintRank(g2d, cardTop, cardHeight);
                    return;
                }
            } finally {
                g2d.dispose();
            }
            super.paintComponent(g);
        }

        // 1~3위는 금·은·동 메달, 그 아래는 패널색 원. 방금 등록한 기록은 앞에 ▶를 붙인다.
        private void paintRank(Graphics2D g2d, int cardTop, int cardHeight) {
            int medal = medalSize();
            int arrowWidth = getFontMetrics(arrowFont()).stringWidth("▶ ");
            int contentX = leftPadding(0) + (getWidth() - leftPadding(0) - rightPadding(0)
                    - rankContentWidth()) / 2;
            int medalX = contentX + arrowWidth;
            int medalY = cardTop + (cardHeight - medal) / 2;
            if (highlighted) {
                g2d.setFont(arrowFont());
                g2d.setColor(UiColors.ACCENT);
                g2d.drawString("▶", contentX, UiPainter.centeredBaseline(g2d.getFontMetrics(), medalY, medal));
            }
            boolean podium = row < MEDAL_COLORS.length;
            Color medalColor = podium ? MEDAL_COLORS[row] : UiColors.PANEL_BORDER;
            g2d.setColor(UiColors.darker(medalColor, 0.2f));
            g2d.fillOval(medalX, medalY + 2, medal, medal);
            g2d.setColor(medalColor);
            g2d.fillOval(medalX, medalY, medal, medal);
            String rank = String.valueOf(row + 1);
            g2d.setFont(UiFonts.display(Math.round(medal * 0.6f)));
            g2d.setColor(podium ? UiColors.TEXT_ON_ACCENT : UiColors.TEXT_SECONDARY);
            UiPainter.drawCentered(g2d, rank, medalX + medal / 2,
                    UiPainter.centeredBaseline(g2d.getFontMetrics(), medalY, medal));
        }
    }

    private final class HeaderRenderer extends DefaultTableCellRenderer {
        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean selected,
                                                       boolean focused, int row, int column) {
            super.getTableCellRendererComponent(table, value, false, false, row, column);
            setOpaque(true);
            setForeground(UiColors.TEXT_MUTED);
            setBackground(UiColors.BACKGROUND);
            setFont(headerFont);
            setHorizontalAlignment(alignmentOf(column));
            setBorder(BorderFactory.createEmptyBorder(0, leftPadding(column), 0, rightPadding(column)));
            return this;
        }
    }

    /** 알약 모양 탭. 선택되면 지정 색으로 채우고, 아니면 패널색 + 테두리. */
    private static final class PillTab extends JLabel {
        private final Color activeColor;
        private boolean active;

        PillTab(String text, Color activeColor) {
            super(text, SwingConstants.CENTER);
            this.activeColor = activeColor;
            setOpaque(false);
        }

        void setActive(boolean active) {
            this.active = active;
            setForeground(active ? UiColors.TEXT_ON_ACCENT : UiColors.TEXT_SECONDARY);
            repaint();
        }

        @Override
        public Dimension getPreferredSize() {
            FontMetrics metrics = getFontMetrics(getFont());
            int height = Math.round(metrics.getAscent() * 2.1f);
            return new Dimension(metrics.stringWidth(getText()) + height, height);
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2d = (Graphics2D) g.create();
            try {
                UiPainter.prepare(g2d);
                int height = getHeight() - 3;
                if (active) {
                    g2d.setColor(UiColors.darker(activeColor, 0.18f));
                    g2d.fillRoundRect(0, 3, getWidth(), height, height, height);
                    g2d.setColor(activeColor);
                    g2d.fillRoundRect(0, 0, getWidth(), height, height, height);
                } else {
                    UiPainter.drawPanel(g2d, 0, 0, getWidth(), height, height / 2,
                            UiColors.PANEL, UiColors.PANEL_BORDER, 2f);
                }
                g2d.setFont(getFont());
                g2d.setColor(getForeground());
                UiPainter.drawCentered(g2d, getText(), getWidth() / 2,
                        UiPainter.centeredBaseline(g2d.getFontMetrics(), 0, height));
            } finally {
                g2d.dispose();
            }
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

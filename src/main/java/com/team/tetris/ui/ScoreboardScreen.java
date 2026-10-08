package com.team.tetris.ui;

import com.team.tetris.common.Screen;
import com.team.tetris.scoreboard.ScoreRecord;
import com.team.tetris.scoreboard.ScoreboardRepository;

import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.KeyStroke;
import javax.swing.SwingWorker;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Font;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Persistent ranking view. Disk reads run off the event dispatch thread. */
public final class ScoreboardScreen extends JPanel implements Screen {
    private static final int DISPLAY_LIMIT = 10;
    private static final DateTimeFormatter DATE_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd").withZone(ZoneId.systemDefault());
    private final ScreenRouter router;
    private final ScoreboardRepository repository;
    private final JTextArea results = new JTextArea();
    private UUID highlightedId;

    public ScoreboardScreen(ScreenRouter router, ScoreboardRepository repository) {
        this.router = Objects.requireNonNull(router, "router");
        this.repository = Objects.requireNonNull(repository, "repository");
        setLayout(new BorderLayout(12, 12));
        setBackground(Color.BLACK);
        results.setEditable(false);
        results.setBackground(Color.BLACK);
        results.setForeground(Color.WHITE);
        results.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 17));
        add(new JScrollPane(results), BorderLayout.CENTER);
        JButton back = new JButton("Main Menu");
        back.addActionListener(event -> router.showScreen(ScreenRouter.MAIN_MENU));
        add(back, BorderLayout.SOUTH);
        getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke("ESCAPE"), "back");
        getActionMap().put("back", new javax.swing.AbstractAction() {
            @Override public void actionPerformed(java.awt.event.ActionEvent event) {
                router.showScreen(ScreenRouter.MAIN_MENU);
            }
        });
    }

    public void highlight(UUID recordId) {
        highlightedId = recordId;
    }

    @Override public JPanel getPanel() { return this; }

    @Override public void onShow() {
        results.setText("Loading scores...");
        new SwingWorker<List<ScoreRecord>, Void>() {
            @Override protected List<ScoreRecord> doInBackground() throws Exception {
                return repository.load();
            }

            @Override protected void done() {
                try {
                    List<ScoreRecord> records = get();
                    if (records.isEmpty()) {
                        results.setText("No scores yet.");
                        return;
                    }
                    StringBuilder text = new StringBuilder(
                            "  RANK  NAME                    SCORE  DATE\n\n");
                    int displayedRecords = Math.min(DISPLAY_LIMIT, records.size());
                    for (int index = 0; index < displayedRecords; index++) {
                        ScoreRecord record = records.get(index);
                        String marker = record.id().equals(highlightedId) ? "▶" : " ";
                        text.append(String.format("%s %3d   %-20s %10d  %s%n", marker, index + 1,
                                record.name(), record.score(), DATE_FORMAT.format(record.recordedAt())));
                    }
                    results.setText(text.toString());
                } catch (Exception failure) {
                    results.setText("Could not load scores. Return to the menu and try again.");
                }
            }
        }.execute();
    }

    @Override public void onHide() { highlightedId = null; }
}

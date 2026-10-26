package com.team.tetris.ui;

import com.team.tetris.common.Difficulty;
import com.team.tetris.common.GameMode;
import com.team.tetris.scoreboard.ScoreRecord;
import com.team.tetris.scoreboard.ScoreboardRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.swing.JTable;
import javax.swing.SwingUtilities;
import java.awt.Component;
import java.awt.Container;
import java.awt.GraphicsEnvironment;
import java.awt.event.KeyEvent;
import java.lang.reflect.InvocationTargetException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

class ScoreboardScreenTest {
    @TempDir Path directory;

    @BeforeEach
    void requireDisplay() {
        assumeFalse(GraphicsEnvironment.isHeadless(), "ScreenRouter is a window");
    }

    /** 요구사항 2.1·4.1: 게임이 끝나면 방금 플레이한 모드·난이도의 순위를 보여 주고 등록한 기록을 강조한다. */
    @Test
    void showsOnlyThePlayedRankingWithSixDigitScoresUncutInTheSmallestWindow() throws Exception {
        ScoreboardRepository repository = new ScoreboardRepository(directory.resolve("scores.properties"));
        ScoreRecord best = repository.add("ItemPlayer", 999999, Difficulty.HARD, GameMode.ITEM).orElseThrow();
        repository.add("SecondItem", 200, Difficulty.HARD, GameMode.ITEM);
        repository.add("NormalPlayer", 300, Difficulty.HARD, GameMode.NORMAL);
        repository.add("EasyItem", 400, Difficulty.EASY, GameMode.ITEM);
        ScreenRouter[] router = new ScreenRouter[1];
        ScoreboardScreen[] screen = new ScoreboardScreen[1];
        edt(() -> {
            router[0] = new ScreenRouter();
            screen[0] = new ScoreboardScreen(router[0], repository);
            router[0].addScreen(ScreenRouter.SCOREBOARD, screen[0]);
            router[0].updateWindowSize(480, 640);
            router[0].setVisible(true);
            screen[0].select(GameMode.ITEM, Difficulty.HARD);
            screen[0].highlight(best.id());
            router[0].showScreen(ScreenRouter.SCOREBOARD);
        });
        JTable table = waitForRows(screen[0], 2);
        edt(() -> {
            try {
                List<String> headers = new ArrayList<>();
                for (int column = 0; column < table.getColumnCount(); column++) headers.add(table.getColumnName(column));
                assertEquals(List.of("RANK", "PLAYER", "SCORE"), headers);
                assertEquals(List.of(1, "ItemPlayer", 999999), row(table, 0));
                assertEquals(List.of(2, "SecondItem", 200), row(table, 1));
                assertTrue(renderedText(table, 0, 0).startsWith("▶"), "The saved record is highlighted");

                for (int column : new int[]{0, 2}) {
                    var tableColumn = table.getColumnModel().getColumn(column);
                    Component cell = table.prepareRenderer(table.getCellRenderer(0, column), 0, column);
                    Component header = tableColumn.getHeaderRenderer().getTableCellRendererComponent(
                            table, tableColumn.getHeaderValue(), false, false, -1, column);
                    int needed = Math.max(cell.getPreferredSize().width, header.getPreferredSize().width);
                    assertTrue(tableColumn.getWidth() >= needed,
                            table.getColumnName(column) + " is not cut off at 480×640");
                }
                assertTrue(table.getColumnModel().getTotalColumnWidth() <= table.getParent().getWidth(),
                        "No column is pushed out of view");
            } finally {
                router[0].dispose();
            }
        });
    }

    /** 메뉴에서 들어오면 Normal 모드·Normal 난이도부터 보이고, ←→로 난이도, ↑↓로 모드를 바꾼다. */
    @Test
    void arrowKeysSwitchBetweenModeAndDifficultyRankings() throws Exception {
        ScoreboardRepository repository = new ScoreboardRepository(directory.resolve("scores.properties"));
        repository.add("NormalNormal", 100, Difficulty.NORMAL, GameMode.NORMAL);
        repository.add("NormalHard", 200, Difficulty.HARD, GameMode.NORMAL);
        repository.add("ItemHard", 300, Difficulty.HARD, GameMode.ITEM);
        repository.add("ItemEasy", 400, Difficulty.EASY, GameMode.ITEM);
        ScreenRouter[] router = new ScreenRouter[1];
        ScoreboardScreen[] screen = new ScoreboardScreen[1];
        edt(() -> {
            router[0] = new ScreenRouter();
            screen[0] = new ScoreboardScreen(router[0], repository);
            router[0].addScreen(ScreenRouter.SCOREBOARD, screen[0]);
            router[0].setVisible(true);
            router[0].showScreen(ScreenRouter.SCOREBOARD);
        });
        try {
            waitForOnlyPlayer(screen[0], "NormalNormal");
            press(screen[0], KeyEvent.VK_RIGHT);
            waitForOnlyPlayer(screen[0], "NormalHard");
            press(screen[0], KeyEvent.VK_DOWN);
            waitForOnlyPlayer(screen[0], "ItemHard");
            press(screen[0], KeyEvent.VK_RIGHT);
            waitForOnlyPlayer(screen[0], "ItemEasy"); // Hard에서 →는 Easy로 돌아간다.
            press(screen[0], KeyEvent.VK_UP);
            waitForStatus(screen[0], "NO SCORES YET");
        } finally {
            edt(() -> router[0].dispose());
        }
    }

    @Test
    void emptyAndUnreadableScoreboardsShowAStatusInsteadOfTheTable() throws Exception {
        Path file = directory.resolve("scores.properties");
        ScoreboardRepository repository = new ScoreboardRepository(file);
        ScreenRouter[] router = new ScreenRouter[1];
        ScoreboardScreen[] screen = new ScoreboardScreen[1];
        edt(() -> {
            router[0] = new ScreenRouter();
            screen[0] = new ScoreboardScreen(router[0], repository);
            router[0].addScreen(ScreenRouter.SCOREBOARD, screen[0]);
            router[0].setVisible(true);
            router[0].showScreen(ScreenRouter.SCOREBOARD);
        });
        waitForStatus(screen[0], "NO SCORES YET");

        Files.writeString(file, "version=99\ncount=0");
        edt(() -> screen[0].onShow());
        waitForStatus(screen[0], "COULD NOT LOAD SCORES");
        edt(() -> router[0].dispose());
    }

    private static void press(ScoreboardScreen screen, int keyCode) throws Exception {
        edt(() -> screen.dispatchEvent(new KeyEvent(screen, KeyEvent.KEY_PRESSED, System.currentTimeMillis(),
                0, keyCode, KeyEvent.CHAR_UNDEFINED)));
    }

    /** Loading runs on a SwingWorker; wait until the switched ranking shows exactly the expected player. */
    private static void waitForOnlyPlayer(ScoreboardScreen screen, String expected) throws Exception {
        List<Object> seen = new ArrayList<>();
        for (int attempt = 0; attempt < 100; attempt++) {
            edt(() -> {
                seen.clear();
                JTable table = find(screen, JTable.class);
                if (!table.isShowing()) return;
                for (int row = 0; row < table.getRowCount(); row++) seen.add(table.getValueAt(row, 1));
            });
            if (seen.equals(List.of(expected))) return;
            Thread.sleep(20);
        }
        fail("Expected only " + expected + " but saw " + seen);
    }

    private static String renderedText(JTable table, int row, int column) {
        Component cell = table.prepareRenderer(table.getCellRenderer(row, column), row, column);
        return ((javax.swing.JLabel) cell).getText();
    }

    private static List<Object> row(JTable table, int row) {
        List<Object> values = new ArrayList<>();
        for (int column = 0; column < table.getColumnCount(); column++) values.add(table.getValueAt(row, column));
        return values;
    }

    /** Loading runs on a SwingWorker; poll the EDT until the rows arrive. */
    private static JTable waitForRows(ScoreboardScreen screen, int rows) throws Exception {
        JTable[] table = new JTable[1];
        for (int attempt = 0; attempt < 100; attempt++) {
            edt(() -> table[0] = find(screen, JTable.class));
            int[] count = new int[1];
            edt(() -> count[0] = table[0].getRowCount());
            if (count[0] == rows && table[0].isShowing()) return table[0];
            Thread.sleep(20);
        }
        fail("Scores were not shown");
        return null;
    }

    private static void waitForStatus(ScoreboardScreen screen, String prefix) throws Exception {
        for (int attempt = 0; attempt < 100; attempt++) {
            boolean[] shown = new boolean[1];
            edt(() -> shown[0] = visibleLabels(screen).stream().anyMatch(text -> text.startsWith(prefix)));
            if (shown[0]) return;
            Thread.sleep(20);
        }
        fail("Status was not shown: " + prefix);
    }

    private static List<String> visibleLabels(Container container) {
        List<String> texts = new ArrayList<>();
        for (Component child : container.getComponents()) {
            if (!child.isShowing()) continue;
            if (child instanceof javax.swing.JLabel label) texts.add(label.getText());
            if (child instanceof Container nested) texts.addAll(visibleLabels(nested));
        }
        return texts;
    }

    private static <T extends Component> T find(Container container, Class<T> type) {
        for (Component child : container.getComponents()) {
            if (type.isInstance(child)) return type.cast(child);
            if (child instanceof Container nested) {
                T found = find(nested, type);
                if (found != null) return found;
            }
        }
        return null;
    }

    private static void edt(Runnable action) throws Exception {
        try {
            SwingUtilities.invokeAndWait(action);
        } catch (InvocationTargetException failure) {
            if (failure.getCause() instanceof AssertionError assertion) throw assertion;
            if (failure.getCause() instanceof Exception exception) throw exception;
            throw failure;
        }
    }
}

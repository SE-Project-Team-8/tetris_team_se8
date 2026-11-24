/*
 * 로컬 대전 확인용 화면. 꾸미지 않은 임시 화면으로, 실행 인자 --versus=... 로만 띄운다 (docs/battle/ui-contract.md 5절).
 * 정식 대전 화면은 UI 담당이 만든다. 이 화면은 연결 지점(VersusMatch, VersusInput)을 쓰는 예제이기도 하다.
 */
package com.team.tetris.ui;

import com.team.tetris.block.ColorScheme;
import com.team.tetris.block.Rotation;
import com.team.tetris.block.Tetromino;
import com.team.tetris.block.TetrominoType;
import com.team.tetris.common.Screen;
import com.team.tetris.common.battle.GarbageLine;
import com.team.tetris.common.battle.Player;
import com.team.tetris.common.battle.VersusMode;
import com.team.tetris.common.events.BoardSnapshot;
import com.team.tetris.common.events.CellCodes;
import com.team.tetris.common.events.GameEventListener;
import com.team.tetris.game.GameAction;
import com.team.tetris.game.versus.MatchResult;
import com.team.tetris.game.versus.VersusInput;
import com.team.tetris.game.versus.VersusListener;
import com.team.tetris.game.versus.VersusMatch;
import com.team.tetris.settings.GameSettings;
import com.team.tetris.settings.VersusKeyBindings;
import com.team.tetris.ui.theme.CellPainter;
import com.team.tetris.ui.theme.UiColors;
import com.team.tetris.ui.theme.UiFonts;

import javax.swing.JPanel;
import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.KeyEventDispatcher;
import java.awt.KeyboardFocusManager;
import java.awt.RenderingHints;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.StringJoiner;
import java.util.function.Supplier;

/**
 * 두 보드와 플레이어별 점수·레벨·다음 블록·공격 대기열, 남은 시간, 결과를 그린다. 연출은 없다.
 *
 * 키: 대전 키는 {@link VersusInput}에 넘긴다. 일시정지 키는 일시정지·재개, 나가기 키는 메인 화면으로. 대전이 진행 중이
 * 아닐 때 Enter는 같은 방식으로 다시 대전, 1·2·3은 일반·아이템·시간제한으로 시작한다.
 */
public final class VersusCheckScreen extends JPanel implements Screen {
    /** {@link ScreenRouter}에 등록하는 이름. */
    public static final String NAME = "VERSUS_CHECK";
    /** 보드 두 개가 들어가는 창 크기. 화면을 떠나면 원래 크기로 돌린다. */
    static final int WINDOW_WIDTH = 960;
    static final int WINDOW_HEIGHT = 720;

    private static final int HEADER = 44;
    private static final int FOOTER = 40;

    private final ScreenRouter router;
    private final VersusMatch match;
    private final VersusInput input;
    private final Supplier<GameSettings> settings;
    private final Map<Player, PlayerView> views = new EnumMap<>(Player.class);
    private final Map<Player, List<GarbageLine>> queues = new EnumMap<>(Player.class);
    private final Map<Player, Integer> levels = new EnumMap<>(Player.class);
    private final KeyEventDispatcher dispatcher = this::dispatch;
    private final WindowAdapter focusLoss = new WindowAdapter() {
        @Override public void windowLostFocus(WindowEvent event) { input.releaseAll(); }
    };
    private VersusMode mode;
    private int remainingSeconds;
    private boolean paused;
    private MatchResult result;
    private int previousWidth;
    private int previousHeight;

    public VersusCheckScreen(ScreenRouter router, VersusMatch match, VersusInput input,
                             Supplier<GameSettings> settings, VersusMode mode) {
        this.router = Objects.requireNonNull(router, "router");
        this.match = Objects.requireNonNull(match, "match");
        this.input = Objects.requireNonNull(input, "input");
        this.settings = Objects.requireNonNull(settings, "settings");
        this.mode = Objects.requireNonNull(mode, "mode");
        setBackground(UiColors.BACKGROUND);
        setFocusable(true);
        match.setListener(new Events());
        for (Player player : Player.values()) {
            PlayerView view = new PlayerView();
            views.put(player, view);
            match.setPlayerListener(player, view);
            queues.put(player, List.of());
            levels.put(player, 1);
        }
    }

    @Override public JPanel getPanel() { return this; }

    @Override
    public void onShow() {
        previousWidth = router.getCurrentWidth();
        previousHeight = router.getCurrentHeight();
        router.updateWindowSize(WINDOW_WIDTH, WINDOW_HEIGHT);
        KeyboardFocusManager.getCurrentKeyboardFocusManager().addKeyEventDispatcher(dispatcher);
        router.addWindowFocusListener(focusLoss);
        start(mode);
    }

    @Override
    public void onHide() {
        KeyboardFocusManager.getCurrentKeyboardFocusManager().removeKeyEventDispatcher(dispatcher);
        router.removeWindowFocusListener(focusLoss);
        input.releaseAll();
        match.stop();
        router.updateWindowSize(previousWidth, previousHeight);
    }

    private boolean dispatch(KeyEvent event) {
        return switch (event.getID()) {
            case KeyEvent.KEY_PRESSED -> keyPressed(event.getKeyCode());
            case KeyEvent.KEY_RELEASED -> {
                input.keyReleased(event.getKeyCode());
                yield false;
            }
            default -> false;
        };
    }

    /** 창에서 받은 KEY_PRESSED 하나. 대전 키를 먼저 보고, 남은 키를 이 화면이 처리한다. */
    boolean keyPressed(int keyCode) {
        if (input.keyPressed(keyCode)) return true;
        GameSettings current = settings.get();
        if (keyCode == current.keyBindings().keyFor(GameAction.PAUSE)) {
            if (!match.pause()) match.resume();
            input.releaseAll();
            return true;
        }
        if (keyCode == current.keyBindings().keyFor(GameAction.QUIT)) {
            router.showScreen(ScreenRouter.MAIN_MENU);
            return true;
        }
        VersusMatch.State state = match.state();
        if (state == VersusMatch.State.RUNNING || state == VersusMatch.State.PAUSED) return false;
        VersusMode chosen = switch (keyCode) {
            case KeyEvent.VK_ENTER -> mode;
            case KeyEvent.VK_1 -> VersusMode.NORMAL;
            case KeyEvent.VK_2 -> VersusMode.ITEM;
            case KeyEvent.VK_3 -> VersusMode.TIME_LIMIT;
            default -> null;
        };
        if (chosen == null) return false;
        start(chosen);
        return true;
    }

    private void start(VersusMode chosen) {
        mode = chosen;
        result = null;
        paused = false;
        remainingSeconds = 0;
        input.releaseAll();
        match.start(chosen);
        repaint();
    }

    // ---- 그리기

    @Override
    protected void paintComponent(Graphics graphics) {
        super.paintComponent(graphics);
        Graphics2D g = (Graphics2D) graphics.create();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            ColorScheme colors = settings.get().isColorBlindModeEnabled()
                    ? ColorScheme.colorBlind() : ColorScheme.standard();
            int half = getWidth() / 2;
            int cell = Math.max(8, Math.min((getHeight() - HEADER - FOOTER - 16) / 20, (half - 40) / 16));
            drawHeader(g);
            for (Player player : Player.values()) {
                int left = player == Player.P1 ? 0 : half;
                drawPlayer(g, colors, player, left + (half - cell * 16) / 2, HEADER + 8, cell);
            }
            drawFooter(g);
            if (result != null) drawResult(g);
        } finally {
            g.dispose();
        }
    }

    private void drawHeader(Graphics2D g) {
        g.setColor(UiColors.TEXT_PRIMARY);
        StringBuilder title = new StringBuilder("대전 확인용 · ").append(modeName(mode));
        if (mode == VersusMode.TIME_LIMIT) {
            title.append(" · 남은 시간 ").append(remainingSeconds / 60).append(':')
                    .append(String.format("%02d", remainingSeconds % 60));
        }
        if (paused) title.append(" · 일시정지");
        text(g, title.toString(), 18f, 16, 30);
    }

    private void drawPlayer(Graphics2D g, ColorScheme colors, Player player, int x, int y, int cell) {
        PlayerView view = views.get(player);
        g.setColor(UiColors.BOARD);
        g.fillRect(x, y, cell * 10, cell * 20);
        if (view.board != null) {
            int[][] cells = view.board.cells();
            for (int row = 0; row < cells.length; row++) {
                for (int col = 0; col < cells[row].length; col++) {
                    drawCell(g, colors, cells[row][col], x + col * cell, y + row * cell, cell);
                }
            }
        }

        int side = x + cell * 11;
        g.setColor(player == Player.P1 ? UiColors.PLAYER1 : UiColors.PLAYER2);
        text(g, player.name(), 16f, side, y + 16);
        g.setColor(UiColors.TEXT_PRIMARY);
        text(g, "점수 " + view.score, 16f, side, y + 40);
        text(g, "레벨 " + levels.get(player), 16f, side, y + 62);
        text(g, "다음", 16f, side, y + 90);
        int small = Math.max(6, cell * 3 / 5);
        for (int i = 0; i < view.next.length; i++) {
            drawNext(g, colors, view.next[i], side, y + 98 + i * small * 3, small);
        }

        int queueTop = y + cell * 10;
        int queueCell = Math.max(4, cell / 2);
        g.setColor(UiColors.TEXT_PRIMARY);
        text(g, "대기열 " + queues.get(player).size() + "줄", 16f, side, queueTop - 8);
        g.setColor(UiColors.BOARD);
        g.fillRect(side, queueTop, queueCell * 10, queueCell * 10);
        List<GarbageLine> lines = queues.get(player);
        for (int row = 0; row < lines.size(); row++) {
            for (int col = 0; col < GarbageLine.COLS; col++) {
                if (lines.get(row).filled(col)) {
                    CellPainter.drawGarbage(g, side + col * queueCell, queueTop + row * queueCell, queueCell);
                }
            }
        }
    }

    private static void drawCell(Graphics2D g, ColorScheme colors, int code, int x, int y, int size) {
        if (CellCodes.isGarbage(code)) {
            CellPainter.drawGarbage(g, x + 1, y + 1, size - 2);
        } else if (CellCodes.isValid(code)) {
            Color color = new Color(colors.rgbOf(TetrominoType.fromId(CellCodes.baseType(code))));
            char symbol = CellCodes.symbol(code);
            if (symbol == CellCodes.NO_SYMBOL) CellPainter.drawBlock(g, x + 1, y + 1, size - 2, color);
            else CellPainter.drawItem(g, x + 1, y + 1, size - 2, color, symbol);
        }
    }

    /** 다음 블록 하나를 처음 방향 모양으로 그린다. 아이템이면 첫 칸에 아이템 문자를 그린다. */
    private static void drawNext(Graphics2D g, ColorScheme colors, int code, int x, int y, int size) {
        if (!CellCodes.isValid(code)) return;
        TetrominoType type = TetrominoType.fromId(CellCodes.baseType(code));
        char symbol = CellCodes.symbol(code);
        boolean first = true;
        for (var cell : new Tetromino(type, Rotation.SPAWN, 0, 0).cells()) {
            int code1 = first && symbol != CellCodes.NO_SYMBOL ? code : CellCodes.cell(type.id(), CellCodes.NO_SYMBOL);
            drawCell(g, colors, code1, x + cell.col() * size, y + cell.row() * size, size);
            first = false;
        }
    }

    private void drawFooter(Graphics2D g) {
        GameSettings current = settings.get();
        VersusKeyBindings keys = current.versusKeys();
        String guide = "P1 " + keyGuide(keys, Player.P1) + "   P2 " + keyGuide(keys, Player.P2)
                + "   일시정지 " + keyName(current.keyBindings().keyFor(GameAction.PAUSE))
                + "  나가기 " + keyName(current.keyBindings().keyFor(GameAction.QUIT));
        g.setColor(UiColors.TEXT_SECONDARY);
        text(g, guide, 14f, 16, getHeight() - 14);
    }

    private void drawResult(Graphics2D g) {
        String winner = result.isDraw() ? "무승부" : result.winner().name() + " 승리";
        String reason = result.reason() == MatchResult.Reason.TOP_OUT ? "상대가 더 쌓을 수 없음" : "시간 종료";
        String[] lines = {
                winner + " (" + reason + ")",
                "P1 " + result.p1Score() + "점  ·  P2 " + result.p2Score() + "점",
                "Enter 다시 대전  ·  1 일반  2 아이템  3 시간제한  ·  "
                        + keyName(settings.get().keyBindings().keyFor(GameAction.QUIT)) + " 메인 화면"};
        int boxWidth = Math.min(getWidth() - 40, 620);
        int boxX = (getWidth() - boxWidth) / 2;
        int boxY = getHeight() / 2 - 60;
        g.setColor(new Color(0, 0, 0, 200));
        g.fillRect(boxX, boxY, boxWidth, 120);
        g.setColor(UiColors.ACCENT);
        text(g, lines[0], 24f, boxX + 20, boxY + 36);
        g.setColor(UiColors.TEXT_PRIMARY);
        text(g, lines[1], 16f, boxX + 20, boxY + 70);
        text(g, lines[2], 16f, boxX + 20, boxY + 98);
    }

    /** 본문 서체로 쓰되 서체에 없는 글자(·, 화살표)는 시스템 서체로 그린다. 셀 그리기가 바꾼 서체에 영향받지 않는다. */
    private static void text(Graphics2D g, String value, float size, int x, int y) {
        g.drawString(UiFonts.withFallback(value, UiFonts.body(size)).getIterator(), x, y);
    }

    private static String keyGuide(VersusKeyBindings keys, Player player) {
        StringJoiner joiner = new StringJoiner(" ");
        for (GameAction action : VersusKeyBindings.PLAYER_ACTIONS) joiner.add(keyName(keys.keyFor(player, action)));
        return joiner.toString();
    }

    static String keyName(int keyCode) {
        return switch (keyCode) {
            case KeyEvent.VK_LEFT -> "←";
            case KeyEvent.VK_RIGHT -> "→";
            case KeyEvent.VK_UP -> "↑";
            case KeyEvent.VK_DOWN -> "↓";
            case KeyEvent.VK_ESCAPE -> "Esc";
            case KeyEvent.VK_SHIFT -> "Shift";
            case KeyEvent.VK_ENTER -> "Enter";
            default -> KeyEvent.getKeyText(keyCode);
        };
    }

    private static String modeName(VersusMode mode) {
        return switch (mode) {
            case NORMAL -> "일반";
            case ITEM -> "아이템";
            case TIME_LIMIT -> "시간제한";
        };
    }

    // ---- 알림

    /** 대전 전체의 알림을 받아 다시 그린다. */
    private final class Events implements VersusListener {
        @Override public void onQueueChanged(Player player, List<GarbageLine> lines) {
            queues.put(player, lines);
            repaint();
        }

        @Override public void onLevelChanged(Player player, int level) {
            levels.put(player, level);
            repaint();
        }

        @Override public void onTimeChanged(int seconds) {
            remainingSeconds = seconds;
            repaint();
        }

        @Override public void onPauseChanged(boolean value) {
            paused = value;
            repaint();
        }

        @Override public void onMatchEnded(MatchResult ended) {
            result = ended;
            input.releaseAll();
            repaint();
        }
    }

    /** 플레이어 한 명의 표시 정보. 1인 모드 화면이 받는 것과 같은 알림이다. */
    private final class PlayerView implements GameEventListener {
        BoardSnapshot board;
        int score;
        int[] next = new int[0];

        @Override public void onBoardUpdated(BoardSnapshot snapshot) {
            board = snapshot;
            repaint();
        }

        @Override public void onScoreChanged(int value) {
            score = value;
            repaint();
        }

        @Override public void onNextBlocksChanged(int[] blockTypes) {
            next = blockTypes.clone();
            repaint();
        }

        @Override public void onPauseStateChanged(boolean value) { }

        @Override public void onGameOver(int value) { }
    }

    // ---- 테스트용

    VersusMode currentMode() { return mode; }

    MatchResult result() { return result; }
}

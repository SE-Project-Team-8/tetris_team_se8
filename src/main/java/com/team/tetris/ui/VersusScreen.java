/*
 * 로컬 2인 대전 화면 (docs/battle/ui-handoff.md 2절, 목업 VersusScreen·VersusAttack).
 */
package com.team.tetris.ui;

import java.awt.Color;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.KeyEventDispatcher;
import java.awt.KeyboardFocusManager;
import java.awt.Rectangle;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.StringJoiner;
import java.util.function.Consumer;
import java.util.function.Supplier;

import javax.swing.JPanel;
import javax.swing.Timer;

import com.team.tetris.block.ColorScheme;
import com.team.tetris.common.Screen;
import com.team.tetris.common.battle.GarbageLine;
import com.team.tetris.common.battle.Player;
import com.team.tetris.common.battle.VersusMode;
import com.team.tetris.common.constants.GameConstants;
import com.team.tetris.common.events.BoardSnapshot;
import com.team.tetris.common.events.CellPosition;
import com.team.tetris.common.events.GameEventListener;
import com.team.tetris.game.GameAction;
import com.team.tetris.game.item.ItemRegistry;
import com.team.tetris.game.versus.MatchResult;
import com.team.tetris.game.versus.SidedKey;
import com.team.tetris.game.versus.VersusInput;
import com.team.tetris.game.versus.VersusListener;
import com.team.tetris.game.versus.VersusMatch;
import com.team.tetris.settings.GameSettings;
import com.team.tetris.settings.VersusKeyBindings;
import com.team.tetris.ui.theme.PressFeedback;
import com.team.tetris.ui.theme.ThemedDialog;
import com.team.tetris.ui.theme.UiColors;
import com.team.tetris.ui.theme.UiFonts;
import com.team.tetris.ui.theme.UiPainter;

/**
 * 두 보드를 바깥쪽에, 플레이어 정보(NEXT·SCORE·LEVEL·공격 대기열)를 안쪽에, 가운데에 VS를 둔다.
 * 보드 그리기와 1인 모드 연출은 플레이어마다 {@link BoardView}가 맡고, 이 화면은 공격 연출과 일시정지·결과를 맡는다.
 *
 * 공격 연출 (design-spec §3.2, 컴포넌트 시트 "공격 이펙트", VersusAttack 목업):
 * ① 공격 발생: 공격자 보드 테두리가 플레이어색으로 한 번 글로우 펄스.
 * ② 발사: 회색 뭉치(>_<)가 꼬리 잔상 3개를 달고 공격자가 지운 줄에서 상대 대기열로 날아감.
 * ③ 적재: 상대 대기열의 새 줄이 노랑으로 2회 깜빡인 뒤 회색으로 확정.
 * ④ 삽입: 대기열이 보드에 들어갈 때 회색 뭉치가 대기열에서 보드 맨 아래로 대각선 낙하, 삽입 구역은 accent 점선 + 7% 틴트.
 * 연출이 있는 동안에만 타이머가 돈다(상시 루프 없음).
 */
public final class VersusScreen extends JPanel implements Screen {
    // 창 크기 L(1280px) 기준 수치. 그릴 때 배율을 곱한다.
    static final int BASE_HEADER = 64;
    static final int BASE_FOOTER = 44;

    /** 승패가 난 뒤 두 보드를 멈춘 채 보여 주는 시간 (인계서 4단계 기본안). */
    private static final int END_FREEZE_MS = 1_000;
    private static final Color DIMMED_OVERLAY = new Color(0x14, 0x0F, 0x28, 184);
    private static final String[] PAUSE_ITEMS = {"계속하기", "대전 방식 선택", "메인 화면으로 나가기"};

    private final ScreenRouter router;
    private final VersusMatch match;
    private final VersusInput input;
    private final Supplier<GameSettings> settings;
    private final Consumer<MatchResult> showResult;
    private final VersusBoards boards;
    private final Map<Player, PlayerSide> listeners = new EnumMap<>(Player.class);
    private final VersusListener matchListener = new MatchEvents();
    private final KeyEventDispatcher dispatcher = this::dispatch;
    private final WindowAdapter focusLoss = new WindowAdapter() {
        @Override public void windowLostFocus(WindowEvent event) { input.releaseAll(); }
    };
    private final Timer endTimer;
    private final PressFeedback pausePress = new PressFeedback(this);

    private VersusMode pendingMode;
    /** 시간제한 방식의 제한 시간(초). 방식 선택 화면에서 고르고, "다시 대전"은 같은 시간으로 한다. */
    private int timeLimitSeconds = GameConstants.VERSUS_TIME_LIMIT_SECONDS;
    private VersusMode mode = VersusMode.NORMAL;
    private int remainingSeconds;
    private boolean pauseMenuOpen;
    private int pauseSelected;
    private boolean dialogOpen;
    private MatchResult ended;
    private int previousWidth;
    private int previousHeight;

    /**
     * @param showResult 승패가 난 뒤 결과를 받아 승자 화면으로 넘어가는 동작
     */
    public VersusScreen(ScreenRouter router, VersusMatch match, VersusInput input, Supplier<GameSettings> settings,
                        ItemRegistry items, Consumer<MatchResult> showResult) {
        this.router = Objects.requireNonNull(router, "router");
        this.match = Objects.requireNonNull(match, "match");
        this.input = Objects.requireNonNull(input, "input");
        this.settings = Objects.requireNonNull(settings, "settings");
        this.showResult = Objects.requireNonNull(showResult, "showResult");
        this.boards = new VersusBoards(this, items);
        for (Player player : Player.values()) {
            listeners.put(player, new PlayerSide(player));
        }
        endTimer = new Timer(END_FREEZE_MS, event -> {
            if (ended != null) showResult.accept(ended);
        });
        endTimer.setRepeats(false);
        setBackground(UiColors.BACKGROUND);
        setOpaque(true);
        setFocusable(true);
    }

    /** 다음에 이 화면이 보일 때 시작할 방식. 시간제한은 마지막으로 고른 시간이다 ("다시 대전"). */
    public void prepare(VersusMode chosen) {
        pendingMode = Objects.requireNonNull(chosen, "mode");
    }

    /**
     * 다음에 이 화면이 보일 때 시작할 방식과 시간제한 방식의 제한 시간. 방식 선택 화면이 화면을 넘기기 전에 부른다.
     *
     * @param seconds 제한 시간(초). 시간제한이 아닌 방식에서는 기억만 해 둔다
     */
    public void prepare(VersusMode chosen, int seconds) {
        if (seconds <= 0) throw new IllegalArgumentException("seconds must be positive");
        timeLimitSeconds = seconds;
        prepare(chosen);
    }

    @Override
    public JPanel getPanel() {
        return this;
    }

    @Override
    public void onShow() {
        previousWidth = router.getCurrentWidth();
        previousHeight = router.getCurrentHeight();
        int[] size = windowSize(settings.get().screenSize());
        router.updateWindowSize(size[0], size[1]);
        // 확인용 화면도 같은 대전을 쓰므로, 이 화면이 보일 때마다 알림을 다시 받아 온다.
        match.setListener(matchListener);
        for (Player player : Player.values()) match.setPlayerListener(player, listeners.get(player));
        ColorScheme colors = settings.get().isColorBlindModeEnabled() ? ColorScheme.colorBlind() : ColorScheme.standard();
        boards.setColorScheme(colors);
        KeyboardFocusManager.getCurrentKeyboardFocusManager().addKeyEventDispatcher(dispatcher);
        router.addWindowFocusListener(focusLoss);
        if (pendingMode != null) {
            start(pendingMode);
            pendingMode = null;
        }
        requestFocusInWindow();
        repaint();
    }

    @Override
    public void onHide() {
        KeyboardFocusManager.getCurrentKeyboardFocusManager().removeKeyEventDispatcher(dispatcher);
        router.removeWindowFocusListener(focusLoss);
        input.releaseAll();
        if (match.state() == VersusMatch.State.RUNNING) match.pause();
        boards.stopEffects();
        endTimer.stop();
        pausePress.cancel();
        router.updateWindowSize(previousWidth, previousHeight);
    }

    /** 설정의 1인 창 크기에 맞춘 대전 창 크기 (인계서 2단계 표). */
    static int[] windowSize(GameSettings.ScreenSize size) {
        return switch (size) {
            case SMALL -> new int[]{1024, 640};
            case MEDIUM -> new int[]{1152, 720};
            case LARGE -> new int[]{1280, 800};
        };
    }

    private void start(VersusMode chosen) {
        mode = chosen;
        ended = null;
        remainingSeconds = chosen == VersusMode.TIME_LIMIT ? timeLimitSeconds : 0;
        pauseMenuOpen = false;
        endTimer.stop();
        boards.reset();
        input.releaseAll();
        match.start(chosen, timeLimitSeconds);
        repaint();
    }

    // ---- 키

    private boolean dispatch(KeyEvent event) {
        // 확인 대화상자가 떠 있는 동안에는 키를 대화상자에 맡긴다.
        if (dialogOpen) return false;
        return switch (event.getID()) {
            case KeyEvent.KEY_PRESSED -> keyPressed(event.getKeyCode(), event.getKeyLocation());
            case KeyEvent.KEY_RELEASED -> {
                input.keyReleased(event.getKeyCode(), event.getKeyLocation());
                yield false;
            }
            default -> false;
        };
    }

    /** 창에서 받은 KEY_PRESSED 하나. 대전 키를 먼저 보고, 남은 키(일시정지·나가기·메뉴)를 이 화면이 처리한다. */
    boolean keyPressed(int keyCode, int location) {
        if (ended != null) return true;
        GameSettings current = settings.get();
        int pauseKey = current.keyBindings().keyFor(GameAction.PAUSE);
        int quitKey = current.keyBindings().keyFor(GameAction.QUIT);
        if (pauseMenuOpen) return pauseMenuKey(keyCode, pauseKey, quitKey);
        if (input.keyPressed(keyCode, location)) return true;
        if (HeldKeys.isAutoRepeat()) return false;
        if (keyCode == pauseKey) {
            if (match.pause()) openPauseMenu();
            return true;
        }
        if (keyCode == quitKey) {
            confirmQuit();
            return true;
        }
        return false;
    }

    private boolean pauseMenuKey(int keyCode, int pauseKey, int quitKey) {
        if (pausePress.isPressing() || HeldKeys.isAutoRepeat()) return true;
        if (keyCode == pauseKey) {
            resumeFromPause();
        } else if (keyCode == quitKey) {
            confirmQuit();
        } else if (keyCode == KeyEvent.VK_UP) {
            pauseSelected = (pauseSelected - 1 + PAUSE_ITEMS.length) % PAUSE_ITEMS.length;
            repaint();
        } else if (keyCode == KeyEvent.VK_DOWN) {
            pauseSelected = (pauseSelected + 1) % PAUSE_ITEMS.length;
            repaint();
        } else if (keyCode == KeyEvent.VK_ENTER || keyCode == KeyEvent.VK_SPACE) {
            pausePress.press(pauseSelected, this::runPauseItem);
        }
        return true;
    }

    private void openPauseMenu() {
        input.releaseAll();
        pauseMenuOpen = true;
        pauseSelected = 0;
        repaint();
    }

    private void resumeFromPause() {
        pauseMenuOpen = false;
        match.resume();
        repaint();
    }

    private void runPauseItem() {
        switch (pauseSelected) {
            case 0 -> resumeFromPause();
            case 1 -> leaveTo(ScreenRouter.BATTLE_SELECT);
            default -> leaveTo(ScreenRouter.MAIN_MENU);
        }
    }

    private void leaveTo(String screen) {
        pauseMenuOpen = false;
        match.stop();
        router.showScreen(screen);
    }

    /** 1인 모드와 같은 문구·기본 선택("계속하기")의 확인 대화상자 (인계서 5단계). */
    private void confirmQuit() {
        input.releaseAll();
        boolean pausedHere = match.pause();
        dialogOpen = true;
        int choice;
        try {
            choice = ThemedDialog.showOptions(this, "메인 화면으로 나가기",
                    "메인 화면으로 나갈까요?\n현재 게임 내용은 저장되지 않고 초기화됩니다.",
                    ThemedDialog.Tone.WARNING, 1, "나가기", "계속하기");
        } finally {
            dialogOpen = false;
        }
        if (choice == 0) {
            leaveTo(ScreenRouter.MAIN_MENU);
        } else if (pausedHere && !pauseMenuOpen) {
            match.resume();
        }
    }

    // ---- 공격 연출

    // ---- 그리기

    @Override
    protected void paintComponent(Graphics graphics) {
        super.paintComponent(graphics);
        Graphics2D g2d = (Graphics2D) graphics.create();
        try {
            UiPainter.prepare(g2d);
            int width = getWidth();
            int height = getHeight();
            UiPainter.fillBackground(g2d, width, height);
            float scale = boards.layout(width, height);
            drawHeader(g2d, width, scale);
            boards.paint(g2d);
            drawFooter(g2d, width, height, scale);
            if (ended != null) drawEndBanner(g2d, width, height, scale);
            if (pauseMenuOpen) drawPauseMenu(g2d, width, height, scale);
        } finally {
            g2d.dispose();
        }
    }


    private void drawHeader(Graphics2D g2d, int width, float scale) {
        int header = Math.round(BASE_HEADER * scale);
        int margin = Math.round(32 * scale);
        g2d.setFont(UiFonts.display(Math.round(28 * scale)));
        g2d.setColor(UiColors.TEXT_PRIMARY);
        FontMetrics metrics = g2d.getFontMetrics();
        int baseline = UiPainter.centeredBaseline(metrics, 0, header);
        UiPainter.drawText(g2d, "대전 모드 — " + modeName(mode), margin, baseline);
        if (mode == VersusMode.TIME_LIMIT) {
            String time = String.format(Locale.US, "%02d:%02d", remainingSeconds / 60, remainingSeconds % 60);
            g2d.setFont(UiFonts.mono(Math.round(28 * scale)));
            g2d.setColor(remainingSeconds <= 10 ? UiColors.PLAYER2 : UiColors.ACCENT);
            FontMetrics timeMetrics = g2d.getFontMetrics();
            g2d.drawString(time, width - margin - timeMetrics.stringWidth(time),
                    UiPainter.centeredBaseline(timeMetrics, 0, header));
        }
    }

    private void drawFooter(Graphics2D g2d, int width, int height, float scale) {
        GameSettings current = settings.get();
        int baseline = height - Math.round(BASE_FOOTER * scale / 2) + Math.round(5 * scale);
        int margin = Math.round(32 * scale);
        g2d.setFont(UiFonts.body(Math.round(13 * scale)));
        g2d.setColor(UiColors.PLAYER1);
        UiPainter.drawText(g2d, "P1: " + playerKeys(current, Player.P1), margin, baseline);
        String p2 = "P2: " + playerKeys(current, Player.P2);
        g2d.setColor(UiColors.PLAYER2);
        UiPainter.drawText(g2d, p2, width - margin - UiPainter.textWidth(g2d.getFontMetrics(), p2), baseline);
        g2d.setColor(UiColors.withAlpha(UiColors.TEXT_MUTED, 0.85f));
        String shared = keyName(current.keyBindings().keyFor(GameAction.PAUSE)) + " 일시정지  ·  "
                + keyName(current.keyBindings().keyFor(GameAction.QUIT)) + " 나가기";
        UiPainter.drawCentered(g2d, shared, width / 2, baseline);
    }

    /** 키 안내 순서: 키보드 배치대로 회전·왼쪽·아래·오른쪽 (목업 "W A S D", "↑ ← ↓ →"). */
    private static final List<GameAction> KEY_HINT_ORDER = List.of(
            GameAction.ROTATE_CLOCKWISE, GameAction.MOVE_LEFT, GameAction.SOFT_DROP, GameAction.MOVE_RIGHT);

    /** "W A S D · 하드드롭 Left Shift"처럼 이동·회전 키 넷과 하드드롭 키를 현재 설정값으로 쓴다. */
    static String playerKeys(GameSettings settings, Player player) {
        VersusKeyBindings keys = settings.versusKeys();
        StringJoiner moves = new StringJoiner(" ");
        for (GameAction action : KEY_HINT_ORDER) {
            moves.add(versusKeyName(keys.keyFor(player, action)));
        }
        return moves + "  ·  하드드롭 " + versusKeyName(keys.keyFor(player, GameAction.HARD_DROP));
    }

    /** 키 코드 하나의 짧은 이름: 화살표는 기호로, 나머지는 Esc·Shift·Enter처럼 짧은 영어로 쓴다. */
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

    /**
     * 대전 키({@link SidedKey}) 이름. 쪽을 정한 키는 "Left Shift"·"Right Shift"처럼 영어로 쓴다.
     * 다른 키 이름(Space·Enter·Esc)과 같은 영어 표기로 맞춘다.
     */
    static String versusKeyName(int key) {
        String name = keyName(SidedKey.keyCode(key));
        return switch (SidedKey.side(key)) {
            case SidedKey.LEFT -> "Left " + name;
            case SidedKey.RIGHT -> "Right " + name;
            default -> name;
        };
    }

    /** 승패가 난 뒤 결과 화면으로 넘어가기 전 잠깐 띄우는 승리 배너 (컴포넌트 시트 "토스트 / 승리 배너"). */
    private void drawEndBanner(Graphics2D g2d, int width, int height, float scale) {
        g2d.setColor(UiColors.withAlpha(UiColors.BACKGROUND, 0.45f));
        g2d.fillRect(0, 0, width, height);
        Player winner = ended.winner();
        Color color = winner == null ? UiColors.ACCENT : playerColor(winner);
        String title = winner == null ? "무승부" : winner.name() + " 승리!";
        String subtitle = ended.reason() == MatchResult.Reason.TIME_UP ? "시간 종료" : null;
        int boxWidth = Math.round(320 * scale);
        int boxHeight = Math.round(96 * scale);
        int boxX = (width - boxWidth) / 2;
        int boxY = boards.vsCenterY() - boxHeight / 2;
        UiPainter.drawPanel(g2d, boxX, boxY, boxWidth, boxHeight, Math.round(18 * scale), UiColors.PANEL, color, 3f);
        g2d.setFont(UiFonts.display(Math.round(34 * scale)));
        g2d.setColor(color);
        // 사유가 없으면 제목을 배너 가운데에 둔다.
        UiPainter.drawCentered(g2d, title, width / 2,
                boxY + Math.round((subtitle == null ? 60 : 50) * scale));
        if (subtitle != null) {
            g2d.setFont(UiFonts.body(Math.round(13 * scale)));
            g2d.setColor(UiColors.TEXT_SECONDARY);
            UiPainter.drawCentered(g2d, subtitle, width / 2, boxY + boxHeight - Math.round(16 * scale));
        }
    }

    /** 일시정지: 1인 모드 일시정지 화면과 같은 카드 (반투명 막 + 노란 테두리 카드 + 알약 버튼). */
    private void drawPauseMenu(Graphics2D g2d, int width, int height, float scale) {
        g2d.setColor(DIMMED_OVERLAY);
        g2d.fillRect(0, 0, width, height);
        int buttonHeight = Math.round(46 * scale);
        int buttonGap = Math.round(12 * scale);
        int padding = Math.round(40 * scale);
        int titleSpace = Math.round(86 * scale);
        int footerSpace = Math.round(52 * scale);
        int cardWidth = Math.round(420 * scale);
        int cardHeight = titleSpace + PAUSE_ITEMS.length * buttonHeight + (PAUSE_ITEMS.length - 1) * buttonGap + footerSpace;
        int cardX = (width - cardWidth) / 2;
        int cardY = (height - cardHeight) / 2;
        int radius = Math.round(22 * scale);
        g2d.setColor(new Color(0, 0, 0, 70));
        g2d.fillRoundRect(cardX - 6, cardY + 8, cardWidth + 12, cardHeight + 12, radius * 2 + 12, radius * 2 + 12);
        UiPainter.drawPanel(g2d, cardX, cardY, cardWidth, cardHeight, radius, UiColors.PANEL, UiColors.ACCENT, 3f);
        g2d.setFont(UiFonts.display(Math.round(30 * scale)));
        g2d.setColor(UiColors.ACCENT);
        UiPainter.drawCentered(g2d, "일시정지", width / 2, cardY + Math.round(58 * scale));
        int buttonY = cardY + titleSpace;
        for (int i = 0; i < PAUSE_ITEMS.length; i++) {
            UiPainter.drawPillButton(g2d, cardX + padding, buttonY, cardWidth - padding * 2, buttonHeight,
                    PAUSE_ITEMS[i], pausePress.stateOf(i, pauseSelected), false, Math.round(18 * scale));
            buttonY += buttonHeight + buttonGap;
        }
        GameSettings current = settings.get();
        String help = keyName(current.keyBindings().keyFor(GameAction.PAUSE)) + " 키로 바로 재개  ·  "
                + keyName(current.keyBindings().keyFor(GameAction.QUIT)) + " 메인 화면으로";
        g2d.setFont(UiFonts.body(Math.round(12 * scale)));
        g2d.setColor(UiColors.withAlpha(UiColors.TEXT_MUTED, 0.8f));
        UiPainter.drawCentered(g2d, help, width / 2, cardY + cardHeight - Math.round(22 * scale));
    }

    static Color playerColor(Player player) {
        return player == Player.P1 ? UiColors.PLAYER1 : UiColors.PLAYER2;
    }

    static String modeName(VersusMode mode) {
        return switch (mode) {
            case NORMAL -> "일반";
            case ITEM -> "아이템";
            case TIME_LIMIT -> "시간제한";
        };
    }

    // ---- 알림

    private final class MatchEvents implements VersusListener {
        @Override public void onQueueChanged(Player player, List<GarbageLine> lines) {
            boards.queueChanged(player, lines);
        }

        @Override public void onLevelChanged(Player player, int level) {
            boards.side(player).level = level;
            boards.repaintSide(player);
        }

        @Override public void onTimeChanged(int seconds) {
            remainingSeconds = seconds;
            repaint();
        }

        @Override public void onPauseChanged(boolean paused) {
            if (!paused) pauseMenuOpen = false;
            repaint();
        }

        @Override public void onMatchEnded(MatchResult result) {
            ended = result;
            pauseMenuOpen = false;
            input.releaseAll();
            endTimer.restart();
            repaint();
        }
    }

    /** 플레이어 한 명의 엔진 알림을 보드 영역의 그 플레이어 칸에 옮긴다. */
    private final class PlayerSide implements GameEventListener {
        private final Player player;

        PlayerSide(Player player) {
            this.player = player;
        }

        private VersusBoards.Side side() {
            return boards.side(player);
        }

        @Override public void onBoardUpdated(BoardSnapshot snapshot) { side().board.onBoardUpdated(snapshot); }

        @Override public void onLandingUpdated(BoardSnapshot falling, BoardSnapshot landing) {
            side().board.onLandingUpdated(falling, landing);
        }

        @Override public void onLinesCleared(List<Integer> rows, BoardSnapshot boardBeforeClear) {
            side().linesCleared(rows);
            side().board.onLinesCleared(rows, boardBeforeClear);
        }

        @Override public void onScoreChanged(int updated) {
            side().score = updated;
            boards.repaintSide(player);
        }

        @Override public void onNextBlocksChanged(int[] blockTypes) {
            side().next = blockTypes.clone();
            boards.repaintSide(player);
        }

        @Override public void onPauseStateChanged(boolean paused) { }

        @Override public void onFeverActivated(int seconds) { side().board.onFeverActivated(seconds); }

        @Override public void onSlowDownActivated(int previousLevel, int currentLevel) {
            side().board.onSlowDownActivated(previousLevel, currentLevel);
        }

        @Override public void onHardDropped(BoardSnapshot before, BoardSnapshot after) {
            side().board.onHardDropped(before, after);
        }

        @Override public void onBombExploded(CellPosition center, List<CellPosition> blastCells) {
            side().board.onBombExploded(center, blastCells);
        }

        @Override public void onAnvilCrushed(List<CellPosition> crushedCells) { side().board.onAnvilCrushed(crushedCells); }

        @Override public void onGameOver(int finalScore) {
            // 승패는 VersusListener.onMatchEnded로 받는다 (인계서 4.3).
        }
    }

    /** 마지막으로 그린 배치에서 그 플레이어 공격 대기열 격자의 위치 (테스트용). */
    Rectangle queueGridBounds(Player player) {
        return boards.queueGridBounds(player);
    }
}

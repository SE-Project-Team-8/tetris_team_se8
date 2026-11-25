/*
 * 로컬 2인 대전 화면 (docs/battle/ui-handoff.md 2절, 목업 VersusScreen·VersusAttack).
 */
package com.team.tetris.ui;

import java.awt.AlphaComposite;
import java.awt.BasicStroke;
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
import java.awt.font.TextAttribute;
import java.util.ArrayList;
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
import com.team.tetris.ui.theme.CellPainter;
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
    private static final float BASE_WIDTH = 1280f;
    private static final int BASE_HEADER = 64;
    private static final int BASE_FOOTER = 44;
    private static final int BASE_FRAME_PADDING = 10;
    private static final int BASE_GAP = 16;
    private static final int BASE_INFO_WIDTH = 150;
    private static final int BASE_VS_SPACE = 150;

    private static final int ATTACK_TICK_MS = 16;
    /** ① 공격자 보드 글로우 펄스. */
    private static final int GLOW_MS = 320;
    /** ②·④ 회색 뭉치가 날아가는 시간 (컴포넌트 시트: 약 300ms). */
    private static final int COMET_MS = 320;
    /** ③ 대기열 새 줄 노랑 깜빡임 2회 (한 번 켜짐·꺼짐 120ms씩). */
    private static final int QUEUE_FLASH_MS = 480;
    /** ④ 삽입 구역 하이라이트. */
    private static final int INSERT_ZONE_MS = 560;
    /** 승패가 난 뒤 두 보드를 멈춘 채 보여 주는 시간 (인계서 4단계 기본안). */
    private static final int END_FREEZE_MS = 1_000;
    /** 꼬리 잔상 불투명도 (컴포넌트 시트: 50→12%). */
    private static final float[] TRAIL_ALPHAS = {0.50f, 0.28f, 0.12f};
    private static final Color DIMMED_OVERLAY = new Color(0x14, 0x0F, 0x28, 184);
    private static final String[] PAUSE_ITEMS = {"계속하기", "대전 방식 선택", "메인 화면으로 나가기"};

    private enum EffectKind { GLOW, SEND, QUEUE_FLASH, INSERT }

    /**
     * 공격 연출 하나.
     *
     * @param player GLOW는 공격자, 나머지는 공격을 받은(대기열·보드) 플레이어
     * @param rows 줄 수
     * @param fromIndex QUEUE_FLASH에서 깜빡일 대기열 줄 범위의 시작(포함)
     * @param fromRow SEND가 출발하는 공격자 보드의 행
     */
    private record AttackEffect(EffectKind kind, Player player, int rows, int fromIndex, float fromRow,
                                long startMs, int durationMs) {
        float progress(long now) {
            return Math.max(0f, Math.min(1f, (float) (now - startMs) / durationMs));
        }

        boolean started(long now) {
            return now >= startMs;
        }

        boolean finished(long now) {
            return now >= startMs + durationMs;
        }
    }

    /** 한 번 그릴 때 정한 배치. 연출 위치 계산과 플레이어 영역 다시 그리기에 쓴다. */
    private record Layout(float scale, int pitch, int framePadding, int frameY, int frameWidth, int frameHeight,
                          Map<Player, Integer> frameX, Map<Player, Integer> infoX, int infoWidth,
                          Map<Player, Rectangle> queueGrid, int vsCenterX, int vsCenterY) {
        int boardX(Player player) {
            return frameX.get(player) + framePadding;
        }

        int boardY() {
            return frameY + framePadding;
        }

        Rectangle region(Player player) {
            int left = Math.min(frameX.get(player), infoX.get(player));
            int right = Math.max(frameX.get(player) + frameWidth, infoX.get(player) + infoWidth);
            int glow = Math.round(16 * scale);
            return new Rectangle(left - glow, frameY - glow, right - left + glow * 2, frameHeight + glow * 2);
        }
    }

    private final ScreenRouter router;
    private final VersusMatch match;
    private final VersusInput input;
    private final Supplier<GameSettings> settings;
    private final Consumer<MatchResult> showResult;
    private final Map<Player, PlayerSide> sides = new EnumMap<>(Player.class);
    private final VersusListener matchListener = new MatchEvents();
    private final KeyEventDispatcher dispatcher = this::dispatch;
    private final WindowAdapter focusLoss = new WindowAdapter() {
        @Override public void windowLostFocus(WindowEvent event) { input.releaseAll(); }
    };
    private final List<AttackEffect> effects = new ArrayList<>();
    private final Timer attackTimer = new Timer(ATTACK_TICK_MS, event -> advanceEffects());
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
    private Layout layout;
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
        Objects.requireNonNull(items, "items");
        for (Player player : Player.values()) {
            sides.put(player, new PlayerSide(player, items));
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
        for (Player player : Player.values()) match.setPlayerListener(player, sides.get(player));
        ColorScheme colors = settings.get().isColorBlindModeEnabled() ? ColorScheme.colorBlind() : ColorScheme.standard();
        sides.values().forEach(side -> side.board.setColorScheme(colors));
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
        attackTimer.stop();
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
        effects.clear();
        attackTimer.stop();
        endTimer.stop();
        sides.values().forEach(PlayerSide::reset);
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

    private void addEffect(AttackEffect effect) {
        effects.add(effect);
        if (!attackTimer.isRunning()) attackTimer.start();
        repaint();
    }

    private void advanceEffects() {
        long now = now();
        effects.removeIf(effect -> effect.finished(now));
        if (effects.isEmpty()) attackTimer.stop();
        repaint();
    }

    private static long now() {
        return System.nanoTime() / 1_000_000L;
    }

    /** 대기열이 바뀐 순간: 늘었으면 공격이 온 것(①②③), 비었으면 보드에 들어간 것(④). */
    private void onQueueChanged(Player player, List<GarbageLine> lines) {
        PlayerSide side = sides.get(player);
        int before = side.queue.size();
        side.queue = List.copyOf(lines);
        int after = side.queue.size();
        long now = now();
        if (after > before) {
            Player attacker = player.opponent();
            addEffect(new AttackEffect(EffectKind.GLOW, attacker, after - before, 0, 0f, now, GLOW_MS));
            addEffect(new AttackEffect(EffectKind.SEND, player, after - before, 0,
                    sides.get(attacker).lastClearedRow, now, COMET_MS));
            // 회색 뭉치가 대기열에 닿는 순간부터 새 줄이 깜빡인다. 그 전에는 새 줄을 비워 둔다.
            addEffect(new AttackEffect(EffectKind.QUEUE_FLASH, player, after - before, before, 0f,
                    now + COMET_MS, QUEUE_FLASH_MS));
        } else if (after == 0 && before > 0) {
            addEffect(new AttackEffect(EffectKind.INSERT, player, before, 0, 0f, now, INSERT_ZONE_MS));
        }
        repaint();
    }

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
            layout = computeLayout(width, height);
            long now = now();
            float scale = layout.scale();

            drawHeader(g2d, width, scale);
            for (Player player : Player.values()) {
                drawFrameAndBoard(g2d, player, now);
                drawInfoColumn(g2d, player, now);
            }
            drawVs(g2d, scale);
            drawAttackEffects(g2d, now);
            drawFooter(g2d, width, height, scale);
            if (ended != null) drawEndBanner(g2d, width, height, scale);
            if (pauseMenuOpen) drawPauseMenu(g2d, width, height, scale);
        } finally {
            g2d.dispose();
        }
    }

    private Layout computeLayout(int width, int height) {
        float scale = Math.max(0.5f, width / BASE_WIDTH);
        int header = Math.round(BASE_HEADER * scale);
        int footer = Math.round(BASE_FOOTER * scale);
        int padding = Math.round(BASE_FRAME_PADDING * scale);
        int gap = Math.round(BASE_GAP * scale);
        int infoWidth = Math.round(BASE_INFO_WIDTH * scale);
        int vsSpace = Math.round(BASE_VS_SPACE * scale);
        int margin = Math.round(32 * scale);
        int pitch = Math.max(8, Math.min(
                (height - header - footer - padding * 2) / GameConstants.BOARD_ROWS,
                ((width - margin * 2 - gap * 2 - infoWidth * 2 - vsSpace) / 2 - padding * 2) / GameConstants.BOARD_COLS));
        int frameWidth = pitch * GameConstants.BOARD_COLS + padding * 2;
        int frameHeight = pitch * GameConstants.BOARD_ROWS + padding * 2;
        int total = frameWidth * 2 + gap * 2 + infoWidth * 2 + vsSpace;
        int left = (width - total) / 2;
        Map<Player, Integer> frameX = new EnumMap<>(Player.class);
        Map<Player, Integer> infoX = new EnumMap<>(Player.class);
        frameX.put(Player.P1, left);
        infoX.put(Player.P1, left + frameWidth + gap);
        infoX.put(Player.P2, left + frameWidth + gap + infoWidth + vsSpace);
        frameX.put(Player.P2, left + total - frameWidth);
        int frameY = header;
        Map<Player, Rectangle> queueGrid = new EnumMap<>(Player.class);
        for (Player player : Player.values()) queueGrid.put(player, queueGridBounds(infoX.get(player), frameY, infoWidth, scale));
        return new Layout(scale, pitch, padding, frameY, frameWidth, frameHeight, frameX, infoX, infoWidth, queueGrid,
                width / 2, frameY + frameHeight / 2);
    }

    // 정보 열 세로 배치 (L 기준 px)
    private static final int INFO_LABEL = 30;
    private static final int INFO_NEXT = 86;
    private static final int INFO_STAT = 78;
    private static final int INFO_GAP = 12;
    private static final int QUEUE_LABEL = 32;
    private static final int QUEUE_PADDING = 14;

    private static int queuePanelTop(int frameY, float scale) {
        return frameY + Math.round((INFO_LABEL + INFO_NEXT + INFO_STAT * 2 + INFO_GAP * 3) * scale);
    }

    private static Rectangle queueGridBounds(int infoX, int frameY, int infoWidth, float scale) {
        int padding = Math.round(QUEUE_PADDING * scale);
        int cell = (infoWidth - padding * 2) / GarbageLine.COLS;
        int gridWidth = cell * GarbageLine.COLS;
        int x = infoX + (infoWidth - gridWidth) / 2;
        int y = queuePanelTop(frameY, scale) + Math.round(QUEUE_LABEL * scale);
        return new Rectangle(x, y, gridWidth, cell * GameConstants.ATTACK_QUEUE_MAX_LINES);
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

    private void drawFrameAndBoard(Graphics2D g2d, Player player, long now) {
        PlayerSide side = sides.get(player);
        float scale = layout.scale();
        int x = layout.frameX().get(player);
        int y = layout.frameY();
        int radius = Math.round(16 * scale);
        Color color = playerColor(player);
        // ① 공격 발생: 공격자 보드 테두리를 플레이어색으로 한 번 글로우 펄스 (테두리 +3px, 바깥 빛 번짐).
        for (AttackEffect effect : effects) {
            if (effect.kind() != EffectKind.GLOW || effect.player() != player || !effect.started(now)) continue;
            float strength = 1f - effect.progress(now);
            for (int ring = 3; ring >= 1; ring--) {
                int spread = Math.round(ring * 5 * scale);
                UiPainter.strokeRoundRect(g2d, x - spread, y - spread, layout.frameWidth() + spread * 2,
                        layout.frameHeight() + spread * 2, radius + spread,
                        UiColors.withAlpha(color, 0.22f * strength * (4 - ring) / 3f), Math.max(2f, 4 * scale));
            }
        }
        float border = 3f + 3f * glowStrength(player, now);
        int shake = side.board.shakeOffset(scale);
        Graphics2D board = (Graphics2D) g2d.create();
        try {
            board.translate(0, shake);
            UiPainter.drawPanel(board, x, y, layout.frameWidth(), layout.frameHeight(), radius,
                    UiColors.BOARD, color, border);
            side.board.paintBoard(board, layout.boardX(player), layout.boardY(), layout.pitch());
        } finally {
            board.dispose();
        }
        int boardWidth = layout.pitch() * GameConstants.BOARD_COLS;
        int boardHeight = layout.pitch() * GameConstants.BOARD_ROWS;
        side.board.paintToast(g2d, layout.boardX(player), layout.boardY(), boardWidth, boardHeight, scale);
    }

    private float glowStrength(Player player, long now) {
        float strength = 0f;
        for (AttackEffect effect : effects) {
            if (effect.kind() == EffectKind.GLOW && effect.player() == player && effect.started(now)) {
                strength = Math.max(strength, 1f - effect.progress(now));
            }
        }
        return strength;
    }

    private void drawInfoColumn(Graphics2D g2d, Player player, long now) {
        PlayerSide side = sides.get(player);
        float scale = layout.scale();
        int x = layout.infoX().get(player);
        int width = layout.infoWidth();
        int y = layout.frameY();
        int gap = Math.round(INFO_GAP * scale);
        int radius = Math.round(12 * scale);
        Color color = playerColor(player);

        // 플레이어 이름: P1은 왼쪽, P2는 오른쪽 정렬 (보드 쪽).
        g2d.setFont(UiFonts.display(Math.round(22 * scale)));
        g2d.setColor(color);
        FontMetrics nameMetrics = g2d.getFontMetrics();
        String name = player.name();
        int nameX = player == Player.P1 ? x : x + width - nameMetrics.stringWidth(name);
        g2d.drawString(name, nameX, y + Math.round(20 * scale));
        y += Math.round(INFO_LABEL * scale);

        int nextHeight = Math.round(INFO_NEXT * scale);
        UiPainter.drawPanel(g2d, x, y, width, nextHeight, radius);
        drawPanelLabel(g2d, "NEXT", x, y, width, scale);
        if (side.next.length > 0) {
            int labelSpace = Math.round(28 * scale);
            side.board.paintPreview(g2d, side.next[0], x, y + labelSpace, width, nextHeight - labelSpace,
                    Math.round(18 * scale));
        }
        y += nextHeight + gap;

        int statHeight = Math.round(INFO_STAT * scale);
        drawStat(g2d, "SCORE", String.format(Locale.US, "%,d", side.score), UiColors.CREAM, x, y, width, statHeight, scale);
        y += statHeight + gap;
        drawStat(g2d, "LEVEL", String.valueOf(side.level), UiColors.LEVEL, x, y, width, statHeight, scale);
        y += statHeight + gap;

        drawQueuePanel(g2d, player, x, y, width, scale, now);
    }

    private static void drawStat(Graphics2D g2d, String label, String value, Color valueColor,
                                 int x, int y, int width, int height, float scale) {
        UiPainter.drawPanel(g2d, x, y, width, height, Math.round(12 * scale));
        drawPanelLabel(g2d, label, x, y, width, scale);
        g2d.setFont(UiFonts.mono(Math.round(26 * scale)));
        g2d.setColor(valueColor);
        UiPainter.drawCentered(g2d, value, x + width / 2, y + height - Math.round(16 * scale));
    }

    private static void drawPanelLabel(Graphics2D g2d, String label, int x, int y, int width, float scale) {
        g2d.setFont(UiFonts.body(Math.round(11 * scale)).deriveFont(Map.of(TextAttribute.TRACKING, 0.12f)));
        g2d.setColor(UiColors.TEXT_MUTED);
        UiPainter.drawCentered(g2d, label, x + width / 2, y + Math.round(22 * scale));
    }

    /**
     * 공격 대기열: 10줄×10칸 격자에 대기 중인 줄을 빈 칸이 보이는 모양 그대로 회색으로 그린다 (위가 먼저 온 줄).
     * 줄이 있으면 패널 테두리와 제목을 플레이어색으로 바꿔 "다음 블럭 고정 때 들어온다"는 것을 알린다.
     */
    private void drawQueuePanel(Graphics2D g2d, Player player, int x, int y, int width, float scale, long now) {
        PlayerSide side = sides.get(player);
        Rectangle grid = layout.queueGrid().get(player);
        int padding = Math.round(QUEUE_PADDING * scale);
        int height = grid.y + grid.height + padding - y;
        boolean waiting = !side.queue.isEmpty();
        Color color = playerColor(player);
        UiPainter.drawPanel(g2d, x, y, width, height, Math.round(12 * scale), UiColors.PANEL,
                waiting ? color : UiColors.PANEL_BORDER, waiting ? 2.5f : 1f);
        g2d.setFont(UiFonts.body(Math.round(12 * scale)));
        g2d.setColor(waiting ? color : UiColors.TEXT_MUTED);
        UiPainter.drawCentered(g2d, "공격 대기열 " + side.queue.size() + "/" + GameConstants.ATTACK_QUEUE_MAX_LINES,
                x + width / 2, y + Math.round(22 * scale));

        int cell = grid.width / GarbageLine.COLS;
        int inset = Math.max(1, Math.round(cell * 0.1f));
        int arc = Math.max(2, Math.round(cell * 0.35f));
        for (int row = 0; row < GameConstants.ATTACK_QUEUE_MAX_LINES; row++) {
            GarbageLine line = row < side.queue.size() ? side.queue.get(row) : null;
            Color lineColor = UiColors.GARBAGE;
            if (line != null) {
                AttackEffect flash = flashFor(player, row);
                if (flash != null && !flash.started(now)) {
                    line = null;   // 회색 뭉치가 아직 날아오는 중
                } else if (flash != null && !flash.finished(now)) {
                    long phase = (now - flash.startMs()) / (QUEUE_FLASH_MS / 4);
                    if (phase % 2 == 0) lineColor = UiColors.ACCENT;
                }
            }
            for (int col = 0; col < GarbageLine.COLS; col++) {
                int cellX = grid.x + col * cell + inset;
                int cellY = grid.y + row * cell + inset;
                int size = cell - inset * 2;
                if (line != null && line.filled(col)) {
                    g2d.setColor(lineColor);
                } else {
                    g2d.setColor(UiColors.withAlpha(UiColors.BOARD, 0.9f));
                }
                g2d.fillRoundRect(cellX, cellY, size, size, arc, arc);
            }
        }
    }

    private AttackEffect flashFor(Player player, int row) {
        for (AttackEffect effect : effects) {
            if (effect.kind() == EffectKind.QUEUE_FLASH && effect.player() == player
                    && row >= effect.fromIndex() && row < effect.fromIndex() + effect.rows()) {
                return effect;
            }
        }
        return null;
    }

    private void drawVs(Graphics2D g2d, float scale) {
        int radius = Math.round(62 * scale);
        int cx = layout.vsCenterX();
        int cy = layout.vsCenterY();
        g2d.setColor(UiColors.darker(UiColors.PANEL, 0.35f));
        g2d.fillOval(cx - radius, cy - radius + Math.round(5 * scale), radius * 2, radius * 2);
        g2d.setColor(UiColors.PANEL);
        g2d.fillOval(cx - radius, cy - radius, radius * 2, radius * 2);
        g2d.setFont(UiFonts.display(Math.round(54 * scale)));
        FontMetrics metrics = g2d.getFontMetrics();
        int baseline = UiPainter.centeredBaseline(metrics, cy - radius, radius * 2);
        g2d.setColor(UiColors.darker(UiColors.ACCENT, 0.5f));
        UiPainter.drawCentered(g2d, "VS", cx + Math.round(2 * scale), baseline + Math.round(3 * scale));
        g2d.setColor(UiColors.ACCENT);
        UiPainter.drawCentered(g2d, "VS", cx, baseline);
    }

    private void drawAttackEffects(Graphics2D g2d, long now) {
        for (AttackEffect effect : effects) {
            if (!effect.started(now)) continue;
            switch (effect.kind()) {
                case SEND -> drawComet(g2d, sendStart(effect), queueCenter(effect.player()), effect, now, null);
                case INSERT -> {
                    drawInsertZone(g2d, effect, now);
                    drawComet(g2d, queueCenter(effect.player()), insertTarget(effect), effect, now,
                            effect.rows() + "줄 공격 삽입!");
                }
                default -> { }
            }
        }
    }

    private float[] sendStart(AttackEffect effect) {
        Player attacker = effect.player().opponent();
        int pitch = layout.pitch();
        return new float[]{layout.boardX(attacker) + pitch * GameConstants.BOARD_COLS / 2f,
                layout.boardY() + (effect.fromRow() + 0.5f) * pitch};
    }

    private float[] queueCenter(Player player) {
        Rectangle grid = layout.queueGrid().get(player);
        return new float[]{(float) grid.getCenterX(), (float) grid.getCenterY()};
    }

    private float[] insertTarget(AttackEffect effect) {
        int pitch = layout.pitch();
        return new float[]{layout.boardX(effect.player()) + pitch * GameConstants.BOARD_COLS / 2f,
                layout.boardY() + (GameConstants.BOARD_ROWS - effect.rows() / 2f) * pitch};
    }

    /**
     * 회색 뭉치(>_< 공격줄 칸)가 꼬리 잔상 3개를 달고 {@code from}에서 {@code to}로 날아간다.
     * 도착하면 사라진다. 삽입(④)은 점점 빨라지며 떨어지게 한다.
     */
    private void drawComet(Graphics2D g2d, float[] from, float[] to, AttackEffect effect, long now, String label) {
        float travel = Math.min(1f, (float) (now - effect.startMs()) / COMET_MS);
        if (travel >= 1f) return;
        boolean falling = effect.kind() == EffectKind.INSERT;
        int size = Math.round(layout.pitch() * 1.05f);
        Graphics2D comet = (Graphics2D) g2d.create();
        try {
            for (int step = TRAIL_ALPHAS.length; step >= 0; step--) {
                float t = travel - step * 0.09f;
                if (t < 0f) continue;
                float eased = falling ? t * t : 1f - (1f - t) * (1f - t);
                float px = from[0] + (to[0] - from[0]) * eased;
                float py = from[1] + (to[1] - from[1]) * eased;
                if (step == 0) {
                    comet.setComposite(AlphaComposite.SrcOver);
                    CellPainter.drawGarbage(comet, Math.round(px - size / 2f), Math.round(py - size / 2f), size);
                    if (label != null) drawCometLabel(comet, label, px, py, size, from[0] > to[0]);
                } else {
                    int ghost = Math.round(size * (1f - step * 0.16f));
                    comet.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, TRAIL_ALPHAS[step - 1]));
                    comet.setColor(UiColors.GARBAGE);
                    int arc = CellPainter.radius(ghost) * 2;
                    comet.fillRoundRect(Math.round(px - ghost / 2f), Math.round(py - ghost / 2f), ghost, ghost, arc, arc);
                }
            }
        } finally {
            comet.dispose();
        }
    }

    private void drawCometLabel(Graphics2D g2d, String label, float x, float y, int size, boolean labelOnRight) {
        float scale = layout.scale();
        g2d.setFont(UiFonts.display(Math.round(15 * scale)));
        FontMetrics metrics = g2d.getFontMetrics();
        int width = UiPainter.textWidth(metrics, label);
        int gap = Math.round(8 * scale);
        int textX = labelOnRight ? Math.round(x + size / 2f + gap) : Math.round(x - size / 2f - gap - width);
        int baseline = Math.round(y + metrics.getAscent() / 2f - metrics.getDescent() / 2f);
        g2d.setColor(UiColors.darker(UiColors.ACCENT, 0.6f));
        UiPainter.drawText(g2d, label, textX + 1, baseline + 1);
        g2d.setColor(UiColors.ACCENT);
        UiPainter.drawText(g2d, label, textX, baseline);
    }

    /** ④ 삽입 구역: 보드 맨 아래 들어온 줄을 accent 점선 + 7% 틴트로 감싼다. */
    private void drawInsertZone(Graphics2D g2d, AttackEffect effect, long now) {
        float fade = effect.progress(now) < 0.7f ? 1f : (1f - effect.progress(now)) / 0.3f;
        int pitch = layout.pitch();
        int rows = Math.min(effect.rows(), GameConstants.BOARD_ROWS);
        int x = layout.boardX(effect.player());
        int y = layout.boardY() + (GameConstants.BOARD_ROWS - rows) * pitch;
        int width = pitch * GameConstants.BOARD_COLS;
        int height = pitch * rows;
        int arc = CellPainter.radius(pitch) * 2;
        Graphics2D zone = (Graphics2D) g2d.create();
        try {
            zone.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, Math.max(0f, fade)));
            zone.setColor(UiColors.withAlpha(UiColors.ACCENT, 0.07f));
            zone.fillRoundRect(x, y, width, height, arc, arc);
            float dash = Math.max(3f, pitch * 0.25f);
            zone.setStroke(new BasicStroke(Math.max(2f, 2.5f * layout.scale()), BasicStroke.CAP_ROUND,
                    BasicStroke.JOIN_ROUND, 10f, new float[]{dash, dash * 0.7f}, 0f));
            zone.setColor(UiColors.ACCENT);
            zone.drawRoundRect(x, y, width, height, arc, arc);
        } finally {
            zone.dispose();
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
        int boxY = layout.vsCenterY() - boxHeight / 2;
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

    /** 마지막으로 그린 배치에서 그 플레이어 공격 대기열 격자의 위치. 아직 그리지 않았으면 지금 크기로 계산한다. */
    Rectangle queueGridBounds(Player player) {
        Layout current = layout != null ? layout : computeLayout(Math.max(1, getWidth()), Math.max(1, getHeight()));
        return new Rectangle(current.queueGrid().get(player));
    }

    /** 알림이 온 플레이어 영역만 다시 그린다 (인계서 2단계, 성능 LB-N01). 배치를 아직 모르면 전체를 그린다. */
    private void repaintSide(Player player) {
        if (layout == null) {
            repaint();
        } else {
            repaint(layout.region(player));
        }
    }

    // ---- 알림

    private final class MatchEvents implements VersusListener {
        @Override public void onQueueChanged(Player player, List<GarbageLine> lines) {
            VersusScreen.this.onQueueChanged(player, lines);
        }

        @Override public void onLevelChanged(Player player, int level) {
            sides.get(player).level = level;
            repaintSide(player);
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

    /** 플레이어 한 명의 보드·점수·다음 블록·대기열. 그 플레이어의 엔진 알림을 받는다. */
    private final class PlayerSide implements GameEventListener {
        private final Player player;
        private final BoardView board;
        private int score;
        private int level = 1;
        private int[] next = new int[0];
        private List<GarbageLine> queue = List.of();
        /** 마지막으로 지운 줄들의 가운데 행. 공격 회색 뭉치가 여기서 출발한다. */
        private float lastClearedRow = GameConstants.BOARD_ROWS - 2;

        PlayerSide(Player player, ItemRegistry items) {
            this.player = player;
            this.board = new BoardView(() -> repaintSide(player), items);
        }

        void reset() {
            board.reset();
            score = 0;
            level = 1;
            next = new int[0];
            queue = List.of();
            lastClearedRow = GameConstants.BOARD_ROWS - 2;
        }

        @Override public void onBoardUpdated(BoardSnapshot snapshot) { board.onBoardUpdated(snapshot); }

        @Override public void onLandingUpdated(BoardSnapshot falling, BoardSnapshot landing) {
            board.onLandingUpdated(falling, landing);
        }

        @Override public void onLinesCleared(List<Integer> rows, BoardSnapshot boardBeforeClear) {
            if (!rows.isEmpty()) {
                lastClearedRow = (float) rows.stream().mapToInt(Integer::intValue).average().orElse(lastClearedRow);
            }
            board.onLinesCleared(rows, boardBeforeClear);
        }

        @Override public void onScoreChanged(int updated) {
            score = updated;
            repaintSide(player);
        }

        @Override public void onNextBlocksChanged(int[] blockTypes) {
            next = blockTypes.clone();
            repaintSide(player);
        }

        @Override public void onPauseStateChanged(boolean paused) { }

        @Override public void onFeverActivated(int seconds) { board.onFeverActivated(seconds); }

        @Override public void onSlowDownActivated(int previousLevel, int currentLevel) {
            board.onSlowDownActivated(previousLevel, currentLevel);
        }

        @Override public void onHardDropped(BoardSnapshot before, BoardSnapshot after) { board.onHardDropped(before, after); }

        @Override public void onBombExploded(CellPosition center, List<CellPosition> blastCells) {
            board.onBombExploded(center, blastCells);
        }

        @Override public void onAnvilCrushed(List<CellPosition> crushedCells) { board.onAnvilCrushed(crushedCells); }

        @Override public void onGameOver(int finalScore) {
            // 승패는 VersusListener.onMatchEnded로 받는다 (인계서 4.3).
        }
    }
}

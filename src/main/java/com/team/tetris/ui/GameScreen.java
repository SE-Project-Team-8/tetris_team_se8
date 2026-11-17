/*
 * 게임 화면. 엔진 이벤트를 그리고 설정된 키를 GameAction으로 변환한다.
 * 셀 코드(CellCodes)에서 색과 문자를 디코드해 그리며, 블록형 아이템의 미리보기 모양은 ItemRegistry에서 찾는다.
 */
package com.team.tetris.ui;

import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.event.ActionEvent;
import java.awt.font.TextAttribute;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.IntConsumer;

import javax.swing.AbstractAction;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.Timer;

import com.team.tetris.block.ColorScheme;
import com.team.tetris.block.Cell;
import com.team.tetris.block.Rotation;
import com.team.tetris.block.Tetromino;
import com.team.tetris.block.TetrominoType;
import com.team.tetris.common.GameMode;
import com.team.tetris.common.Screen;
import com.team.tetris.common.constants.GameConstants;
import com.team.tetris.common.events.BoardSnapshot;
import com.team.tetris.common.events.CellCodes;
import com.team.tetris.common.events.GameEventListener;
import com.team.tetris.game.GameAction;
import com.team.tetris.game.GameEngine;
import com.team.tetris.game.GameState;
import com.team.tetris.game.item.AnvilItem;
import com.team.tetris.game.item.BombItem;
import com.team.tetris.game.item.FeverItem;
import com.team.tetris.game.item.ItemRegistry;
import com.team.tetris.game.item.StandaloneItem;
import com.team.tetris.settings.KeyBindings;
import com.team.tetris.ui.theme.CellPainter;
import com.team.tetris.ui.theme.UiColors;
import com.team.tetris.ui.theme.UiFonts;
import com.team.tetris.ui.theme.UiPainter;

/**
 * 엔진의 이벤트를 화면에 그려 주고, 설정된 키를 게임 동작으로 변환한다.
 * 보드·점수·다음 블록의 실제 상태는 GameEngine이 소유한다.
 */
public class GameScreen extends JPanel implements Screen {
    /** Shift·Ctrl·Alt·Meta를 누른 모든 조합. 조작 키는 수정 키를 함께 누른 상태로도 연결한다. */
    private static final int[] MODIFIER_COMBINATIONS = modifierCombinations();
    // 창 크기 L(600px) 기준 레이아웃 수치. 그릴 때 UiPainter.scale()을 곱한다.
    private static final int BASE_MARGIN = 28;
    private static final int BASE_HEADER_HEIGHT = 60;
    private static final int BASE_FOOTER_HEIGHT = 36;
    private static final int BASE_FRAME_PADDING = 10;
    private static final int BASE_FRAME_RADIUS = 16;
    private static final int BASE_PANEL_GAP = 14;
    private static final int BASE_PREVIEW_CELL = 18;
    /** 우측 정보 패널 열이 창 폭에서 차지하는 비율. */
    private static final float SIDEBAR_RATIO = 0.38f;
    private static final Color ITEM_MODE_BADGE = new Color(0xB07FE8);
    /** 줄 삭제 팝 단계. 프레임 간격(65ms)마다 하나씩 넘어가며, 마지막 단계 뒤에 압축된 보드를 보여 준다. */
    private enum LineClearStage { POP, POP_HOLD, FADE, CLEARED }
    private static final LineClearStage[] LINE_CLEAR_STAGES = LineClearStage.values();
    /** 아이템 토스트 종류. 보드 색조와 헤더 뱃지를 정한다. */
    private enum ItemEffectKind { FEVER, SLOW }
    private static final Color SLOW_COLOR = new Color(0x5FD8F0);
    /** 보드 상태 틴트 불투명도 (스펙: 13%). */
    private static final float BOARD_TINT_ALPHA = 0.13f;
    /** 하드드롭 잔상·착지 눌림·흔들림이 보이는 시간 (1~2프레임). */
    private static final int HARD_DROP_EFFECT_MS = 110;
    /** 잔상 개수와 각 잔상의 불투명도. 착지 위치에 가까울수록 진하다. */
    private static final float[] TRAIL_ALPHAS = {0.15f, 0.30f, 0.50f};
    /** 한 번 누르면 한 번만 일어나야 하는 동작. 이동·회전은 누르고 있으면 계속 반복된다. */
    private static final Set<GameAction> ONE_PRESS_ACTIONS =
            EnumSet.of(GameAction.HARD_DROP, GameAction.PAUSE, GameAction.QUIT);
    private static final int LINE_CLEAR_FLASH_INTERVAL_MS = 65;
    private static final int BEST_SCORE_TICK_MS = 30;
    private static final int BEST_SCORE_HOLD_MS = 500;
    private static final int BEST_SCORE_FADE_MS = 1_500;
    private static final int ITEM_EFFECT_TICK_MS = 30;
    private static final int ITEM_EFFECT_FADE_MS = 450;
    private static final int FEVER_NOTIFICATION_MS = 2_700;
    private static final int SLOW_DOWN_NOTIFICATION_MS = 2_000;
    private static final Color BEST_SCORE_COLOR = new Color(255, 211, 64);

    private final ScreenRouter router;
    private final GameEngine engine;
    private final IntConsumer gameOverAction;
    private final ItemRegistry items;
    private KeyBindings keyBindings;

    // 엔진 이벤트는 EDT에서 온다. 배열은 화면 상태로 보관하기 전에 복사한다.
    private final GameEventListener eventListener = new GameEventListener() {
        @Override
        public void onBoardUpdated(BoardSnapshot snapshot) {
            int[][] cells = copyBoard(snapshot);
            if (cells == null) {
                return;
            }
            onEdt(() -> {
                if (lineClearAnimating) {
                    pendingBoard = cells;
                } else {
                    board = cells;
                }
                repaint();
            });
        }

        @Override
        public void onLandingUpdated(BoardSnapshot fallingPiece, BoardSnapshot landingPiece) {
            int[][] fallingCells = fallingPiece == null ? null : copyBoard(fallingPiece);
            int[][] landingCells = landingPiece == null ? null : copyBoard(landingPiece);
            if ((fallingPiece != null && fallingCells == null)
                    || (landingPiece != null && landingCells == null)) {
                return;
            }
            onEdt(() -> {
                if (lineClearAnimating) {
                    pendingFalling = fallingCells;
                    pendingLanding = landingCells;
                } else {
                    falling = fallingCells;
                    landing = landingCells;
                }
                repaint();
            });
        }

        @Override
        public void onLinesCleared(List<Integer> rows, BoardSnapshot boardBeforeClear) {
            int[][] beforeClear = copyBoard(Objects.requireNonNull(boardBeforeClear, "boardBeforeClear"));
            if (beforeClear == null) {
                throw new IllegalArgumentException("Pre-clear board does not match the game board dimensions");
            }
            startLineClearAnimation(rows, beforeClear);
        }

        @Override
        public void onLinesCleared(List<Integer> rows) {
            startLineClearAnimation(rows, null);
        }

        private void startLineClearAnimation(List<Integer> rows, int[][] beforeClear) {
            int[] copy = Objects.requireNonNull(rows, "rows").stream()
                    .mapToInt(row -> Objects.requireNonNull(row, "row"))
                    .toArray();
            for (int row : copy) {
                if (row < 0 || row >= GameConstants.BOARD_ROWS) {
                    throw new IllegalArgumentException("Cleared row is outside the game board: " + row);
                }
            }
            onEdt(() -> {
                if (beforeClear != null) {
                    board = beforeClear;
                }
                pendingBoard = null;
                falling = null;
                pendingFalling = null;
                landing = null;
                pendingLanding = null;
                flashingRows = copy;
                flashFrame = 0;
                lineClearAnimating = true;
                lineClearTimer.restart();
                repaint();
            });
        }

        @Override
        public void onScoreChanged(int updatedScore) {
            onEdt(() -> {
                score = updatedScore;
                updateBestScoreNotification();
                repaint();
            });
        }

        @Override
        public void onNextBlocksChanged(int[] blockTypes) {
            int[] copy = Objects.requireNonNull(blockTypes, "blockTypes").clone();
            onEdt(() -> {
                nextBlocks = copy;
                repaint();
            });
        }

        @Override
        public void onPauseStateChanged(boolean isPaused) {
            // The separate pause screen reflects this state; no board change is needed.
        }

        @Override
        public void onFeverActivated(int seconds) {
            showItemEffect("FEVER TIME!", "2X SCORE  ·  " + seconds + " SECONDS",
                    UiColors.ACCENT, FEVER_NOTIFICATION_MS, ItemEffectKind.FEVER);
        }

        @Override
        public void onSlowDownActivated(int previousLevel, int currentLevel) {
            String title = currentLevel < previousLevel ? "SLOW DOWN" : "MIN SPEED";
            String subtitle = currentLevel < previousLevel
                    ? "LEVEL " + previousLevel + "  →  " + currentLevel
                    : "LEVEL " + currentLevel + " CANNOT DROP";
            showItemEffect(title, subtitle, SLOW_COLOR, SLOW_DOWN_NOTIFICATION_MS, ItemEffectKind.SLOW);
        }

        @Override
        public void onGameOver(int finalScore) {
            onEdt(() -> {
                if (gameOver) {
                    return;
                }
                gameOver = true;
                score = finalScore;
                updateBestScoreNotification();
                gameOverAction.accept(finalScore);
            });
        }
    };

    // 아래 값은 렌더링 전용 화면 상태이며, 엔진 상태의 복사본이다.
    private int[][] board = emptyBoard();
    private int[] nextBlocks = new int[0];
    private int score;
    private ColorScheme colorScheme = ColorScheme.standard();
    private GameMode gameMode = GameMode.NORMAL;
    private boolean gameOver;
    private int[] flashingRows = new int[0];
    private int flashFrame;
    private boolean lineClearAnimating;
    private int bestScore = -1;
    private boolean bestScoreNotificationShown;
    private int bestScoreElapsedMs;
    private String itemEffectTitle;
    private String itemEffectSubtitle;
    private Color itemEffectColor;
    /** 지금 떠 있는 아이템 토스트 종류. 슬로우다운 색조는 토스트가 떠 있는 동안만 보인다. */
    private ItemEffectKind itemEffectKind;
    private int itemEffectElapsedMs;
    private int itemEffectDurationMs;
    private int[][] pendingBoard;
    private int[][] falling;
    private int[][] pendingFalling;
    private int[][] landing;
    private int[][] pendingLanding;
    private final Timer lineClearTimer = new Timer(LINE_CLEAR_FLASH_INTERVAL_MS, event -> advanceLineClearAnimation());
    private final Timer bestScoreTimer = new Timer(BEST_SCORE_TICK_MS, event -> advanceBestScoreAnimation());
    private final Timer itemEffectTimer = new Timer(ITEM_EFFECT_TICK_MS, event -> advanceItemEffectAnimation());
    // 하드드롭 연출 상태. 타이머가 끝나면 비워 다음 리페인트에 원래 모습으로 돌아간다.
    private int[][] dropTrail;
    private int dropDistance;
    private boolean[][] squashedCells;
    private final Timer hardDropTimer = createOneShot(HARD_DROP_EFFECT_MS, () -> {
        clearHardDropEffect();
        repaint();
    });

    private static Timer createOneShot(int delayMs, Runnable action) {
        Timer timer = new Timer(delayMs, event -> action.run());
        timer.setRepeats(false);
        return timer;
    }

    public void setBestScore(int bestScore) {
        if (bestScore < -1) {
            throw new IllegalArgumentException("bestScore must be non-negative or -1 when no score exists");
        }
        onEdt(() -> {
            this.bestScore = bestScore;
            updateBestScoreNotification();
        });
    }

    private void updateBestScoreNotification() {
        if (bestScore >= 0 && score > bestScore && !bestScoreNotificationShown) {
            bestScoreNotificationShown = true;
            bestScoreElapsedMs = 0;
            bestScoreTimer.restart();
            repaint();
        }
    }

    private void advanceBestScoreAnimation() {
        bestScoreElapsedMs += BEST_SCORE_TICK_MS;
        if (bestScoreElapsedMs >= BEST_SCORE_HOLD_MS + BEST_SCORE_FADE_MS) {
            bestScoreTimer.stop();
        }
        repaint();
    }

    private void showItemEffect(String title, String subtitle, Color color, int durationMs, ItemEffectKind kind) {
        onEdt(() -> {
            itemEffectTitle = title;
            itemEffectSubtitle = subtitle;
            itemEffectColor = color;
            itemEffectKind = kind;
            itemEffectElapsedMs = 0;
            itemEffectDurationMs = durationMs;
            itemEffectTimer.restart();
            repaint();
        });
    }

    private void advanceItemEffectAnimation() {
        itemEffectElapsedMs += ITEM_EFFECT_TICK_MS;
        if (itemEffectElapsedMs >= itemEffectDurationMs) {
            itemEffectTitle = null;
            itemEffectSubtitle = null;
            itemEffectColor = null;
            itemEffectKind = null;
            itemEffectTimer.stop();
        }
        repaint();
    }

    private void advanceLineClearAnimation() {
        if (flashFrame + 1 >= LINE_CLEAR_STAGES.length) {
            flashingRows = new int[0];
            lineClearAnimating = false;
            if (pendingBoard != null) {
                board = pendingBoard;
                pendingBoard = null;
            }
            falling = pendingFalling;
            pendingFalling = null;
            landing = pendingLanding;
            pendingLanding = null;
            lineClearTimer.stop();
        } else {
            flashFrame++;
        }
        repaint();
    }

    /**
        * @param router 화면 전환을 관리하는 라우터
        * @param engine 게임 입력과 상태를 담당하는 엔진
        * @param gameOverAction 최종 점수를 전달받아 결과 화면을 설정하고 전환할 동작
     */
    public GameScreen(ScreenRouter router, GameEngine engine, KeyBindings bindings, IntConsumer gameOverAction) {
        this(router, engine, bindings, gameOverAction, ItemRegistry.defaults());
    }

    /** @param items 블록형 아이템의 미리보기 모양을 문자로 찾는 데 쓴다. 게임 쪽과 같은 목록을 전달한다. */
    public GameScreen(ScreenRouter router, GameEngine engine, KeyBindings bindings, IntConsumer gameOverAction,
            ItemRegistry items) {
        this.router = Objects.requireNonNull(router, "router");
        this.engine = Objects.requireNonNull(engine, "engine");
        this.gameOverAction = Objects.requireNonNull(gameOverAction, "gameOverAction");
        this.items = Objects.requireNonNull(items, "items");

        setBackground(UiColors.BACKGROUND);
        setOpaque(true);
        setFocusable(true);
        updateKeyBindings(bindings);
    }

    /** The app composition root forwards its single engine listener here. */
    public GameEventListener eventListener() {
        return eventListener;
    }

    @Override
    public JPanel getPanel() {
        return this;
    }

    @Override
    public void onShow() {
        requestFocusInWindow();
        if (engine.getState() == GameState.READY || engine.getState() == GameState.STOPPED
                || engine.getState() == GameState.GAME_OVER) {
            resetViewState();
            gameOver = false;
            engine.start();
        } else if (engine.getState() == GameState.PAUSED) {
            engine.resume();
        }
        repaint();
    }

    @Override
    public void onHide() {
        if (engine.getState() == GameState.RUNNING) {
            engine.pause();
        }
    }

    /** 시작 메뉴의 새 게임 동작에서 호출해 이전 상태를 버리고 새 판을 시작한다. */
    public void startNewGame() {
        onEdt(() -> {
            if (engine.getState() == GameState.RUNNING || engine.getState() == GameState.PAUSED) {
                engine.stop();
            }
            if (!ScreenRouter.GAME.equals(router.getCurrentScreenName())) {
                router.showScreen(ScreenRouter.GAME);
            } else {
                onShow();
            }
            requestFocusInWindow();
        });
    }

    /**
     * 설정에서 선택한 팔레트를 적용한다.
     * 블록 종류 ID는 유지하고 렌더링 색상만 바꾼다.
     */
    public void setColorScheme(ColorScheme colorScheme) {
        ColorScheme updatedScheme = Objects.requireNonNull(colorScheme, "colorScheme");
        onEdt(() -> {
            this.colorScheme = updatedScheme;
            repaint();
        });
    }

    /** 헤더의 모드 뱃지에 쓸 현재 게임 방식. 새 판을 시작하기 전에 호출한다. */
    public void setGameMode(GameMode gameMode) {
        GameMode updatedMode = Objects.requireNonNull(gameMode, "gameMode");
        onEdt(() -> {
            this.gameMode = updatedMode;
            repaint();
        });
    }

    public void updateKeyBindings(KeyBindings bindings) {
        keyBindings = Objects.requireNonNull(bindings, "bindings");
        getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).clear();
        getActionMap().clear();
        for (GameAction action : GameAction.values()) {
            int keyCode = bindings.keyFor(action);
            // 수정 키(Shift 등)를 조작 키로 지정하면 그 키를 누를 때 자기 자신이 눌린 상태로 오고,
            // 수정 키를 누른 채 다른 조작 키를 눌러도 동작해야 하므로 모든 조합에 같은 동작을 연결한다.
            for (int modifiers : MODIFIER_COMBINATIONS) {
                bindKey(action.name(), KeyStroke.getKeyStroke(keyCode, modifiers), () ->
                        keyBindings.actionFor(keyCode).ifPresent(this::handleAction));
            }
        }
        repaint();
    }

    private void handleAction(GameAction action) {
        // 하드 드롭 키를 누르고 있으면 자동 반복으로 다음 블록까지 연달아 떨어지므로 새로 누를 때만 받는다.
        if (ONE_PRESS_ACTIONS.contains(action) && HeldKeys.isAutoRepeat()) return;
        // 하드드롭 연출에 쓸 출발 위치(낙하 블록)와 도착 위치(고스트)는 엔진이 블록을 고정하기 전에 잡아 둔다.
        int[][] dropFrom = action == GameAction.HARD_DROP ? falling : null;
        int[][] dropTo = action == GameAction.HARD_DROP ? landing : null;
        if (!engine.handle(action)) return;
        if (dropFrom != null && dropTo != null) startHardDropEffect(dropFrom, dropTo);
        if (action == GameAction.PAUSE) router.showScreen(ScreenRouter.PAUSE);
        if (action == GameAction.QUIT) router.showScreen(ScreenRouter.MAIN_MENU);
    }

    /**
     * 하드드롭 연출(스펙 §3.3)을 시작한다: 지나온 경로의 잔상 + 착지 칸 눌림 + 보드 1px 흔들림.
     * 한 번만 울리는 타이머가 끝나면 지우므로 상시 애니메이션 루프가 없다.
     */
    private void startHardDropEffect(int[][] from, int[][] to) {
        int fromTop = topRow(from);
        int toTop = topRow(to);
        if (fromTop < 0 || toTop <= fromTop) return;
        dropTrail = from;
        dropDistance = toTop - fromTop;
        squashedCells = new boolean[GameConstants.BOARD_ROWS][GameConstants.BOARD_COLS];
        for (int row = 0; row < to.length; row++) {
            for (int col = 0; col < to[row].length; col++) {
                squashedCells[row][col] = to[row][col] != BoardSnapshot.EMPTY;
            }
        }
        hardDropTimer.restart();
        repaint();
    }

    private void clearHardDropEffect() {
        hardDropTimer.stop();
        dropTrail = null;
        squashedCells = null;
        dropDistance = 0;
    }

    private static int topRow(int[][] cells) {
        for (int row = 0; row < cells.length; row++) {
            for (int value : cells[row]) {
                if (value != BoardSnapshot.EMPTY) return row;
            }
        }
        return -1;
    }

    private static int[] modifierCombinations() {
        int[] masks = {InputEvent.SHIFT_DOWN_MASK, InputEvent.CTRL_DOWN_MASK,
                InputEvent.ALT_DOWN_MASK, InputEvent.META_DOWN_MASK};
        int[] combinations = new int[1 << masks.length];
        for (int combination = 0; combination < combinations.length; combination++) {
            for (int bit = 0; bit < masks.length; bit++) {
                if ((combination & (1 << bit)) != 0) combinations[combination] |= masks[bit];
            }
        }
        return combinations;
    }

    private void bindKey(String name, KeyStroke keyStroke, Runnable command) {
        // KeyListener 대신 Swing 키 바인딩을 사용해 포커스 변화에 덜 민감하게 입력을 처리한다.
        getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(keyStroke, name);
        getActionMap().put(name, new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent event) {
                if (engine.getState() == GameState.RUNNING && !gameOver) {
                    command.run();
                }
            }
        });
    }

    private void resetViewState() {
        // 새 게임 시작 시 이전 판에서 전달받은 표시 데이터만 초기화한다.
        lineClearTimer.stop();
        bestScoreTimer.stop();
        itemEffectTimer.stop();
        clearHardDropEffect();
        flashingRows = new int[0];
        lineClearAnimating = false;
        bestScoreNotificationShown = false;
        bestScoreElapsedMs = 0;
        itemEffectTitle = null;
        itemEffectSubtitle = null;
        itemEffectColor = null;
        itemEffectKind = null;
        itemEffectElapsedMs = 0;
        itemEffectDurationMs = 0;
        pendingBoard = null;
        falling = null;
        pendingFalling = null;
        landing = null;
        pendingLanding = null;
        board = emptyBoard();
        nextBlocks = new int[0];
        score = 0;
    }

    private static int[][] copyBoard(BoardSnapshot snapshot) {
        Objects.requireNonNull(snapshot, "snapshot");
        int[][] source = snapshot.cells();
        // UI는 20x10 보드만 그린다. 계약과 다른 크기의 이벤트는 화면 상태에 반영하지 않는다.
        if (source.length != GameConstants.BOARD_ROWS) {
            return null;
        }

        int[][] copy = new int[source.length][];
        for (int row = 0; row < source.length; row++) {
            if (source[row] == null || source[row].length != GameConstants.BOARD_COLS) {
                return null;
            }
            // 행 배열까지 복사해 엔진이 원본 배열을 재사용해도 화면 상태가 바뀌지 않게 한다.
            copy[row] = source[row].clone();
        }
        return copy;
    }

    private static int[][] emptyBoard() {
        // 빈 칸은 0이 아니라 공용 스냅샷 계약의 EMPTY(-1)로 표현한다.
        int[][] cells = new int[GameConstants.BOARD_ROWS][GameConstants.BOARD_COLS];
        for (int[] row : cells) {
            Arrays.fill(row, BoardSnapshot.EMPTY);
        }
        return cells;
    }

    private void paintGame(Graphics graphics) {
        // Swing이 전달한 Graphics의 색상·폰트 상태를 오염시키지 않도록 복사본을 사용한다.
        Graphics2D g2d = (Graphics2D) graphics.create();
        try {
            UiPainter.prepare(g2d);

            int width = getWidth();
            int height = getHeight();
            // 고정 수치는 L(600px) 기준이며 창 폭 배율을 곱해 S/M/L에서 같은 비율을 유지한다.
            float scale = UiPainter.scale(width);
            int margin = Math.round(BASE_MARGIN * scale);
            int headerHeight = Math.round(BASE_HEADER_HEIGHT * scale);
            int footerHeight = Math.round(BASE_FOOTER_HEIGHT * scale);
            int framePadding = Math.round(BASE_FRAME_PADDING * scale);
            int gap = Math.round(BASE_PANEL_GAP * scale);
            // 보드와 우측 정보 패널이 사용할 공간을 나눈 뒤, 보드 칸 간격은 가로·세로에 모두 맞춘다.
            int boardAreaWidth = width - margin * 2 - gap - Math.round(width * SIDEBAR_RATIO);
            int boardAreaHeight = height - headerHeight - footerHeight - margin;
            int pitch = Math.max(8, Math.min(
                    (boardAreaWidth - framePadding * 2) / GameConstants.BOARD_COLS,
                    (boardAreaHeight - framePadding * 2) / GameConstants.BOARD_ROWS));
            int boardWidth = pitch * GameConstants.BOARD_COLS;
            int boardHeight = pitch * GameConstants.BOARD_ROWS;
            int frameX = margin;
            int frameY = headerHeight;
            int boardX = frameX + framePadding;
            int boardY = frameY + framePadding;
            int sideX = frameX + boardWidth + framePadding * 2 + gap;

            drawHeader(g2d, margin, width, headerHeight, scale);
            // 하드드롭 착지 순간에는 보드 전체를 1px 아래로 흔든다. 연출이 끝나면 다음 리페인트에 제자리로 온다.
            int shake = squashedCells != null ? Math.max(1, Math.round(scale)) : 0;
            g2d.translate(0, shake);
            drawBoardFrame(g2d, frameX, frameY, boardWidth + framePadding * 2, boardHeight + framePadding * 2,
                    Math.round(BASE_FRAME_RADIUS * scale));
            drawBoard(g2d, boardX, boardY, pitch);
            g2d.translate(0, -shake);
            drawSidebar(g2d, sideX, frameY, width - margin - sideX, scale);
            drawItemEffectNotification(g2d, boardX, boardY, boardWidth, boardHeight, scale);
            drawBestScoreNotification(g2d, boardX, boardY, boardWidth, boardHeight, scale);

            if (engine.getState() != GameState.PAUSED) {
                g2d.setColor(UiColors.withAlpha(UiColors.TEXT_MUTED, 0.75f));
                g2d.setFont(UiFonts.body(Math.round(12 * scale)));
                String help = "이동 " + keyText(GameAction.MOVE_LEFT) + " " + keyText(GameAction.MOVE_RIGHT)
                        + " " + keyText(GameAction.SOFT_DROP) + "  ·  회전 " + keyText(GameAction.ROTATE_CLOCKWISE)
                        + "  ·  하드드롭 " + keyText(GameAction.HARD_DROP)
                        + "  ·  일시정지 " + keyText(GameAction.PAUSE) + "  ·  나가기 " + keyText(GameAction.QUIT);
                UiPainter.drawCentered(g2d, help, width / 2, height - footerHeight / 2);
            }
        } finally {
            // create()로 만든 Graphics 복사본은 사용 후 반드시 해제한다.
            g2d.dispose();
        }
    }

    /** 지금 보드에 입힐 상태 색. 피버는 점수 배율이 켜진 동안, 슬로우다운은 토스트가 떠 있는 동안이다. */
    private Color boardStateColor() {
        if (engine.getScoreMultiplier() > 1) return UiColors.PLAYER2;
        if (itemEffectKind == ItemEffectKind.SLOW) return SLOW_COLOR;
        return null;
    }

    /**
     * 보드 프레임. 피버·슬로우다운 중에는 13% 색조 + 상태색 테두리와 바깥 글로우를 입힌다.
     * 정적 오버레이 한 장이라 상시 애니메이션 비용이 없다.
     */
    private void drawBoardFrame(Graphics2D g2d, int x, int y, int width, int height, int radius) {
        Color state = boardStateColor();
        if (state == null) {
            UiPainter.drawPanel(g2d, x, y, width, height, radius, UiColors.BOARD, UiColors.PANEL_BORDER, 2f);
            return;
        }
        // 바깥 글로우: 테두리 밖으로 점점 옅어지는 링 두 겹.
        for (int ring = 2; ring >= 1; ring--) {
            int spread = ring * 4;
            UiPainter.strokeRoundRect(g2d, x - spread, y - spread, width + spread * 2, height + spread * 2,
                    radius + spread, UiColors.withAlpha(state, 0.10f * (3 - ring)), 4f);
        }
        UiPainter.drawPanel(g2d, x, y, width, height, radius, UiColors.BOARD, null, 0f);
        g2d.setColor(UiColors.withAlpha(state, BOARD_TINT_ALPHA));
        g2d.fillRoundRect(x, y, width, height, radius * 2, radius * 2);
        UiPainter.strokeRoundRect(g2d, x, y, width, height, radius, state, 3f);
    }

    private float itemEffectFade() {
        if (itemEffectDurationMs <= 0) return 0f;
        int fadeStart = itemEffectDurationMs - ITEM_EFFECT_FADE_MS;
        if (itemEffectElapsedMs <= fadeStart) return 1f;
        return Math.max(0f, (float) (itemEffectDurationMs - itemEffectElapsedMs) / ITEM_EFFECT_FADE_MS);
    }

    /**
     * 아이템 토스트: 보드 위쪽 가운데의 패널 카드 + 상태색 테두리 + Jua 제목 + 고정폭 부제.
     * 피버는 제목 양옆에 별, 슬로우다운은 제목 앞에 ▼를 도형으로 그린다.
     */
    private void drawItemEffectNotification(Graphics2D g2d, int boardX, int boardY, int boardWidth,
            int boardHeight, float scale) {
        if (itemEffectTitle == null) return;
        Graphics2D toast = (Graphics2D) g2d.create();
        try {
            toast.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, itemEffectFade()));
            Font titleFont = UiFonts.display(Math.round(22 * scale));
            Font subtitleFont = UiFonts.monoOrFallback(itemEffectSubtitle, Math.round(12 * scale));
            FontMetrics titleMetrics = toast.getFontMetrics(titleFont);
            FontMetrics subtitleMetrics = toast.getFontMetrics(subtitleFont);
            int icon = Math.round(16 * scale);
            int iconGap = Math.round(8 * scale);
            int titleWidth = titleMetrics.stringWidth(itemEffectTitle);
            int decoratedWidth = titleWidth + (itemEffectKind == ItemEffectKind.FEVER ? (icon + iconGap) * 2
                    : itemEffectKind == ItemEffectKind.SLOW ? icon + iconGap : 0);
            int boxWidth = Math.min(boardWidth - Math.round(16 * scale),
                    Math.max(decoratedWidth, subtitleMetrics.stringWidth(itemEffectSubtitle)) + Math.round(48 * scale));
            int boxHeight = Math.round(74 * scale);
            int centerX = boardX + boardWidth / 2;
            int boxX = centerX - boxWidth / 2;
            int boxY = boardY + Math.round(boardHeight * 0.2f);

            UiPainter.drawPanel(toast, boxX, boxY, boxWidth, boxHeight, Math.round(16 * scale),
                    UiColors.PANEL, itemEffectColor, 3f);
            int titleBaseline = boxY + Math.round(33 * scale);
            int titleX = centerX - decoratedWidth / 2;
            int iconCenterY = titleBaseline - titleMetrics.getAscent() / 2 + Math.round(2 * scale);
            if (itemEffectKind == ItemEffectKind.FEVER) {
                UiPainter.drawStar(toast, titleX + icon / 2, iconCenterY, icon / 2, itemEffectColor);
                UiPainter.drawStar(toast, titleX + decoratedWidth - icon / 2, iconCenterY, icon / 2, itemEffectColor);
                titleX += icon + iconGap;
            } else if (itemEffectKind == ItemEffectKind.SLOW) {
                UiPainter.drawDownTriangle(toast, titleX + icon / 2, iconCenterY, icon, itemEffectColor);
                titleX += icon + iconGap;
            }
            toast.setFont(titleFont);
            toast.setColor(itemEffectColor);
            toast.drawString(itemEffectTitle, titleX, titleBaseline);
            toast.setFont(subtitleFont);
            toast.setColor(UiColors.TEXT_SECONDARY);
            UiPainter.drawCentered(toast, itemEffectSubtitle, centerX, boxY + boxHeight - Math.round(17 * scale));
        } finally {
            toast.dispose();
        }
    }

    /** 최고 점수 갱신 알림: 보드 위쪽에 노란 글자를 잠깐 띄웠다가 흐리게 사라진다. */
    private void drawBestScoreNotification(Graphics2D g2d, int boardX, int boardY, int boardWidth,
            int boardHeight, float scale) {
        if (!bestScoreNotificationShown
                || bestScoreElapsedMs >= BEST_SCORE_HOLD_MS + BEST_SCORE_FADE_MS) {
            return;
        }

        float alpha = 1f;
        if (bestScoreElapsedMs > BEST_SCORE_HOLD_MS) {
            alpha = 1f - (float) (bestScoreElapsedMs - BEST_SCORE_HOLD_MS) / BEST_SCORE_FADE_MS;
        }
        var originalComposite = g2d.getComposite();
        g2d.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, Math.max(0f, alpha)));
        g2d.setFont(UiFonts.display(Math.round(30 * scale)));
        String message = "BEST SCORE!";
        int centerX = boardX + boardWidth / 2;
        int messageY = boardY + Math.round(boardHeight * 0.12f);
        g2d.setColor(UiColors.darker(BEST_SCORE_COLOR, 0.6f));
        UiPainter.drawCentered(g2d, message, centerX + 2, messageY + 2);
        g2d.setColor(BEST_SCORE_COLOR);
        UiPainter.drawCentered(g2d, message, centerX, messageY);
        g2d.setComposite(originalComposite);
    }

    /** 화면 제목과 우측 상단의 모드·난이도 뱃지. */
    private void drawHeader(Graphics2D g2d, int margin, int width, int headerHeight, float scale) {
        g2d.setFont(UiFonts.display(Math.round(26 * scale)));
        g2d.setColor(UiColors.TEXT_PRIMARY);
        FontMetrics metrics = g2d.getFontMetrics();
        int titleBaseline = (headerHeight - metrics.getHeight()) / 2 + metrics.getAscent();
        g2d.drawString("TETRIS", margin, titleBaseline);

        int badgeHeight = Math.round(22 * scale);
        int badgeY = (headerHeight - badgeHeight) / 2;
        int badgeGap = Math.round(6 * scale);
        String difficulty = engine.getDifficulty().name();
        Color[] difficultyColors = UiPainter.difficultyBadgeColors(difficulty);
        int badgeX = width - margin - UiPainter.badgeWidth(g2d, badgeHeight, difficulty);
        UiPainter.drawBadge(g2d, badgeX, badgeY, badgeHeight, difficulty, difficultyColors[0], difficultyColors[1]);
        if (gameMode == GameMode.ITEM) {
            String itemBadge = "아이템 모드";
            badgeX -= badgeGap + UiPainter.badgeWidth(g2d, badgeHeight, itemBadge);
            UiPainter.drawBadge(g2d, badgeX, badgeY, badgeHeight, itemBadge, ITEM_MODE_BADGE, UiColors.CREAM);
        }
        // 피버·슬로우다운 중에는 상태 뱃지를 맨 앞에 붙인다.
        String stateBadge = null;
        Color stateColor = null;
        if (engine.getScoreMultiplier() > 1) {
            stateBadge = "FEVER ×" + engine.getScoreMultiplier();
            stateColor = UiColors.PLAYER2;
        } else if (itemEffectKind == ItemEffectKind.SLOW) {
            stateBadge = "SLOW";
            stateColor = SLOW_COLOR;
        }
        if (stateBadge != null) {
            badgeX -= badgeGap + UiPainter.badgeWidth(g2d, badgeHeight, stateBadge);
            UiPainter.drawBadge(g2d, badgeX, badgeY, badgeHeight, stateBadge, stateColor, UiColors.TEXT_ON_ACCENT);
        }
    }

    private void drawBoard(Graphics2D g2d, int x, int y, int cellSize) {
        int width = cellSize * GameConstants.BOARD_COLS;

        // 빈 칸도 옅은 슬롯으로 그려 보드의 칸 구분을 격자선 대신 보여 준다.
        for (int row = 0; row < GameConstants.BOARD_ROWS; row++) {
            for (int col = 0; col < GameConstants.BOARD_COLS; col++) {
                int inset = cellInset(cellSize);
                CellPainter.drawEmptySlot(g2d, x + col * cellSize + inset, y + row * cellSize + inset,
                        cellSize - inset * 2);
            }
        }

        // 스냅샷의 숫자 ID를 종류로 바꿔 팔레트 색상과 블록 식별 문자를 함께 그린다.
        // 모루 칸은 모아 두었다가 한 덩어리 실루엣으로 그린다.
        boolean[][] anvil = new boolean[board.length][GameConstants.BOARD_COLS];
        boolean hasAnvil = false;
        for (int row = 0; row < board.length; row++) {
            for (int col = 0; col < board[row].length; col++) {
                if (isAnvil(board[row][col])) {
                    anvil[row][col] = true;
                    hasAnvil = true;
                    continue;
                }
                if (isSquashed(row, col)) {
                    drawSquashedCell(g2d, board[row][col], x + col * cellSize, y + row * cellSize, cellSize);
                    continue;
                }
                drawBoardCell(g2d, board[row][col], x + col * cellSize, y + row * cellSize, cellSize);
            }
        }
        if (hasAnvil) {
            CellPainter.drawAnvil(g2d, anvil, x, y, cellSize, cellInset(cellSize), AnvilItem.SYMBOL);
        }
        drawHardDropTrail(g2d, x, y, cellSize);

        if (flashingRows.length > 0) {
            drawLineClearPop(g2d, x, y, cellSize, width);
        }

        if (lineClearAnimating && pendingFalling != null) {
            // 깜빡이는 동안에도 엔진은 다음 블록을 움직이므로, 그 블록은 보이게 그린다.
            // 아이템 문자는 블록이 합쳐진 대기 중 보드의 셀 코드에 들어 있으므로 그 코드를 쓴다.
            for (int row = 0; row < pendingFalling.length; row++) {
                for (int col = 0; col < pendingFalling[row].length; col++) {
                    if (pendingFalling[row][col] == BoardSnapshot.EMPTY) continue;
                    int blockId = pendingBoard != null ? pendingBoard[row][col] : pendingFalling[row][col];
                    drawBoardCell(g2d, blockId, x + col * cellSize, y + row * cellSize, cellSize);
                }
            }
        }

        if (!lineClearAnimating && landing != null) {
            drawLandingGhost(g2d, x, y, cellSize);
        }
    }

    /**
     * 줄 삭제 팝 (스펙 §3.1): 지워질 줄이 cream으로 변하며 `^ ^` 웃고 양옆에 반짝이가 뜬다 →
     * 옅어지며 사라진다 → 빈 줄로 보인 뒤 압축된 보드로 바뀐다. 여러 줄이면 모든 줄이 동시에 팝한다.
     */
    private void drawLineClearPop(Graphics2D g2d, int x, int y, int pitch, int width) {
        LineClearStage stage = LINE_CLEAR_STAGES[flashFrame];
        int inset = cellInset(pitch);
        int size = pitch - inset * 2;
        for (int row : flashingRows) {
            int rowY = y + row * pitch;
            // 원래 블럭을 보드색으로 덮고 그 위에 단계별 모습을 그린다.
            g2d.setColor(UiColors.BOARD);
            g2d.fillRect(x, rowY, width, pitch);
            for (int col = 0; col < GameConstants.BOARD_COLS; col++) {
                int cellX = x + col * pitch + inset;
                switch (stage) {
                    case POP, POP_HOLD -> CellPainter.drawPop(g2d, cellX, rowY + inset, size);
                    case FADE -> {
                        CellPainter.drawEmptySlot(g2d, cellX, rowY + inset, size);
                        g2d.setColor(UiColors.withAlpha(UiColors.CREAM, 0.4f));
                        int arc = CellPainter.radius(size) * 2;
                        g2d.fillRoundRect(cellX, rowY + inset, size, size, arc, arc);
                    }
                    case CLEARED -> CellPainter.drawEmptySlot(g2d, cellX, rowY + inset, size);
                }
            }
            if (stage == LineClearStage.POP || stage == LineClearStage.POP_HOLD) {
                // 줄 양끝과 가운데 근처에 반짝이 셋. 위치는 줄 번호로 정해 프레임마다 흔들리지 않는다.
                int sparkle = Math.max(3, Math.round(pitch * 0.28f));
                UiPainter.drawSparkle(g2d, x - sparkle / 2, rowY + pitch / 4, sparkle, UiColors.ACCENT);
                UiPainter.drawSparkle(g2d, x + width + sparkle / 2, rowY + pitch * 3 / 4, sparkle, UiColors.ACCENT);
                int middle = x + pitch * (3 + row % 4) + pitch / 2;
                UiPainter.drawSparkle(g2d, middle, rowY + pitch / 8, Math.round(sparkle * 0.7f), UiColors.ACCENT);
            }
        }
    }

    private boolean isSquashed(int row, int col) {
        return squashedCells != null && squashedCells[row][col];
    }

    /** 착지 눌림은 표정 있는 기본 블럭에만 준다. 아이템 칸은 아이콘이 일그러지지 않게 그대로 그린다. */
    private void drawSquashedCell(Graphics2D g2d, int code, int cellX, int cellY, int pitch) {
        TetrominoType type = typeFromCode(code);
        if (type == null || CellCodes.symbol(code) != CellCodes.NO_SYMBOL) {
            drawBoardCell(g2d, code, cellX, cellY, pitch);
            return;
        }
        int inset = cellInset(pitch);
        CellPainter.drawSquashed(g2d, cellX + inset, cellY + inset, pitch - inset * 2,
                new Color(colorScheme.rgbOf(type)));
    }

    /**
     * 하드드롭 잔상: 출발 위치에서 착지 위치까지의 경로에 블록 모양을 세 번 겹쳐 그린다.
     * 빈 칸에만 그려 이미 쌓인 블럭을 덮지 않는다.
     */
    private void drawHardDropTrail(Graphics2D g2d, int x, int y, int pitch) {
        if (dropTrail == null || dropDistance <= 1) return;
        int inset = cellInset(pitch);
        int size = pitch - inset * 2;
        for (int step = 0; step < TRAIL_ALPHAS.length; step++) {
            int offset = Math.round(dropDistance * (step + 1) / (float) (TRAIL_ALPHAS.length + 1));
            for (int row = 0; row < dropTrail.length; row++) {
                for (int col = 0; col < dropTrail[row].length; col++) {
                    TetrominoType type = typeFromCode(dropTrail[row][col]);
                    int targetRow = row + offset;
                    if (type == null || targetRow >= board.length
                            || board[targetRow][col] != BoardSnapshot.EMPTY) {
                        continue;
                    }
                    CellPainter.drawTrail(g2d, x + col * pitch + inset, y + targetRow * pitch + inset, size,
                            new Color(colorScheme.rgbOf(type)), TRAIL_ALPHAS[step]);
                }
            }
        }
    }

    /** 칸 사이 간격. 셀을 칸보다 조금 작게 그려 뿌요처럼 낱알이 구분되게 한다. */
    private static int cellInset(int pitch) {
        return Math.max(1, Math.round(pitch * 0.04f));
    }

    private void drawBoardCell(Graphics2D g2d, int code, int cellX, int cellY, int pitch) {
        // 빈칸·잘못된 코드는 symbol()이 예외를 던지므로 먼저 걸러 낸다.
        if (!CellCodes.isValid(code)) {
            return;
        }
        int inset = cellInset(pitch);
        drawCell(g2d, code, CellCodes.symbol(code), cellX + inset, cellY + inset, pitch - inset * 2);
    }

    /**
     * 셀 코드 하나를 그린다. 셀 코드는 색을 빌려 올 블록 종류와 아이템 문자를 함께 담는다.
     * {@code symbol}이 없으면 표정 있는 기본 블럭, 있으면 아이템 칸으로 그린다.
     */
    private void drawCell(Graphics2D g2d, int code, char symbol, int x, int y, int size) {
        TetrominoType type = typeFromCode(code);
        if (type == null) {
            return;
        }
        Color color = new Color(colorScheme.rgbOf(type));
        if (symbol == AnvilItem.SYMBOL) {
            // 보드와 미리보기는 모루를 한 덩어리로 그린다. 여기는 줄 삭제 중 낱칸으로 그려야 할 때만 온다.
            CellPainter.drawAnvil(g2d, new boolean[][]{{true}}, x, y, size, 0, symbol);
        } else if (symbol == BombItem.SYMBOL) {
            CellPainter.drawBombItem(g2d, x, y, size, color, symbol);
        } else if (symbol == FeverItem.SYMBOL) {
            CellPainter.drawFeverItem(g2d, x, y, size, color, symbol);
        } else if (symbol != CellCodes.NO_SYMBOL) {
            CellPainter.drawItem(g2d, x, y, size, color, symbol);
        } else {
            CellPainter.drawBlock(g2d, x, y, size, color);
        }
    }

    private static boolean isAnvil(int code) {
        return CellCodes.isValid(code) && CellCodes.symbol(code) == AnvilItem.SYMBOL;
    }

    private void drawLandingGhost(Graphics2D g2d, int x, int y, int cellSize) {
        int inset = cellInset(cellSize);
        for (int row = 0; row < landing.length; row++) {
            for (int col = 0; col < landing[row].length; col++) {
                TetrominoType type = typeFromCode(landing[row][col]);
                // 착지 위치가 낙하 중인 블록과 겹치는 칸은 블록 위에 테두리를 덧그리지 않는다.
                if (type == null || board[row][col] != BoardSnapshot.EMPTY) {
                    continue;
                }
                CellPainter.drawGhost(g2d, x + col * cellSize + inset, y + row * cellSize + inset,
                        cellSize - inset * 2);
            }
        }
    }

    private void drawSidebar(Graphics2D g2d, int x, int y, int width, float scale) {
        int gap = Math.round(12 * scale);
        int radius = Math.round(12 * scale);
        int nextHeight = Math.round(96 * scale);
        int statHeight = Math.round(74 * scale);

        UiPainter.drawPanel(g2d, x, y, width, nextHeight, radius);
        drawPanelLabel(g2d, "NEXT", x, y, width, scale);
        // 미리보기 큐가 더 길어지더라도 현재 설정된 표시 개수만큼만 그린다.
        int previewCount = Math.min(nextBlocks.length, GameConstants.NEXT_QUEUE_SIZE);
        int labelSpace = Math.round(28 * scale);
        for (int index = 0; index < previewCount; index++) {
            drawPreview(g2d, nextBlocks[index], x, y + labelSpace, width, nextHeight - labelSpace, scale);
        }

        int statY = y + nextHeight + gap;
        // 피버 중에는 점수 옆에 배율, 슬로우다운으로 내려간 단계가 있으면 레벨 옆에 ▼단계를 붙인다.
        int multiplier = engine.getScoreMultiplier();
        drawStatPanel(g2d, "SCORE", String.format(Locale.US, "%,d", score), UiColors.CREAM,
                multiplier > 1 ? "×" + multiplier : null, UiColors.PLAYER2, x, statY, width, statHeight, scale);
        statY += statHeight + gap;
        drawStatPanel(g2d, "LINES", String.valueOf(engine.getClearedLines()), UiColors.LINES,
                null, null, x, statY, width, statHeight, scale);
        statY += statHeight + gap;
        int slowdown = engine.getSlowdownSteps();
        drawStatPanel(g2d, "LEVEL", String.valueOf(engine.getLevel()), slowdown > 0 ? SLOW_COLOR : UiColors.LEVEL,
                slowdown > 0 ? "▼" + slowdown : null, SLOW_COLOR, x, statY, width, statHeight, scale);
    }

    /**
     * 라벨 + 큰 수치 패널. {@code chip}이 있으면 수치 오른쪽에 작은 알약 표시를 붙인다.
     */
    private static void drawStatPanel(Graphics2D g2d, String label, String value, Color valueColor,
            String chip, Color chipColor, int x, int y, int width, int height, float scale) {
        UiPainter.drawPanel(g2d, x, y, width, height, Math.round(12 * scale));
        drawPanelLabel(g2d, label, x, y, width, scale);
        g2d.setFont(UiFonts.mono(Math.round(26 * scale)));
        FontMetrics valueMetrics = g2d.getFontMetrics();
        int baseline = y + height - Math.round(16 * scale);
        if (chip == null) {
            g2d.setColor(valueColor);
            UiPainter.drawCentered(g2d, value, x + width / 2, baseline);
            return;
        }
        int chipHeight = Math.round(20 * scale);
        int chipGap = Math.round(8 * scale);
        int chipWidth = UiPainter.badgeWidth(g2d, chipHeight, chip);
        int valueWidth = valueMetrics.stringWidth(value);
        int startX = x + (width - valueWidth - chipGap - chipWidth) / 2;
        g2d.setColor(valueColor);
        g2d.drawString(value, startX, baseline);
        int chipY = baseline - valueMetrics.getAscent() / 2 - chipHeight / 2 + Math.round(2 * scale);
        UiPainter.drawBadge(g2d, startX + valueWidth + chipGap, chipY, chipHeight, chip, chipColor,
                UiColors.TEXT_ON_ACCENT);
    }

    /** 패널 상단의 작은 대문자 라벨. 자간을 넓혀 목업의 라벨 질감을 낸다. */
    private static void drawPanelLabel(Graphics2D g2d, String label, int x, int y, int width, float scale) {
        g2d.setFont(UiFonts.body(Math.round(11 * scale)).deriveFont(Map.of(TextAttribute.TRACKING, 0.12f)));
        g2d.setColor(UiColors.TEXT_MUTED);
        UiPainter.drawCentered(g2d, label, x + width / 2, y + Math.round(22 * scale));
    }

    private void drawPreview(Graphics2D g2d, int code, int x, int y, int width, int height, float scale) {
        TetrominoType type = typeFromCode(code);
        if (type == null) {
            return;
        }
        char symbol = CellCodes.symbol(code);
        int itemIndex = CellCodes.itemCellIndex(code);
        // 문자가 있는데 붙은 칸 번호가 없으면 블록 전체가 아이템인 블록형이다.
        boolean wholePieceItem = symbol != CellCodes.NO_SYMBOL && itemIndex == CellCodes.NO_INDEX;
        List<Cell> occupied = wholePieceItem
                ? standaloneItemShape(symbol)
                : new Tetromino(type, Rotation.SPAWN, 0, 0).cells();
        drawPreviewShape(g2d, occupied, code, symbol, itemIndex, x, y, width, height, scale);
    }

    /** 블록형 아이템의 모양은 아이템 자신이 정한다. 목록에 없는 문자는 한 칸짜리로 그려 문자만이라도 보인다. */
    private List<Cell> standaloneItemShape(char symbol) {
        return items.standaloneBySymbol(symbol)
                .map(StandaloneItem::previewShape)
                .orElse(List.of(new Cell(0, 0)));
    }

    /**
     * 실제 점유 셀 범위만 찾아 영역 중앙에 맞춰 그린다. 각 블록의 회전 격자에는 빈 테두리가 있을 수 있다.
     * {@code symbol}이 있으면 {@code itemIndex} 칸에, 칸 번호가 없으면 모든 칸에 문자를 얹는다.
     */
    private void drawPreviewShape(Graphics2D g2d, List<Cell> occupied, int code,
            char symbol, int itemIndex, int x, int y, int width, int height, float scale) {
        int minRow = occupied.stream().mapToInt(Cell::row).min().orElse(0);
        int maxRow = occupied.stream().mapToInt(Cell::row).max().orElse(0);
        int minCol = occupied.stream().mapToInt(Cell::col).min().orElse(0);
        int maxCol = occupied.stream().mapToInt(Cell::col).max().orElse(0);
        int previewCellSize = Math.min(Math.round(BASE_PREVIEW_CELL * scale), Math.min(
                (width - 20) / (maxCol - minCol + 1),
                (height - 12) / (maxRow - minRow + 1)));
        int shapeWidth = previewCellSize * (maxCol - minCol + 1);
        int shapeHeight = previewCellSize * (maxRow - minRow + 1);
        int shapeX = x + (width - shapeWidth) / 2;
        int shapeY = y + (height - shapeHeight) / 2;
        int inset = cellInset(previewCellSize);

        if (symbol == AnvilItem.SYMBOL) {
            boolean[][] anvil = new boolean[maxRow - minRow + 1][maxCol - minCol + 1];
            for (Cell cell : occupied) anvil[cell.row() - minRow][cell.col() - minCol] = true;
            CellPainter.drawAnvil(g2d, anvil, shapeX, shapeY, previewCellSize, inset, symbol);
            return;
        }
        for (int index = 0; index < occupied.size(); index++) {
            var cell = occupied.get(index);
            int cellX = shapeX + (cell.col() - minCol) * previewCellSize;
            int cellY = shapeY + (cell.row() - minRow) * previewCellSize;
            // 블록형 아이템은 모든 칸에, 포함형은 붙은 칸 하나에만 문자를 얹는다.
            boolean lettered = symbol != CellCodes.NO_SYMBOL
                    && (itemIndex == CellCodes.NO_INDEX || index == itemIndex);
            drawCell(g2d, code, lettered ? symbol : CellCodes.NO_SYMBOL,
                    cellX + inset, cellY + inset, previewCellSize - inset * 2);
        }
    }


    /** 빈칸이나 규칙에 없는 코드는 null. 잘못된 코드 하나 때문에 전체 화면 그리기가 중단되지 않게 한다. */
    private static TetrominoType typeFromCode(int code) {
        if (!CellCodes.isValid(code)) {
            return null;
        }
        return TetrominoType.fromId(CellCodes.baseType(code));
    }

    private String keyText(GameAction action) {
        return switch (keyBindings.keyFor(action)) {
            case KeyEvent.VK_LEFT -> "←";
            case KeyEvent.VK_RIGHT -> "→";
            case KeyEvent.VK_UP -> "↑";
            case KeyEvent.VK_DOWN -> "↓";
            case KeyEvent.VK_ESCAPE -> "Esc";
            default -> KeyEvent.getKeyText(keyBindings.keyFor(action));
        };
    }

    private static void onEdt(Runnable action) {
        // Swing 컴포넌트 변경은 이벤트 디스패치 스레드(EDT)에서만 수행한다.
        if (SwingUtilities.isEventDispatchThread()) {
            action.run();
        } else {
            SwingUtilities.invokeLater(action);
        }
    }

    @Override
    protected void paintComponent(Graphics graphics) {
        super.paintComponent(graphics);
        // 화면 상태를 읽기만 하며, 게임 진행이나 점수 계산은 여기서 수행하지 않는다.
        paintGame(graphics);
    }
}

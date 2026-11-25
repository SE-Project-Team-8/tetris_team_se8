/*
 * 게임 화면. 엔진 이벤트를 그리고 설정된 키를 GameAction으로 변환한다.
 * 셀 코드(CellCodes)에서 색과 문자를 디코드해 그리며, 블록형 아이템의 미리보기 모양은 ItemRegistry에서 찾는다.
 */
package com.team.tetris.ui;

import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.event.ActionEvent;
import java.awt.font.TextAttribute;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
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
import com.team.tetris.common.GameMode;
import com.team.tetris.common.Screen;
import com.team.tetris.common.constants.GameConstants;
import com.team.tetris.common.events.BoardSnapshot;
import com.team.tetris.common.events.CellPosition;
import com.team.tetris.common.events.GameEventListener;
import com.team.tetris.game.GameAction;
import com.team.tetris.game.GameEngine;
import com.team.tetris.game.GameState;
import com.team.tetris.game.item.ItemRegistry;
import com.team.tetris.settings.KeyBindings;
import com.team.tetris.ui.theme.ThemedDialog;
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
    /** 보드 상태 틴트 불투명도 (스펙: 13%). */
    private static final float BOARD_TINT_ALPHA = 0.13f;
    /** 한 번 누르면 한 번만 일어나야 하는 동작. 이동·회전은 누르고 있으면 계속 반복된다. */
    private static final Set<GameAction> ONE_PRESS_ACTIONS =
            EnumSet.of(GameAction.HARD_DROP, GameAction.PAUSE, GameAction.QUIT);
    private static final int BEST_SCORE_TICK_MS = 30;
    private static final int BEST_SCORE_HOLD_MS = 500;
    private static final int BEST_SCORE_FADE_MS = 1_500;
    private static final Color BEST_SCORE_COLOR = new Color(255, 211, 64);
    private static final int LEVEL_UP_TICK_MS = 30;
    /** 레벨업 카드가 떠 있는 전체 시간. 마지막 {@link BoardView#ITEM_EFFECT_FADE_MS} 동안 흐려진다. */
    private static final int LEVEL_UP_NOTIFICATION_MS = 1_800;
    /** 레벨업 카드가 작게 시작해 살짝 넘치며 제 크기로 튀어나오는 시간. */
    private static final int LEVEL_UP_POP_MS = 180;

    private final ScreenRouter router;
    private final GameEngine engine;
    private final IntConsumer gameOverAction;
    private KeyBindings keyBindings;

    // 엔진 이벤트는 EDT에서 온다. 보드·연출은 BoardView가, 점수·다음 블록·레벨업·최고 기록은 이 화면이 맡는다.
    private final GameEventListener eventListener = new GameEventListener() {
        @Override
        public void onBoardUpdated(BoardSnapshot snapshot) {
            onEdt(() -> {
                boardView.onBoardUpdated(snapshot);
                // 엔진은 줄을 지우고 다음 블록을 꺼낼 때 레벨을 다시 계산한 뒤 보드를 보낸다.
                updateLevelUpNotification();
            });
        }

        @Override
        public void onLandingUpdated(BoardSnapshot fallingPiece, BoardSnapshot landingPiece) {
            onEdt(() -> boardView.onLandingUpdated(fallingPiece, landingPiece));
        }

        @Override
        public void onLinesCleared(List<Integer> rows, BoardSnapshot boardBeforeClear) {
            Objects.requireNonNull(boardBeforeClear, "boardBeforeClear");
            onEdt(() -> boardView.onLinesCleared(rows, boardBeforeClear));
        }

        @Override
        public void onLinesCleared(List<Integer> rows) {
            onEdt(() -> boardView.onLinesCleared(rows, null));
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
            onEdt(() -> boardView.onFeverActivated(seconds));
        }

        @Override
        public void onSlowDownActivated(int previousLevel, int currentLevel) {
            onEdt(() -> boardView.onSlowDownActivated(previousLevel, currentLevel));
        }

        @Override
        public void onHardDropped(BoardSnapshot fallingBefore, BoardSnapshot fallingAfter) {
            onEdt(() -> boardView.onHardDropped(fallingBefore, fallingAfter));
        }

        @Override
        public void onBombExploded(CellPosition center, List<CellPosition> blastCells) {
            onEdt(() -> boardView.onBombExploded(center, blastCells));
        }

        @Override
        public void onAnvilCrushed(List<CellPosition> crushedCells) {
            onEdt(() -> boardView.onAnvilCrushed(crushedCells));
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
    private final BoardView boardView;
    private int[] nextBlocks = new int[0];
    private int score;
    private GameMode gameMode = GameMode.NORMAL;
    private boolean gameOver;
    private int bestScore = -1;
    private boolean bestScoreNotificationShown;
    private int bestScoreElapsedMs;
    /** 마지막으로 확인한 레벨. -1이면 새 판이 시작돼 아직 확인하지 않은 상태다. */
    private int shownLevel = -1;
    private int levelUpFrom;
    private int levelUpTo;
    /** 레벨업 카드가 뜬 뒤 지난 시간. 음수이면 카드가 없다. */
    private int levelUpElapsedMs = -1;
    private final Timer bestScoreTimer = new Timer(BEST_SCORE_TICK_MS, event -> advanceBestScoreAnimation());
    private final Timer levelUpTimer = new Timer(LEVEL_UP_TICK_MS, event -> advanceLevelUpAnimation());

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

    /** 레벨이 올랐으면 레벨업 카드를 띄운다. 슬로우다운으로 내려간 레벨은 기억만 한다. */
    private void updateLevelUpNotification() {
        int level = engine.getLevel();
        if (shownLevel >= 0 && level > shownLevel) {
            levelUpFrom = shownLevel;
            levelUpTo = level;
            levelUpElapsedMs = 0;
            levelUpTimer.restart();
        }
        shownLevel = level;
    }

    private void advanceLevelUpAnimation() {
        levelUpElapsedMs += LEVEL_UP_TICK_MS;
        if (levelUpElapsedMs >= LEVEL_UP_NOTIFICATION_MS) {
            levelUpElapsedMs = -1;
            levelUpTimer.stop();
        }
        repaint();
    }

    /** 레벨업 카드 불투명도. 끝나기 직전에만 흐려진다. 카드가 없으면 0이다. */
    private float levelUpFade() {
        if (levelUpElapsedMs < 0) return 0f;
        int fadeStart = LEVEL_UP_NOTIFICATION_MS - BoardView.ITEM_EFFECT_FADE_MS;
        if (levelUpElapsedMs <= fadeStart) return 1f;
        return Math.max(0f, (float) (LEVEL_UP_NOTIFICATION_MS - levelUpElapsedMs) / BoardView.ITEM_EFFECT_FADE_MS);
    }

    /** 레벨업 카드 크기 배율. 0.7배에서 시작해 살짝 넘쳤다가 1배에 멈춘다 (ease-out-back). */
    private float levelUpPopScale() {
        if (levelUpElapsedMs >= LEVEL_UP_POP_MS) return 1f;
        float t = Math.max(0f, (float) levelUpElapsedMs / LEVEL_UP_POP_MS) - 1f;
        float overshoot = 1.70158f;
        float eased = 1f + (overshoot + 1f) * t * t * t + overshoot * t * t;
        return 0.7f + 0.3f * eased;
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
        this.boardView = new BoardView(this::repaint, Objects.requireNonNull(items, "items"));

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
        onEdt(() -> boardView.setColorScheme(updatedScheme));
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
        if (action == GameAction.QUIT && engine.getState() == GameState.RUNNING) {
            confirmQuit();
            return;
        }
        // 하드드롭 연출은 엔진의 onHardDropped 알림으로 시작한다. 키가 화면을 거치지 않는 대전에서도 같은 경로를 쓴다.
        if (!engine.handle(action)) return;
        if (action == GameAction.PAUSE) router.showScreen(ScreenRouter.PAUSE);
        if (action == GameAction.QUIT) router.showScreen(ScreenRouter.MAIN_MENU);
    }

    /** 나가기 키를 실수로 눌러 판을 잃지 않도록 게임을 멈추고 한 번 더 묻는다. */
    private void confirmQuit() {
        engine.pause();
        int choice = ThemedDialog.showOptions(this, "메인 화면으로 나가기",
                "메인 화면으로 나갈까요?\n현재 게임 내용은 저장되지 않고 초기화됩니다.",
                ThemedDialog.Tone.WARNING, 1, "나가기", "계속하기");
        if (choice == 0 && engine.handle(GameAction.QUIT)) {
            router.showScreen(ScreenRouter.MAIN_MENU);
        } else {
            engine.resume();
        }
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
        boardView.reset();
        bestScoreTimer.stop();
        levelUpTimer.stop();
        shownLevel = -1;
        levelUpElapsedMs = -1;
        bestScoreNotificationShown = false;
        bestScoreElapsedMs = 0;
        nextBlocks = new int[0];
        score = 0;
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
            int shake = boardView.shakeOffset(scale);
            g2d.translate(0, shake);
            drawBoardFrame(g2d, frameX, frameY, boardWidth + framePadding * 2, boardHeight + framePadding * 2,
                    Math.round(BASE_FRAME_RADIUS * scale));
            boardView.paintBoard(g2d, boardX, boardY, pitch);
            g2d.translate(0, -shake);
            drawSidebar(g2d, sideX, frameY, width - margin - sideX, scale);
            boardView.paintToast(g2d, boardX, boardY, boardWidth, boardHeight, scale);
            drawBestScoreNotification(g2d, boardX, boardY, boardWidth, boardHeight, scale);

            if (engine.getState() != GameState.PAUSED) {
                g2d.setColor(UiColors.withAlpha(UiColors.TEXT_MUTED, 0.75f));
                g2d.setFont(UiFonts.body(Math.round(12 * scale)));
                String help = "이동 " + keyText(GameAction.MOVE_LEFT) + " " + keyText(GameAction.MOVE_RIGHT)
                        + " " + keyText(GameAction.SOFT_DROP)
                        + "  ·  회전 " + keyText(GameAction.ROTATE_CLOCKWISE) + " " + keyText(GameAction.ROTATE_COUNTERCLOCKWISE)
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
        if (boardView.slowDownShowing()) return BoardView.SLOW_COLOR;
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

    /**
     * 레벨업 카드: 아이템 토스트와 같은 패널 카드에 LEVEL 주황 테두리 + 제목 양옆 ▲ + "LEVEL 이전 → 다음" 부제.
     * 블록을 가리거나 보드 위 알림과 겹치지 않도록 우측 패널 열의 LEVEL 패널 바로 아래 빈 자리에 띄운다.
     *
     * @param x 패널 열 왼쪽 x
     * @param y 카드 윗변 y (LEVEL 패널 아래 간격 포함)
     * @param width 패널 열 폭. 카드도 위 패널들과 같은 폭으로 그린다.
     */
    private void drawLevelUpNotification(Graphics2D g2d, int x, int y, int width, float scale) {
        if (levelUpElapsedMs < 0) return;
        Graphics2D toast = (Graphics2D) g2d.create();
        try {
            String title = "LEVEL UP!";
            String subtitle = "LEVEL " + levelUpFrom + "  →  " + levelUpTo;
            Font titleFont = UiFonts.display(Math.round(24 * scale));
            Font subtitleFont = UiFonts.monoOrFallback(subtitle, Math.round(12 * scale));
            FontMetrics titleMetrics = toast.getFontMetrics(titleFont);
            int icon = Math.round(16 * scale);
            int iconGap = Math.round(8 * scale);
            int decoratedWidth = titleMetrics.stringWidth(title) + (icon + iconGap) * 2;
            int boxWidth = width;
            int boxHeight = Math.round(78 * scale);
            int centerX = x + width / 2;
            int boxX = x;
            int boxY = y;

            // 카드 가운데를 기준으로 튀어나오듯 커진다.
            float pop = levelUpPopScale();
            double centerY = boxY + boxHeight / 2.0;
            toast.translate(centerX, centerY);
            toast.scale(pop, pop);
            toast.translate(-centerX, -centerY);
            toast.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, levelUpFade()));

            Color deep = UiColors.darker(UiColors.LEVEL, 0.55f);
            int radius = Math.round(12 * scale);
            int shadow = Math.max(2, Math.round(5 * scale));
            toast.setColor(deep);
            toast.fillRoundRect(boxX, boxY + shadow, boxWidth, boxHeight, radius * 2, radius * 2);
            UiPainter.drawPanel(toast, boxX, boxY, boxWidth, boxHeight, radius,
                    UiColors.PANEL, UiColors.LEVEL, 3f);

            int titleBaseline = boxY + Math.round(36 * scale);
            int titleX = centerX - decoratedWidth / 2;
            int iconCenterY = titleBaseline - titleMetrics.getAscent() / 2 + Math.round(2 * scale);
            UiPainter.drawUpTriangle(toast, titleX + icon / 2, iconCenterY, icon, UiColors.LEVEL);
            UiPainter.drawUpTriangle(toast, titleX + decoratedWidth - icon / 2, iconCenterY, icon, UiColors.LEVEL);
            // 카드 위 모서리에 작은 반짝이를 얹어 축하하는 느낌을 낸다.
            int sparkle = Math.round(6 * scale);
            int sparkleInset = Math.round(14 * scale);
            UiPainter.drawSparkle(toast, boxX + sparkleInset, boxY + sparkleInset, sparkle, UiColors.ACCENT);
            UiPainter.drawSparkle(toast, boxX + boxWidth - sparkleInset, boxY + sparkleInset, sparkle,
                    UiColors.ACCENT);

            int textX = titleX + icon + iconGap;
            int textShadow = Math.max(1, Math.round(2 * scale));
            toast.setFont(titleFont);
            toast.setColor(deep);
            toast.drawString(title, textX + textShadow, titleBaseline + textShadow);
            toast.setColor(UiColors.LEVEL);
            toast.drawString(title, textX, titleBaseline);
            toast.setFont(subtitleFont);
            toast.setColor(UiColors.TEXT_SECONDARY);
            UiPainter.drawCentered(toast, subtitle, centerX, boxY + boxHeight - Math.round(17 * scale));
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
    /**
     * 헤더 제목: 시작 메뉴의 타일 로고와 달리 타일 없이 글자 자체를 블럭색으로 칠한 캔디 글자.
     * 글자마다 위는 cream을 섞어 밝게, 아래는 블럭색으로 내려가는 그라데이션을 주고 짙은 음영을 아래에 깐다.
     */
    private static void drawHeaderTitle(Graphics2D g2d, int x, int headerHeight, float scale) {
        Graphics2D title = (Graphics2D) g2d.create();
        try {
            title.setFont(UiFonts.display(Math.round(32 * scale)));
            FontMetrics metrics = title.getFontMetrics();
            int depth = Math.max(2, Math.round(3 * scale));
            int baseline = UiPainter.centeredBaseline(metrics, 0, headerHeight) - depth / 2;
            int letterGap = Math.max(1, Math.round(2 * scale));
            String text = "TETRIS";
            int letterX = x;
            for (int index = 0; index < text.length(); index++) {
                String letter = String.valueOf(text.charAt(index));
                Color color = UiColors.LOGO_TILES[index % UiColors.LOGO_TILES.length];
                title.setColor(UiColors.darker(color, 0.55f));
                title.drawString(letter, letterX, baseline + depth);
                title.setPaint(new GradientPaint(0, baseline - metrics.getAscent(), blend(color, UiColors.CREAM, 0.35f),
                        0, baseline, color));
                title.drawString(letter, letterX, baseline);
                letterX += metrics.stringWidth(letter) + letterGap;
            }
        } finally {
            title.dispose();
        }
    }

    /** {@code from}에 {@code to}를 {@code amount}만큼 섞은 색. */
    private static Color blend(Color from, Color to, float amount) {
        return new Color(
                Math.round(from.getRed() + (to.getRed() - from.getRed()) * amount),
                Math.round(from.getGreen() + (to.getGreen() - from.getGreen()) * amount),
                Math.round(from.getBlue() + (to.getBlue() - from.getBlue()) * amount));
    }

    private void drawHeader(Graphics2D g2d, int margin, int width, int headerHeight, float scale) {
        drawHeaderTitle(g2d, margin, headerHeight, scale);

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
        } else if (boardView.slowDownShowing()) {
            stateBadge = "SLOW";
            stateColor = BoardView.SLOW_COLOR;
        }
        if (stateBadge != null) {
            badgeX -= badgeGap + UiPainter.badgeWidth(g2d, badgeHeight, stateBadge);
            UiPainter.drawBadge(g2d, badgeX, badgeY, badgeHeight, stateBadge, stateColor, UiColors.TEXT_ON_ACCENT);
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
            boardView.paintPreview(g2d, nextBlocks[index], x, y + labelSpace, width, nextHeight - labelSpace,
                    Math.round(BASE_PREVIEW_CELL * scale));
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
        drawStatPanel(g2d, "LEVEL", String.valueOf(engine.getLevel()), slowdown > 0 ? BoardView.SLOW_COLOR : UiColors.LEVEL,
                slowdown > 0 ? "▼" + slowdown : null, BoardView.SLOW_COLOR, x, statY, width, statHeight, scale);
        // 레벨업 카드가 떠 있는 동안 LEVEL 패널 테두리도 주황으로 강조해 어느 수치가 올랐는지 보여 준다.
        float levelGlow = levelUpFade();
        if (levelGlow > 0f) {
            UiPainter.strokeRoundRect(g2d, x, statY, width, statHeight, radius,
                    UiColors.withAlpha(UiColors.LEVEL, levelGlow), 2.5f);
        }
        drawLevelUpNotification(g2d, x, statY + statHeight + gap, width, scale);
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

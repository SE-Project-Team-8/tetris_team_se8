/*
 * 보드 한 판의 그리기와 연출 상태. 1인 게임 화면과 대전 화면(플레이어마다 하나)이 같이 쓴다.
 */
package com.team.tetris.ui;

import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.function.LongSupplier;

import javax.swing.Timer;

import com.team.tetris.block.Cell;
import com.team.tetris.block.ColorScheme;
import com.team.tetris.block.Rotation;
import com.team.tetris.block.Tetromino;
import com.team.tetris.block.TetrominoType;
import com.team.tetris.common.constants.GameConstants;
import com.team.tetris.common.events.BoardSnapshot;
import com.team.tetris.common.events.CellCodes;
import com.team.tetris.common.events.CellPosition;
import com.team.tetris.game.item.AnvilItem;
import com.team.tetris.game.item.BombItem;
import com.team.tetris.game.item.FeverItem;
import com.team.tetris.game.item.ItemRegistry;
import com.team.tetris.game.item.StandaloneItem;
import com.team.tetris.ui.theme.CellPainter;
import com.team.tetris.ui.theme.UiColors;
import com.team.tetris.ui.theme.UiFonts;
import com.team.tetris.ui.theme.UiPainter;

/**
 * 20×10 보드 하나를 그린다: 칸(회색 공격줄 포함), 고스트, 줄 삭제 팝, 하드드롭 잔상·눌림, 폭탄·무게추 연출,
 * 아이템 토스트. 엔진 알림을 받는 메서드는 {@link com.team.tetris.common.events.GameEventListener}와 이름이 같다.
 *
 * 모든 메서드는 EDT에서 부른다. 연출 타이머가 돌 때마다 생성자에 넘긴 {@code repaint}를 부른다.
 */
final class BoardView {
    /** 줄 삭제 팝 단계. 프레임 간격(65ms)마다 하나씩 넘어가며, 마지막 단계 뒤에 압축된 보드를 보여 준다. */
    private enum LineClearStage { POP, POP_HOLD, FADE, CLEARED }
    private static final LineClearStage[] LINE_CLEAR_STAGES = LineClearStage.values();
    /** 아이템 토스트 종류. 토스트 장식과 화면의 상태 뱃지·색조를 정한다. */
    enum ItemEffectKind { FEVER, SLOW, BOMB, ANVIL }

    static final Color SLOW_COLOR = new Color(0x5FD8F0);
    private static final int LINE_CLEAR_FLASH_INTERVAL_MS = 65;
    /** 하드드롭 잔상·착지 눌림·흔들림이 보이는 시간 (1~2프레임). */
    private static final int HARD_DROP_EFFECT_MS = 110;
    /** 잔상 개수와 각 잔상의 불투명도. 착지 위치에 가까울수록 진하다. */
    private static final float[] TRAIL_ALPHAS = {0.15f, 0.30f, 0.50f};
    // 폭탄·무게추 연출 (컴포넌트 시트 "폭탄 폭발 이펙트"·"무게추 분쇄 이펙트", BombScreen·AnvilScreen 목업).
    /** 폭탄: 범위 칸이 흰/노랑 체커로 번쩍이는 시간. 이후 빈칸에 회색 연기 점이 남는다. */
    private static final int BOMB_FLASH_MS = 120;
    /** 폭탄 연출 전체 시간 (번쩍임 + 연기 점). */
    private static final int BOMB_EFFECT_MS = 330;
    /** 무게추: 부순 칸 자리에 연기 퍼프가 뜨는 시간. 이후 작은 잔연기 점으로 바뀐다. */
    private static final int ANVIL_PUFF_MS = 200;
    /** 무게추 연출 전체 시간 (퍼프 + 잔연기). */
    private static final int ANVIL_EFFECT_MS = 380;
    /** 무게추 연기 색. steel 받침 위에 겹쳐도 보이도록 밝은 회색으로 둔다. */
    private static final Color ANVIL_SMOKE = new Color(0xC9C4D6);
    private static final int BOARD_EFFECT_TICK_MS = 30;
    /** 회색 연기 색 (컴포넌트 시트: 회색 연기 퍼프, 알파 0.5→0.2). */
    private static final Color SMOKE = UiColors.GARBAGE;
    /** BOMB! 토스트 색 (BombScreen 목업: 빨간 테두리·제목). */
    private static final Color BOMB_TOAST_COLOR = UiColors.PLAYER2;
    /** ANVIL! 토스트 색 (AnvilScreen 목업: 회색 테두리). */
    private static final Color ANVIL_TOAST_COLOR = new Color(0xC9C4D6);
    private static final int BOMB_NOTIFICATION_MS = 1_600;
    private static final int ANVIL_NOTIFICATION_MS = 1_600;
    private static final int FEVER_NOTIFICATION_MS = 2_700;
    private static final int SLOW_DOWN_NOTIFICATION_MS = 2_000;
    private static final int ITEM_EFFECT_TICK_MS = 30;
    static final int ITEM_EFFECT_FADE_MS = 450;

    private final Runnable repaint;
    private final ItemRegistry items;
    private ColorScheme colorScheme = ColorScheme.standard();

    // 아래 값은 렌더링 전용 상태이며, 엔진 상태의 복사본이다.
    private int[][] board = emptyBoard();
    private int[][] pendingBoard;
    private int[][] pendingFalling;
    private int[][] landing;
    private int[][] pendingLanding;
    private int[] flashingRows = new int[0];
    private int flashFrame;
    private boolean lineClearAnimating;
    private final Timer lineClearTimer;

    private int[][] dropTrail;
    private int dropDistance;
    private boolean[][] squashedCells;
    private final Timer hardDropTimer;

    /** 폭탄이 터진 칸(폭탄 자리)과 지운 칸. 연출이 끝나면 null이다. */
    private CellPosition bombCenter;
    private List<CellPosition> bombCells;
    /** 폭발 범위를 감싸는 점선 박스 {위 행, 왼쪽 열, 아래 행, 오른쪽 열}. */
    private int[] bombArea;
    /** 폭발이 시작된 시각({@link #clock}, ns). */
    private long bombStartNanos;
    /** 지금 떨어지는 블록이 무게추인지. false→true로 바뀌는 순간 ANVIL! 토스트를 띄운다. */
    private boolean anvilFalling;
    /** 지금 떨어지는 블록에 폭탄이 붙어 있는지. false→true로 바뀌는 순간 BOMB! 토스트를 띄운다. */
    private boolean bombFalling;
    /** 무게추가 방금 부순 칸. 연출이 끝나면 null이다. */
    private List<CellPosition> anvilCells;
    private long anvilStartNanos;
    // 폭탄·무게추 연출이 진행되는 동안에만 도는 타이머. 둘 다 끝나면 스스로 멈춘다(상시 루프 아님).
    private final Timer boardEffectTimer;

    private String itemEffectTitle;
    private String itemEffectSubtitle;
    private Color itemEffectColor;
    /** 지금 떠 있는 아이템 토스트 종류. 슬로우다운 색조는 토스트가 떠 있는 동안만 보인다. */
    private ItemEffectKind itemEffectKind;
    private long itemEffectStartNanos;
    private int itemEffectDurationMs;

    /**
     * 연출 경과 시간을 재는 시계(ns). 타이머 틱 수로 세면 느린 PC에서 틱이 늦거나 합쳐질 때 연출이 원래보다 오래 남으므로,
     * 실제 시각으로 잰다. 테스트는 정해 둔 시각을 돌려주는 시계로 바꿔 기기 속도와 상관없이 확인한다.
     */
    private static volatile LongSupplier clock = System::nanoTime;

    static void setClockForTests(LongSupplier testClock) {
        clock = testClock == null ? System::nanoTime : testClock;
    }

    private static long now() {
        return clock.getAsLong();
    }

    private static int elapsedMs(long startNanos) {
        return (int) Math.min(Integer.MAX_VALUE, Math.max(0L, (now() - startNanos) / 1_000_000L));
    }
    private final Timer itemEffectTimer;

    /**
     * @param repaint 연출 상태가 바뀔 때 부를 다시 그리기 동작. 보드가 있는 영역만 다시 그려도 된다.
     * @param items 블록형 아이템의 미리보기 모양을 문자로 찾는 데 쓴다. 게임 쪽과 같은 목록을 전달한다.
     */
    BoardView(Runnable repaint, ItemRegistry items) {
        this.repaint = Objects.requireNonNull(repaint, "repaint");
        this.items = Objects.requireNonNull(items, "items");
        lineClearTimer = new Timer(LINE_CLEAR_FLASH_INTERVAL_MS, event -> advanceLineClearAnimation());
        hardDropTimer = new Timer(HARD_DROP_EFFECT_MS, event -> {
            clearHardDropEffect();
            repaint.run();
        });
        hardDropTimer.setRepeats(false);
        boardEffectTimer = new Timer(BOARD_EFFECT_TICK_MS, event -> advanceBoardEffects());
        itemEffectTimer = new Timer(ITEM_EFFECT_TICK_MS, event -> advanceItemEffectAnimation());
    }

    void setColorScheme(ColorScheme scheme) {
        colorScheme = Objects.requireNonNull(scheme, "colorScheme");
        repaint.run();
    }

    /** 새 판을 시작할 때 이전 판의 보드와 연출을 모두 지운다. */
    void reset() {
        lineClearTimer.stop();
        itemEffectTimer.stop();
        clearHardDropEffect();
        boardEffectTimer.stop();
        anvilFalling = false;
        bombFalling = false;
        bombCenter = null;
        bombCells = null;
        bombArea = null;
        anvilCells = null;
        flashingRows = new int[0];
        lineClearAnimating = false;
        itemEffectTitle = null;
        itemEffectSubtitle = null;
        itemEffectColor = null;
        itemEffectKind = null;
        itemEffectDurationMs = 0;
        pendingBoard = null;
        pendingFalling = null;
        landing = null;
        pendingLanding = null;
        board = emptyBoard();
    }

    /** 지금 보드에 보이는 칸 (줄 삭제 팝 중이면 지우기 전 보드). 대전 화면이 삽입 줄 위치를 찾는 데 쓴다. */
    int[][] cells() {
        return board;
    }

    // ---- 엔진 알림 ----

    void onBoardUpdated(BoardSnapshot snapshot) {
        int[][] cells = copyBoard(snapshot);
        if (cells == null) return;
        if (lineClearAnimating) {
            pendingBoard = cells;
        } else {
            board = cells;
        }
        repaint.run();
    }

    void onLandingUpdated(BoardSnapshot fallingPiece, BoardSnapshot landingPiece) {
        int[][] fallingCells = fallingPiece == null ? null : copyBoard(fallingPiece);
        int[][] landingCells = landingPiece == null ? null : copyBoard(landingPiece);
        if ((fallingPiece != null && fallingCells == null) || (landingPiece != null && landingCells == null)) {
            return;
        }
        boolean anvilFalls = containsAnvil(fallingCells);
        boolean bombFalls = containsSymbol(fallingCells, BombItem.SYMBOL);
        if (lineClearAnimating) {
            pendingFalling = fallingCells;
            pendingLanding = landingCells;
        } else {
            landing = landingCells;
        }
        // 폭탄·무게추 블록이 나타나는 순간 토스트를 한 번 띄운다.
        // 폭탄이 터지거나 무게추가 블록을 부술 때는 다시 띄우지 않는다.
        if (anvilFalls && !anvilFalling) {
            showItemEffect("ANVIL!", "지나가는 줄의 블럭 분쇄  ·  점수 없음", ANVIL_TOAST_COLOR,
                    ANVIL_NOTIFICATION_MS, ItemEffectKind.ANVIL);
        }
        if (bombFalls && !bombFalling) {
            showItemEffect("BOMB!", "주변 블럭 제거  ·  점수 없음", BOMB_TOAST_COLOR,
                    BOMB_NOTIFICATION_MS, ItemEffectKind.BOMB);
        }
        anvilFalling = anvilFalls;
        bombFalling = bombFalls;
        repaint.run();
    }

    /** @param beforeClear 지우기 전 보드. null이면 지금 보드에서 팝한다. */
    void onLinesCleared(List<Integer> rows, BoardSnapshot beforeClear) {
        int[] copy = Objects.requireNonNull(rows, "rows").stream()
                .mapToInt(row -> Objects.requireNonNull(row, "row"))
                .toArray();
        for (int row : copy) {
            if (row < 0 || row >= GameConstants.BOARD_ROWS) {
                throw new IllegalArgumentException("Cleared row is outside the game board: " + row);
            }
        }
        int[][] before = null;
        if (beforeClear != null) {
            before = copyBoard(beforeClear);
            if (before == null) {
                throw new IllegalArgumentException("Pre-clear board does not match the game board dimensions");
            }
        }
        if (before != null) board = before;
        pendingBoard = null;
        pendingFalling = null;
        landing = null;
        pendingLanding = null;
        flashingRows = copy;
        flashFrame = 0;
        lineClearAnimating = true;
        lineClearTimer.restart();
        repaint.run();
    }

    void onFeverActivated(int seconds) {
        showItemEffect("FEVER TIME!", "2X SCORE  ·  " + seconds + " SECONDS",
                UiColors.ACCENT, FEVER_NOTIFICATION_MS, ItemEffectKind.FEVER);
    }

    void onSlowDownActivated(int previousLevel, int currentLevel) {
        String title = currentLevel < previousLevel ? "SLOW DOWN" : "MIN SPEED";
        String subtitle = currentLevel < previousLevel
                ? "LEVEL " + previousLevel + "  →  " + currentLevel
                : "LEVEL " + currentLevel + " CANNOT DROP";
        showItemEffect(title, subtitle, SLOW_COLOR, SLOW_DOWN_NOTIFICATION_MS, ItemEffectKind.SLOW);
    }

    /**
     * 하드드롭 연출(스펙 §3.3)을 시작한다: 지나온 경로의 잔상 + 착지 칸 눌림 + 보드 1px 흔들림.
     * 한 번만 울리는 타이머가 끝나면 지우므로 상시 애니메이션 루프가 없다.
     */
    void onHardDropped(BoardSnapshot fallingBefore, BoardSnapshot fallingAfter) {
        int[][] from = copyBoard(Objects.requireNonNull(fallingBefore, "fallingBefore"));
        int[][] to = copyBoard(Objects.requireNonNull(fallingAfter, "fallingAfter"));
        if (from == null || to == null) return;
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
        repaint.run();
    }

    void onBombExploded(CellPosition center, List<CellPosition> blastCells) {
        Objects.requireNonNull(center, "center");
        List<CellPosition> cells = List.copyOf(Objects.requireNonNull(blastCells, "blastCells"));
        // 꽉 찬 행은 폭발 범위에서 빠지므로, 점선 박스는 중심을 포함한 칸들을 감싸는 사각형으로 그린다.
        int top = center.row();
        int bottom = center.row();
        int left = center.col();
        int right = center.col();
        for (CellPosition cell : cells) {
            top = Math.min(top, cell.row());
            bottom = Math.max(bottom, cell.row());
            left = Math.min(left, cell.col());
            right = Math.max(right, cell.col());
        }
        bombCenter = center;
        bombCells = cells;
        bombArea = new int[]{Math.max(0, top), Math.max(0, left),
                Math.min(GameConstants.BOARD_ROWS - 1, bottom), Math.min(GameConstants.BOARD_COLS - 1, right)};
        bombStartNanos = now();
        restartBoardEffectTimer();
        repaint.run();
    }

    void onAnvilCrushed(List<CellPosition> crushedCells) {
        List<CellPosition> cells = List.copyOf(Objects.requireNonNull(crushedCells, "crushedCells"));
        if (cells.isEmpty()) return;
        anvilCells = cells;
        anvilStartNanos = now();
        restartBoardEffectTimer();
        repaint.run();
    }

    // ---- 화면이 읽는 연출 상태 ----

    /** 하드드롭 착지 순간이면 보드를 흔들 만큼(px), 아니면 0. */
    int shakeOffset(float scale) {
        return squashedCells != null ? Math.max(1, Math.round(scale)) : 0;
    }

    /** 슬로우다운 토스트가 떠 있는 동안 true. 보드 색조와 헤더 뱃지에 쓴다. */
    boolean slowDownShowing() {
        return itemEffectKind == ItemEffectKind.SLOW;
    }

    // ---- 그리기 ----

    void paintBoard(Graphics2D g2d, int x, int y, int cellSize) {
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
        drawBombBlast(g2d, x, y, cellSize);
        drawAnvilPuffs(g2d, x, y, cellSize);

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
     * 아이템 토스트: 보드 위쪽 가운데의 패널 카드 + 상태색 테두리 + Jua 제목 + 고정폭 부제.
     * 피버는 제목 양옆에 별, 슬로우다운은 제목 앞에 ▼를 도형으로 그린다.
     */
    void paintToast(Graphics2D g2d, int boardX, int boardY, int boardWidth, int boardHeight, float scale) {
        if (itemEffectTitle == null || elapsedMs(itemEffectStartNanos) >= itemEffectDurationMs) return;
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

    /**
     * NEXT 미리보기 한 칸. 블록 모양을 영역 가운데에 그린다.
     *
     * @param maxCell 미리보기 칸 한 변의 최대 크기(px)
     */
    void paintPreview(Graphics2D g2d, int code, int x, int y, int width, int height, int maxCell) {
        TetrominoType type = typeFromCode(code);
        if (type == null) return;
        char symbol = CellCodes.symbol(code);
        int itemIndex = CellCodes.itemCellIndex(code);
        // 문자가 있는데 붙은 칸 번호가 없으면 블록 전체가 아이템인 블록형이다.
        boolean wholePieceItem = symbol != CellCodes.NO_SYMBOL && itemIndex == CellCodes.NO_INDEX;
        List<Cell> occupied = wholePieceItem
                ? standaloneItemShape(symbol)
                : new Tetromino(type, Rotation.SPAWN, 0, 0).cells();
        drawPreviewShape(g2d, occupied, code, symbol, itemIndex, x, y, width, height, maxCell);
    }

    // ---- 연출 진행 ----

    private void showItemEffect(String title, String subtitle, Color color, int durationMs, ItemEffectKind kind) {
        itemEffectTitle = title;
        itemEffectSubtitle = subtitle;
        itemEffectColor = color;
        itemEffectKind = kind;
        itemEffectStartNanos = now();
        itemEffectDurationMs = durationMs;
        itemEffectTimer.restart();
        repaint.run();
    }

    private void advanceItemEffectAnimation() {
        if (elapsedMs(itemEffectStartNanos) >= itemEffectDurationMs) {
            itemEffectTitle = null;
            itemEffectSubtitle = null;
            itemEffectColor = null;
            itemEffectKind = null;
            itemEffectTimer.stop();
        }
        repaint.run();
    }

    private float itemEffectFade() {
        if (itemEffectDurationMs <= 0) return 0f;
        int fadeStart = itemEffectDurationMs - ITEM_EFFECT_FADE_MS;
        int elapsed = elapsedMs(itemEffectStartNanos);
        if (elapsed <= fadeStart) return 1f;
        return Math.max(0f, (float) (itemEffectDurationMs - elapsed) / ITEM_EFFECT_FADE_MS);
    }

    private void advanceLineClearAnimation() {
        if (flashFrame + 1 >= LINE_CLEAR_STAGES.length) {
            flashingRows = new int[0];
            lineClearAnimating = false;
            if (pendingBoard != null) {
                board = pendingBoard;
                pendingBoard = null;
            }
            pendingFalling = null;
            landing = pendingLanding;
            pendingLanding = null;
            lineClearTimer.stop();
        } else {
            flashFrame++;
        }
        repaint.run();
    }

    private void clearHardDropEffect() {
        hardDropTimer.stop();
        dropTrail = null;
        squashedCells = null;
        dropDistance = 0;
    }

    private void restartBoardEffectTimer() {
        if (!boardEffectTimer.isRunning()) boardEffectTimer.start();
    }

    private void advanceBoardEffects() {
        if (bombCells != null) {
            if (elapsedMs(bombStartNanos) >= BOMB_EFFECT_MS) {
                bombCenter = null;
                bombCells = null;
                bombArea = null;
            }
        }
        if (anvilCells != null) {
            if (elapsedMs(anvilStartNanos) >= ANVIL_EFFECT_MS) anvilCells = null;
        }
        if (bombCells == null && anvilCells == null) boardEffectTimer.stop();
        repaint.run();
    }

    // ---- 칸 그리기 ----

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

    /** 체커 무늬: 폭탄 칸에서 짝수 칸 떨어진 칸(모서리·가운데)은 노랑, 나머지(변 가운데)는 흰색 (목업 배치). */
    private boolean isBombCorner(CellPosition cell) {
        return (Math.abs(cell.row() - bombCenter.row()) + Math.abs(cell.col() - bombCenter.col())) % 2 == 0;
    }

    /**
     * 폭탄 폭발 (컴포넌트 시트 "폭탄 폭발 이펙트", BombScreen 목업).
     * ② 폭발 플래시: 범위 칸을 흰(cream)/노랑(accent) 체커로 칠하고, 폭탄 자리에 폭탄 아이콘, 범위를 감싸는
     * accent 점선 박스, 박스 모서리 근처에 ✦ 3개. ③ 다음 순간: 빈칸 위에 회색 연기 점이 남았다가 사라진다.
     */
    private void drawBombBlast(Graphics2D g2d, int x, int y, int pitch) {
        if (bombCells == null) return;
        int bombElapsedMs = elapsedMs(bombStartNanos);
        if (bombElapsedMs >= BOMB_EFFECT_MS) return;   // 타이머가 지우기 전이어도 시간이 지났으면 그리지 않는다
        int inset = cellInset(pitch);
        int size = pitch - inset * 2;
        Graphics2D blast = (Graphics2D) g2d.create();
        try {
            if (bombElapsedMs < BOMB_FLASH_MS) {
                int arc = CellPainter.radius(size) * 2;
                for (CellPosition cell : bombCells) {
                    if (cell.equals(bombCenter)) continue;
                    blast.setColor(isBombCorner(cell) ? UiColors.ACCENT : UiColors.CREAM);
                    blast.fillRoundRect(x + cell.col() * pitch + inset, y + cell.row() * pitch + inset, size, size,
                            arc, arc);
                }
                CellPainter.drawBombItem(blast, x + bombCenter.col() * pitch + inset,
                        y + bombCenter.row() * pitch + inset, size, UiColors.ACCENT, BombItem.SYMBOL);

                int left = x + bombArea[1] * pitch;
                int top = y + bombArea[0] * pitch;
                int width = (bombArea[3] - bombArea[1] + 1) * pitch;
                int height = (bombArea[2] - bombArea[0] + 1) * pitch;
                float dash = Math.max(3f, pitch * 0.2f);
                blast.setStroke(new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND, 10f,
                        new float[]{dash, dash * 0.7f}, 0f));
                blast.setColor(UiColors.ACCENT);
                int boxArc = CellPainter.radius(pitch) * 2;
                blast.drawRoundRect(left, top, width, height, boxArc, boxArc);

                int sparkle = Math.max(4, Math.round(pitch * 0.22f));
                UiPainter.drawSparkle(blast, left, top, sparkle, UiColors.ACCENT);
                UiPainter.drawSparkle(blast, left + width + sparkle, top - sparkle / 2, sparkle, UiColors.PLAYER2);
                UiPainter.drawSparkle(blast, left + width, top + height, sparkle, UiColors.ACCENT);
            } else {
                // 연기 점: 알파 0.5에서 0.2로 옅어진다. 체커의 cream 칸 자리(가장자리 십자)에 찍는다.
                float progress = (float) (bombElapsedMs - BOMB_FLASH_MS) / (BOMB_EFFECT_MS - BOMB_FLASH_MS);
                blast.setColor(UiColors.withAlpha(SMOKE, 0.5f - 0.3f * Math.min(1f, progress)));
                int dot = Math.max(3, Math.round(size * 0.2f));
                for (CellPosition cell : bombCells) {
                    if (isBombCorner(cell) && !cell.equals(bombCenter)) continue;
                    int centerX = x + cell.col() * pitch + pitch / 2;
                    int centerY = y + cell.row() * pitch + pitch / 2;
                    blast.fillOval(centerX - dot / 2, centerY - dot / 2, dot, dot);
                }
            }
        } finally {
            blast.dispose();
        }
    }

    /**
     * 무게추 분쇄 (컴포넌트 시트 "무게추 분쇄 이펙트", AnvilScreen 목업).
     * ② 분쇄: 부순 칸 자리에 회색 연기 퍼프 2~3개(지름 칸×0.5~0.7).
     * ③ 잔연기: 작은 회색 점으로 남았다가 사라진다. 한 줄씩 내려갈 때마다 반복되어 "뚫고 내려가는" 느낌을 준다.
     */
    private void drawAnvilPuffs(Graphics2D g2d, int x, int y, int pitch) {
        if (anvilCells == null) return;
        int anvilElapsedMs = elapsedMs(anvilStartNanos);
        if (anvilElapsedMs >= ANVIL_EFFECT_MS) return;
        Graphics2D smoke = (Graphics2D) g2d.create();
        try {
            if (anvilElapsedMs < ANVIL_PUFF_MS) {
                // 받침과 겹쳐 그리되 steel 위에서도 보이도록 밝은 회색을 진하게 시작해 옅게 끝낸다 (0.85→0.35).
                float progress = (float) anvilElapsedMs / ANVIL_PUFF_MS;
                smoke.setColor(UiColors.withAlpha(ANVIL_SMOKE, 0.85f - 0.5f * progress));
                for (CellPosition cell : anvilCells) {
                    float left = x + cell.col() * pitch;
                    float top = y + cell.row() * pitch;
                    // {가로 중심, 세로 중심, 지름} (칸 크기 비율). 칸 아래쪽에 모여 받침 밑으로 살짝 삐져나온다.
                    float[][] puffs = cell.col() % 2 == 0
                            ? new float[][]{{0.30f, 0.70f, 0.62f}, {0.78f, 0.82f, 0.50f}}
                            : new float[][]{{0.50f, 0.62f, 0.70f}, {0.18f, 0.88f, 0.50f}, {0.84f, 0.50f, 0.50f}};
                    for (float[] puff : puffs) {
                        int diameter = Math.round(pitch * puff[2]);
                        smoke.fillOval(Math.round(left + pitch * puff[0] - diameter / 2f),
                                Math.round(top + pitch * puff[1] - diameter / 2f), diameter, diameter);
                    }
                }
            } else {
                float progress = (float) (anvilElapsedMs - ANVIL_PUFF_MS) / (ANVIL_EFFECT_MS - ANVIL_PUFF_MS);
                smoke.setColor(UiColors.withAlpha(ANVIL_SMOKE, 0.6f - 0.4f * Math.min(1f, progress)));
                int dot = Math.max(3, Math.round(pitch * 0.2f));
                for (CellPosition cell : anvilCells) {
                    int centerX = x + cell.col() * pitch + pitch / 2;
                    int centerY = y + cell.row() * pitch + Math.round(pitch * 0.85f);
                    smoke.fillOval(centerX - dot / 2, centerY - dot / 2, dot, dot);
                }
            }
        } finally {
            smoke.dispose();
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

    private void drawBoardCell(Graphics2D g2d, int code, int cellX, int cellY, int pitch) {
        int inset = cellInset(pitch);
        // 회색 공격줄 칸은 isValid가 false이므로 먼저 확인한다 (대전 인계서 2절).
        if (CellCodes.isGarbage(code)) {
            CellPainter.drawGarbage(g2d, cellX + inset, cellY + inset, pitch - inset * 2);
            return;
        }
        // 빈칸·잘못된 코드는 symbol()이 예외를 던지므로 먼저 걸러 낸다.
        if (!CellCodes.isValid(code)) return;
        drawCell(g2d, code, CellCodes.symbol(code), cellX + inset, cellY + inset, pitch - inset * 2);
    }

    /**
     * 셀 코드 하나를 그린다. 셀 코드는 색을 빌려 올 블록 종류와 아이템 문자를 함께 담는다.
     * {@code symbol}이 없으면 표정 있는 기본 블럭, 있으면 아이템 칸으로 그린다.
     */
    private void drawCell(Graphics2D g2d, int code, char symbol, int x, int y, int size) {
        TetrominoType type = typeFromCode(code);
        if (type == null) return;
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

    private void drawLandingGhost(Graphics2D g2d, int x, int y, int cellSize) {
        int inset = cellInset(cellSize);
        for (int row = 0; row < landing.length; row++) {
            for (int col = 0; col < landing[row].length; col++) {
                TetrominoType type = typeFromCode(landing[row][col]);
                // 착지 위치가 낙하 중인 블록과 겹치는 칸은 블록 위에 테두리를 덧그리지 않는다.
                if (type == null || board[row][col] != BoardSnapshot.EMPTY) continue;
                CellPainter.drawGhost(g2d, x + col * cellSize + inset, y + row * cellSize + inset,
                        cellSize - inset * 2);
            }
        }
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
            char symbol, int itemIndex, int x, int y, int width, int height, int maxCell) {
        int minRow = occupied.stream().mapToInt(Cell::row).min().orElse(0);
        int maxRow = occupied.stream().mapToInt(Cell::row).max().orElse(0);
        int minCol = occupied.stream().mapToInt(Cell::col).min().orElse(0);
        int maxCol = occupied.stream().mapToInt(Cell::col).max().orElse(0);
        int previewCellSize = Math.min(maxCell, Math.min(
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

    // ---- 공용 도우미 ----

    /** 칸 사이 간격. 셀을 칸보다 조금 작게 그려 뿌요처럼 낱알이 구분되게 한다. */
    static int cellInset(int pitch) {
        return Math.max(1, Math.round(pitch * 0.04f));
    }

    /** 빈칸이나 규칙에 없는 코드는 null. 잘못된 코드 하나 때문에 전체 화면 그리기가 중단되지 않게 한다. */
    static TetrominoType typeFromCode(int code) {
        if (!CellCodes.isValid(code)) return null;
        return TetrominoType.fromId(CellCodes.baseType(code));
    }

    static boolean isAnvil(int code) {
        return CellCodes.isValid(code) && CellCodes.symbol(code) == AnvilItem.SYMBOL;
    }

    private static boolean containsAnvil(int[][] cells) {
        if (cells == null) return false;
        for (int[] row : cells) {
            for (int code : row) {
                if (isAnvil(code)) return true;
            }
        }
        return false;
    }

    private static boolean containsSymbol(int[][] cells, char symbol) {
        if (cells == null) return false;
        for (int[] row : cells) {
            for (int code : row) {
                if (CellCodes.isValid(code) && CellCodes.symbol(code) == symbol) return true;
            }
        }
        return false;
    }

    private static int topRow(int[][] cells) {
        for (int row = 0; row < cells.length; row++) {
            for (int value : cells[row]) {
                if (value != BoardSnapshot.EMPTY) return row;
            }
        }
        return -1;
    }

    /** 엔진이 원본 배열을 재사용해도 화면 상태가 바뀌지 않게 행까지 복사한다. 20×10이 아니면 null. */
    static int[][] copyBoard(BoardSnapshot snapshot) {
        Objects.requireNonNull(snapshot, "snapshot");
        int[][] source = snapshot.cells();
        if (source.length != GameConstants.BOARD_ROWS) return null;
        int[][] copy = new int[source.length][];
        for (int row = 0; row < source.length; row++) {
            if (source[row] == null || source[row].length != GameConstants.BOARD_COLS) return null;
            copy[row] = source[row].clone();
        }
        return copy;
    }

    static int[][] emptyBoard() {
        // 빈 칸은 0이 아니라 공용 스냅샷 계약의 EMPTY(-1)로 표현한다.
        int[][] cells = new int[GameConstants.BOARD_ROWS][GameConstants.BOARD_COLS];
        for (int[] row : cells) Arrays.fill(row, BoardSnapshot.EMPTY);
        return cells;
    }
}

package com.team.tetris.ui;

import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.ActionEvent;
import java.util.Arrays;
import java.util.Locale;
import java.util.Objects;
import java.util.function.IntConsumer;

import javax.swing.AbstractAction;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;

import com.team.tetris.block.ColorScheme;
import com.team.tetris.block.Rotation;
import com.team.tetris.block.Tetromino;
import com.team.tetris.block.TetrominoType;
import com.team.tetris.common.Screen;
import com.team.tetris.common.constants.GameConstants;
import com.team.tetris.common.events.BoardSnapshot;
import com.team.tetris.common.events.GameEventListener;
import com.team.tetris.game.GameSession;

/**
 * 게임 세션의 이벤트를 화면에 그려 주고, 키보드 입력을 세션 명령으로 전달한다.
 * 보드·점수·다음 블록의 실제 상태는 이 화면이 계산하지 않고 GameSession이 소유한다.
 */
public class GameScreen extends JPanel implements Screen {
    // 화면 크기와 관계없이 유지할 여백 및 상·하단 영역의 기준 크기다.
    private static final int PADDING = 16;
    private static final int PANEL_GAP = 16;
    private static final int HEADER_HEIGHT = 40;
    private static final int FOOTER_HEIGHT = 36;
    private static final Color BACKGROUND = new Color(ColorScheme.standard().emptyRgb());
    private static final Color GRID_COLOR = new Color(255, 255, 255, 35);

    // router는 화면 전환을, session은 게임 명령과 상태 이벤트를 담당한다.
    private final ScreenRouter router;
    private final GameSession session;
    // 게임 종료 점수를 결과 화면에 반영하는 책임은 앱 조립부에 위임한다.
    private final IntConsumer gameOverAction;

    // 엔진은 화면과 별도 스레드에서 이벤트를 보낼 수도 있다.
    // 보드 데이터는 이벤트가 끝난 뒤에도 안전하게 보관할 수 있도록 먼저 복사한다.
    private final GameEventListener eventListener = new GameEventListener() {
        @Override
        public void onBoardUpdated(BoardSnapshot snapshot) {
            int[][] cells = copyBoard(snapshot);
            if (cells == null) {
                return;
            }
            onEdt(() -> {
                board = cells;
                repaint();
            });
        }

        @Override
        public void onScoreChanged(int updatedScore) {
            onEdt(() -> {
                score = updatedScore;
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
            onEdt(() -> paused = isPaused);
        }

        @Override
        public void onGameOver(int finalScore) {
            onEdt(() -> {
                if (gameOver) {
                    return;
                }
                gameOver = true;
                score = finalScore;
                gameOverAction.accept(finalScore);
            });
        }
    };

    // 아래 값은 렌더링 전용 화면 상태이며, 엔진 상태의 복사본이다.
    private int[][] board = emptyBoard();
    private int[] nextBlocks = new int[0];
    private int score;
    private ColorScheme colorScheme = ColorScheme.standard();
    private boolean listenerRegistered;
    private boolean sessionStarted;
    private boolean paused = true;
    private boolean gameOver;

    /**
        * @param router 화면 전환을 관리하는 라우터
        * @param session 게임 입력을 처리하고 상태 이벤트를 발행하는 세션
        * @param gameOverAction 최종 점수를 전달받아 결과 화면을 설정하고 전환할 동작
     */
    public GameScreen(ScreenRouter router, GameSession session, IntConsumer gameOverAction) {
        this.router = Objects.requireNonNull(router, "router");
        this.session = Objects.requireNonNull(session, "session");
        this.gameOverAction = Objects.requireNonNull(gameOverAction, "gameOverAction");

        setBackground(BACKGROUND);
        setOpaque(true);
        setFocusable(true);
        installKeyBindings();
    }

    @Override
    public JPanel getPanel() {
        return this;
    }

    @Override
    public void onShow() {
        registerListener();
        requestFocusInWindow();

        // 최초 진입 또는 게임 오버 뒤 재진입은 새 게임으로 처리한다.
        if (!sessionStarted || gameOver) {
            resetViewState();
            gameOver = false;
            sessionStarted = true;
            paused = false;
            session.startNewGame();
        } else if (paused) {
            // 일시정지 화면에서 돌아온 경우 기존 판을 유지한 채 다시 진행한다.
            paused = false;
            session.resume();
        }
        repaint();
    }

    @Override
    public void onHide() {
        // 다른 화면으로 이동할 때 진행 중인 판을 멈춰 숨은 화면의 타이머가 계속 돌지 않게 한다.
        if (sessionStarted && !gameOver) {
            paused = true;
            session.pause();
        }
        unregisterListener();
    }

    /** 시작 메뉴의 새 게임 동작에서 호출해 이전 상태를 버리고 새 판을 시작한다. */
    public void startNewGame() {
        onEdt(() -> {
            registerListener();
            resetViewState();
            gameOver = false;
            sessionStarted = true;
            paused = false;
            session.startNewGame();
            if (!ScreenRouter.GAME.equals(router.getCurrentScreenName())) {
                router.showScreen(ScreenRouter.GAME);
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
            setBackground(new Color(updatedScheme.emptyRgb()));
            repaint();
        });
    }

    private void installKeyBindings() {
        // 포커스가 자식 컴포넌트로 이동해도 게임 조작 키를 받을 수 있도록 창 범위에 등록한다.
        bindKey("moveLeft", KeyStroke.getKeyStroke("LEFT"), session::moveLeft);
        bindKey("moveRight", KeyStroke.getKeyStroke("RIGHT"), session::moveRight);
        bindKey("moveDown", KeyStroke.getKeyStroke("DOWN"), session::moveDown);
        bindKey("rotate", KeyStroke.getKeyStroke("UP"), session::rotateClockwise);
        bindKey("hardDrop", KeyStroke.getKeyStroke("SPACE"), session::hardDrop);
        bindKey("pauseP", KeyStroke.getKeyStroke("P"), this::showPauseScreen);
        bindKey("pauseEscape", KeyStroke.getKeyStroke("ESCAPE"), this::showPauseScreen);
    }

    private void bindKey(String name, KeyStroke keyStroke, Runnable command) {
        // KeyListener 대신 Swing 키 바인딩을 사용해 포커스 변화에 덜 민감하게 입력을 처리한다.
        getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(keyStroke, name);
        getActionMap().put(name, new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent event) {
                // 시작 전·일시정지·게임 종료 상태에서는 이동 명령을 세션으로 보내지 않는다.
                if (sessionStarted && !paused && !gameOver) {
                    command.run();
                }
            }
        });
    }

    private void showPauseScreen() {
        // 화면 전환 과정에서 onHide()가 세션을 멈추고, 복귀 시 onShow()가 다시 시작한다.
        router.showScreen(ScreenRouter.PAUSE);
    }

    private void registerListener() {
        // 화면 재진입이 반복되어도 같은 리스너가 중복 등록되지 않도록 상태를 추적한다.
        if (!listenerRegistered) {
            session.addListener(eventListener);
            listenerRegistered = true;
        }
    }

    private void unregisterListener() {
        // 화면이 숨겨진 동안에는 불필요한 UI 갱신과 리스너 참조가 남지 않도록 해제한다.
        if (listenerRegistered) {
            session.removeListener(eventListener);
            listenerRegistered = false;
        }
    }

    private void resetViewState() {
        // 새 게임 시작 시 이전 판에서 전달받은 표시 데이터만 초기화한다.
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
            g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

            int width = getWidth();
            int height = getHeight();
            int sideWidth = Math.min(144, Math.max(104, width / 3));
            // 보드와 우측 정보 패널이 사용할 공간을 나눈 뒤, 보드 셀은 가로·세로에 모두 맞춘다.
            int boardAreaWidth = width - (PADDING * 2) - sideWidth - PANEL_GAP;
            int boardAreaHeight = height - HEADER_HEIGHT - FOOTER_HEIGHT - (PADDING * 2);
            int cellSize = Math.max(8, Math.min(
                    boardAreaWidth / GameConstants.BOARD_COLS,
                    boardAreaHeight / GameConstants.BOARD_ROWS));
            int boardWidth = cellSize * GameConstants.BOARD_COLS;
            int boardHeight = cellSize * GameConstants.BOARD_ROWS;
            int boardX = PADDING + Math.max(0, (boardAreaWidth - boardWidth) / 2);
            int boardY = HEADER_HEIGHT + PADDING + Math.max(0, (boardAreaHeight - boardHeight) / 2);
            int sideX = PADDING + boardAreaWidth + PANEL_GAP;

            g2d.setColor(Color.WHITE);
            g2d.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 20));
            g2d.drawString("TETRIS", PADDING, 27);

            drawBoard(g2d, boardX, boardY, cellSize);
            drawSidebar(g2d, sideX, HEADER_HEIGHT + PADDING, sideWidth);

            g2d.setColor(new Color(185, 193, 204));
            g2d.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 11));
            String help = "← → Move   ↓ Drop   ↑ Rotate   Space Hard Drop   P / Esc Pause";
            FontMetrics metrics = g2d.getFontMetrics();
            int helpX = Math.max(PADDING, (width - metrics.stringWidth(help)) / 2);
            g2d.drawString(help, helpX, height - 12);
        } finally {
            // create()로 만든 Graphics 복사본은 사용 후 반드시 해제한다.
            g2d.dispose();
        }
    }

    private void drawBoard(Graphics2D g2d, int x, int y, int cellSize) {
        int width = cellSize * GameConstants.BOARD_COLS;
        int height = cellSize * GameConstants.BOARD_ROWS;
        g2d.setColor(new Color(colorScheme.emptyRgb()));
        g2d.fillRect(x, y, width, height);

        // 스냅샷의 숫자 ID를 종류로 바꿔 팔레트 색상과 블록 식별 문자를 함께 그린다.
        for (int row = 0; row < board.length; row++) {
            for (int col = 0; col < board[row].length; col++) {
                int blockId = board[row][col];
                if (blockId != BoardSnapshot.EMPTY) {
                    TetrominoType type = typeFromId(blockId);
                    if (type != null) {
                        drawBlockCell(g2d, x + col * cellSize, y + row * cellSize,
                                cellSize, type, colorScheme);
                    }
                }
            }
        }

        g2d.setColor(GRID_COLOR);
        for (int col = 0; col <= GameConstants.BOARD_COLS; col++) {
            int lineX = x + col * cellSize;
            g2d.drawLine(lineX, y, lineX, y + height);
        }
        for (int row = 0; row <= GameConstants.BOARD_ROWS; row++) {
            int lineY = y + row * cellSize;
            g2d.drawLine(x, lineY, x + width, lineY);
        }
        g2d.setColor(new Color(185, 193, 204));
        g2d.drawRect(x, y, width, height);
    }

    private void drawSidebar(Graphics2D g2d, int x, int y, int width) {
        g2d.setColor(new Color(190, 198, 208));
        g2d.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 14));
        g2d.drawString("NEXT", x, y + 14);

        int previewY = y + 24;
        int previewHeight = 76;
        // 미리보기 큐가 더 길어지더라도 현재 설정된 표시 개수만큼만 그린다.
        int previewCount = Math.min(nextBlocks.length, GameConstants.NEXT_QUEUE_SIZE);
        for (int index = 0; index < previewCount; index++) {
            g2d.setColor(new Color(255, 255, 255, 24));
            g2d.fillRoundRect(x, previewY, width, previewHeight, 6, 6);
            g2d.setColor(new Color(255, 255, 255, 60));
            g2d.drawRoundRect(x, previewY, width, previewHeight, 6, 6);
            drawPreview(g2d, nextBlocks[index], x, previewY, width, previewHeight);
            previewY += previewHeight + 10;
        }

        int scoreY = Math.max(previewY + 14, y + 142);
        g2d.setColor(new Color(190, 198, 208));
        g2d.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 14));
        g2d.drawString("SCORE", x, scoreY);
        g2d.setColor(Color.WHITE);
        g2d.setFont(new Font(Font.MONOSPACED, Font.BOLD, 20));
        g2d.drawString(String.format(Locale.ROOT, "%06d", score), x, scoreY + 29);
    }

    private void drawPreview(Graphics2D g2d, int blockId, int x, int y, int width, int height) {
        TetrominoType type = typeFromId(blockId);
        if (type == null) {
            return;
        }

        // 각 블록의 회전 격자에는 빈 테두리가 있을 수 있으므로 실제 점유 셀 범위만 찾아 중앙에 맞춘다.
        Tetromino piece = new Tetromino(type, Rotation.SPAWN, 0, 0);
        int minRow = piece.cells().stream().mapToInt(cell -> cell.row()).min().orElse(0);
        int maxRow = piece.cells().stream().mapToInt(cell -> cell.row()).max().orElse(0);
        int minCol = piece.cells().stream().mapToInt(cell -> cell.col()).min().orElse(0);
        int maxCol = piece.cells().stream().mapToInt(cell -> cell.col()).max().orElse(0);
        int previewCellSize = Math.min(16, Math.min(
                (width - 20) / (maxCol - minCol + 1),
                (height - 12) / (maxRow - minRow + 1)));
        int shapeWidth = previewCellSize * (maxCol - minCol + 1);
        int shapeHeight = previewCellSize * (maxRow - minRow + 1);
        int shapeX = x + (width - shapeWidth) / 2;
        int shapeY = y + (height - shapeHeight) / 2;

        for (var cell : piece.cells()) {
            int cellX = shapeX + (cell.col() - minCol) * previewCellSize;
            int cellY = shapeY + (cell.row() - minRow) * previewCellSize;
            drawBlockCell(g2d, cellX, cellY, previewCellSize, type, colorScheme);
        }
    }

    private static void drawBlockCell(Graphics2D g2d, int x, int y, int size,
            TetrominoType type, ColorScheme scheme) {
        Color blockColor = new Color(scheme.rgbOf(type));
        g2d.setColor(blockColor);
        g2d.fillRect(x + 1, y + 1, Math.max(1, size - 2), Math.max(1, size - 2));
        g2d.setColor(new Color(0, 0, 0, 100));
        g2d.drawRect(x + 1, y + 1, Math.max(1, size - 2), Math.max(1, size - 2));
    }

    private static TetrominoType typeFromId(int blockId) {
        try {
            return TetrominoType.fromId(blockId);
        } catch (IllegalArgumentException exception) {
            // 잘못된 ID 하나 때문에 전체 화면 그리기가 중단되지 않도록 해당 셀만 생략한다.
            return null;
        }
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
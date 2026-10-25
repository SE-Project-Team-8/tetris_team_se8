package com.team.tetris.ui;

import com.team.tetris.block.Board;
import com.team.tetris.block.TetrominoType;
import com.team.tetris.common.Difficulty;
import com.team.tetris.common.events.BoardSnapshot;
import com.team.tetris.common.events.GameEventListener;
import com.team.tetris.common.events.LineDeleteIds;
import com.team.tetris.game.BlockBoardDriver;
import com.team.tetris.game.DefaultSpeedPolicy;
import com.team.tetris.game.GameEngine;
import com.team.tetris.game.ScoreCalculator;
import com.team.tetris.settings.KeyBindings;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.swing.SwingUtilities;
import java.awt.Graphics2D;
import java.awt.GraphicsEnvironment;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.lang.reflect.InvocationTargetException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

/**
 * GameScreen을 오프스크린 이미지에 그려 화면 결과를 검증한다. 좌표를 하드코딩하지 않고,
 * 한 가지 조건만 다른 두 그림을 비교해 달라진 영역의 위치·크기로 판단한다.
 */
class GameScreenTest {
    private static final int WIDTH = 600;
    private static final int HEIGHT = 772;
    /** 600×772에서 보드 칸은 33px이다. 한 칸 안의 변화인지 넉넉하게 판단한다. */
    private static final int ONE_CELL = 40;
    private static final int WHITE = 0xFFFFFF;
    private static final GameEngine.DropTimer IDLE = new GameEngine.DropTimer() {
        @Override public void start(int intervalMillis, Runnable tick) { }
        @Override public void stop() { }
    };

    @BeforeEach
    void requireDisplay() {
        // GameScreen은 창(ScreenRouter)이 필요하다. headless 환경에서는 건너뛴다.
        assumeFalse(GraphicsEnvironment.isHeadless(), "ScreenRouter is a window");
    }

    @Test
    void ghostShowsTheLandingCells() throws Exception {
        edt(() -> {
            Fixture fixture = new Fixture();
            BufferedImage none = fixture.render();
            fixture.events().onLandingUpdated(null, new BoardSnapshot(board(Map.of(19, TetrominoType.O.id()))));
            Rectangle ghost = difference(none, fixture.render());
            assertNotNull(ghost);
            assertTrue(ghost.y > HEIGHT / 2, "The ghost is drawn at its landing row: " + ghost);
            fixture.events().onLandingUpdated(null, null);
            assertNull(difference(none, fixture.render()));
            fixture.dispose();
        });
    }

    @Test
    void ghostIsNotDrawnOverThePieceItOverlaps() throws Exception {
        edt(() -> {
            Fixture fixture = new Fixture();
            fixture.events().onBoardUpdated(new BoardSnapshot(board(Map.of(19, TetrominoType.O.id()))));
            BufferedImage piece = fixture.render();
            fixture.events().onLandingUpdated(null, new BoardSnapshot(board(Map.of(19, TetrominoType.O.id()))));
            assertNull(difference(piece, fixture.render()), "A piece one row from landing has no outline over it");
            fixture.dispose();
        });
    }

    @Test
    void lineClearFlashesTheRowWhileTheNextPieceStaysVisible() throws Exception {
        int[][] before = board(Map.of());
        Arrays.fill(before[19], TetrominoType.O.id());
        int lineDeleteCell = LineDeleteIds.boardCell(TetrominoType.T.id());
        int[][] after = board(Map.of(0, lineDeleteCell, 19, TetrominoType.T.id()));
        BoardSnapshot nextPiece = new BoardSnapshot(board(Map.of(0, TetrominoType.T.id())));
        Fixture[] holder = new Fixture[1];
        edt(() -> {
            Fixture fixture = new Fixture();
            holder[0] = fixture;
            fixture.events().onBoardUpdated(new BoardSnapshot(before));
            BufferedImage full = fixture.render();
            fixture.events().onLinesCleared(List.of(19), new BoardSnapshot(before));
            BufferedImage flash = fixture.render();
            Rectangle flashed = difference(full, flash);
            assertNotNull(flashed);
            assertTrue(flashed.width > flashed.height * 5, "The whole row flashes: " + flashed);
            assertTrue(countColor(flash, flashed, WHITE) > 0);

            fixture.events().onBoardUpdated(new BoardSnapshot(after));
            fixture.events().onLandingUpdated(null, new BoardSnapshot(board(Map.of(18, TetrominoType.O.id()))));
            assertNull(difference(flash, fixture.render()), "The compacted board waits for the flash to end");

            fixture.events().onLandingUpdated(nextPiece, new BoardSnapshot(board(Map.of(18, TetrominoType.O.id()))));
            BufferedImage withPiece = fixture.render();
            Rectangle piece = difference(flash, withPiece);
            assertNotNull(piece, "The next piece the engine already spawned stays visible during the flash");
            assertTrue(piece.y < HEIGHT / 3 && piece.width < ONE_CELL, "Only the spawned cell is added: " + piece);
            assertTrue(countColor(withPiece, piece, WHITE) > 0, "Its L letter comes from the pending board");
        });
        Thread.sleep(700);
        edt(() -> {
            Fixture fixture = holder[0];
            BufferedImage shown = fixture.render();
            Fixture expected = new Fixture();
            expected.events().onBoardUpdated(new BoardSnapshot(after));
            expected.events().onLandingUpdated(nextPiece, new BoardSnapshot(board(Map.of(18, TetrominoType.O.id()))));
            assertNull(difference(expected.render(), shown), "After the flash the latest board and ghost are shown");
            expected.dispose();
            fixture.dispose();
        });
    }

    /** Each given row holds one cell at column 0; every other cell is empty. */
    private static int[][] board(Map<Integer, Integer> firstColumnByRow) {
        int[][] cells = new int[20][10];
        for (int[] row : cells) Arrays.fill(row, BoardSnapshot.EMPTY);
        firstColumnByRow.forEach((row, id) -> cells[row][0] = id);
        return cells;
    }

    private static Rectangle difference(BufferedImage first, BufferedImage second) {
        Rectangle changed = null;
        for (int y = 0; y < first.getHeight(); y++) {
            for (int x = 0; x < first.getWidth(); x++) {
                if (first.getRGB(x, y) != second.getRGB(x, y)) {
                    if (changed == null) changed = new Rectangle(x, y, 1, 1);
                    else changed.add(new Rectangle(x, y, 1, 1));
                }
            }
        }
        return changed;
    }

    private static int countColor(BufferedImage image, Rectangle area, int rgb) {
        int count = 0;
        for (int y = area.y; y < area.y + area.height; y++) {
            for (int x = area.x; x < area.x + area.width; x++) {
                if ((image.getRGB(x, y) & 0xFFFFFF) == rgb) count++;
            }
        }
        return count;
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

    /** A screen with an engine that is never started; timers never fire. */
    private static final class Fixture {
        final ScreenRouter router = new ScreenRouter();
        final Forwarding listener = new Forwarding();
        final GameEngine engine = new GameEngine(new BlockBoardDriver(new Board(), () -> TetrominoType.O),
                listener, new ScoreCalculator(), new DefaultSpeedPolicy(), IDLE, IDLE, () -> Difficulty.NORMAL);
        final GameScreen screen = new GameScreen(router, engine, KeyBindings.defaults(), score -> { });

        Fixture() {
            listener.delegate = screen.eventListener();
        }

        GameEventListener events() {
            return screen.eventListener();
        }

        BufferedImage render() {
            screen.setSize(WIDTH, HEIGHT);
            BufferedImage image = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_RGB);
            Graphics2D graphics = image.createGraphics();
            try {
                screen.paint(graphics);
            } finally {
                graphics.dispose();
            }
            return image;
        }

        void dispose() {
            engine.close();
            router.dispose();
        }
    }

    private static final class Forwarding implements GameEventListener {
        GameEventListener delegate;
        @Override public void onBoardUpdated(BoardSnapshot snapshot) { delegate.onBoardUpdated(snapshot); }
        @Override public void onLandingUpdated(BoardSnapshot falling, BoardSnapshot landing) {
            delegate.onLandingUpdated(falling, landing);
        }
        @Override public void onLinesCleared(List<Integer> rows, BoardSnapshot before) {
            delegate.onLinesCleared(rows, before);
        }
        @Override public void onScoreChanged(int score) { delegate.onScoreChanged(score); }
        @Override public void onNextBlocksChanged(int[] types) { delegate.onNextBlocksChanged(types); }
        @Override public void onPauseStateChanged(boolean paused) { delegate.onPauseStateChanged(paused); }
        @Override public void onGameOver(int score) { delegate.onGameOver(score); }
    }
}

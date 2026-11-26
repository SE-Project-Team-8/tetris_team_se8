/*
 * GameScreen을 오프스크린에 그려 고스트·줄 삭제 깜빡임을 검증하는 테스트.
 */
package com.team.tetris.ui;

import com.team.tetris.block.Board;
import com.team.tetris.block.TetrominoType;
import com.team.tetris.common.Difficulty;
import com.team.tetris.common.events.BoardSnapshot;
import com.team.tetris.common.events.CellCodes;
import com.team.tetris.common.events.GameEventListener;
import com.team.tetris.game.BlockBoardDriver;
import com.team.tetris.game.DefaultSpeedPolicy;
import com.team.tetris.game.GameAction;
import com.team.tetris.game.GameEngine;
import com.team.tetris.game.ScoreCalculator;
import com.team.tetris.game.SpeedPolicy;
import com.team.tetris.game.item.AnvilItem;
import com.team.tetris.game.item.LineDeleteItem;
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
    /** 아이템 칸 테두리 색 (디자인 토큰 cream). */
    private static final int CREAM = 0xFFF6E8;
    /** 무게추 실루엣 색 (디자인 토큰 steel). */
    private static final int STEEL = 0x4A525E;
    /** LEVEL 강조색 (디자인 토큰 level). 레벨업 카드 테두리·제목에 쓴다. */
    private static final int LEVEL = 0xFFA94F;
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
        int lineDeleteCell = CellCodes.cell(TetrominoType.T.id(), LineDeleteItem.SYMBOL);
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
            assertTrue(countColor(flash, flashed, CREAM) > 0, "Cleared cells pop to cream before disappearing");

            fixture.events().onBoardUpdated(new BoardSnapshot(after));
            fixture.events().onLandingUpdated(null, new BoardSnapshot(board(Map.of(18, TetrominoType.O.id()))));
            assertNull(difference(flash, fixture.render()), "The compacted board waits for the flash to end");

            fixture.events().onLandingUpdated(nextPiece, new BoardSnapshot(board(Map.of(18, TetrominoType.O.id()))));
            BufferedImage withPiece = fixture.render();
            Rectangle piece = difference(flash, withPiece);
            assertNotNull(piece, "The next piece the engine already spawned stays visible during the flash");
            assertTrue(piece.y < HEIGHT / 3 && piece.width < ONE_CELL, "Only the spawned cell is added: " + piece);
            assertTrue(countColor(withPiece, piece, CREAM) > 0, "Its L item border comes from the pending board");
        });
        // 느린 기기에서는 줄 삭제 플래시가 늦게 끝날 수 있어서, 끝날 때까지(최대 5초) 기다린다.
        long deadline = System.currentTimeMillis() + 5_000;
        Rectangle[] remaining = {new Rectangle()};
        while (remaining[0] != null && System.currentTimeMillis() < deadline) {
            Thread.sleep(50);
            edt(() -> {
                Fixture expected = new Fixture();
                expected.events().onBoardUpdated(new BoardSnapshot(after));
                expected.events().onLandingUpdated(nextPiece, new BoardSnapshot(board(Map.of(18, TetrominoType.O.id()))));
                remaining[0] = difference(expected.render(), holder[0].render());
                expected.dispose();
            });
        }
        edt(() -> {
            Fixture fixture = holder[0];
            assertNull(remaining[0], "After the flash the latest board and ghost are shown");
            fixture.dispose();
        });
    }

    @Test
    void itemEffectsShowDistinctFeverAndSlowDownNotifications() throws Exception {
        edt(() -> {
            Fixture fixture = new Fixture();
            BufferedImage normal = fixture.render();

            fixture.events().onFeverActivated(10);
            BufferedImage fever = fixture.render();
            assertNotNull(difference(normal, fever), "Fever activation adds its toast and spotlight");
            assertTrue(countColor(fever, new Rectangle(0, 0, WIDTH, HEIGHT), 0xFFD34E) > 0,
                    "Fever notification uses its gold accent");

            fixture.events().onSlowDownActivated(3, 2);
            BufferedImage slowDown = fixture.render();
            assertNotNull(difference(fever, slowDown), "Slow-down activation updates the notification");
            assertTrue(countColor(slowDown, new Rectangle(0, 0, WIDTH, HEIGHT), 0x5FD8F0) > 0,
                    "Slow-down notification uses its cyan accent");

            fixture.events().onSlowDownActivated(1, 1);
            BufferedImage minimumSpeed = fixture.render();
            assertNotNull(difference(slowDown, minimumSpeed),
                    "A slow-down at minimum speed still explains why the level did not change");
            fixture.dispose();
        });
    }

    @Test
    void levelUpShowsANotificationOnTheBoardOnlyWhenTheLevelRises() throws Exception {
        // 첫 블록은 레벨 1, 두 번째 블록부터 레벨 2가 되는 속도 정책.
        SpeedPolicy secondBlockLevelsUp = (generatedBlocks, clearedLines) ->
                new SpeedPolicy.Speed(generatedBlocks >= 2 ? 2 : 1, 1_000);
        edt(() -> {
            Fixture fixture = new Fixture(secondBlockLevelsUp);
            fixture.engine.start();
            // 카드는 우측 패널 열의 LEVEL 패널 아래 빈 자리에 뜬다. LEVEL 숫자는 원래 주황색이라 그 아래만 본다.
            Rectangle cardArea = new Rectangle(WIDTH / 2, Math.round(HEIGHT * 0.57f), WIDTH / 2,
                    Math.round(HEIGHT * 0.25f));
            // 헤더의 TETRIS 로고 타일에도 같은 주황이 있으므로 헤더 아래 보드 쪽만 본다.
            Rectangle boardArea = new Rectangle(0, HEIGHT / 10, WIDTH / 2, HEIGHT - HEIGHT / 10);
            assertEquals(0, countColor(fixture.render(), cardArea, LEVEL), "No notification at the first level");

            fixture.engine.handle(GameAction.HARD_DROP);
            assertEquals(2, fixture.engine.getLevel());
            BufferedImage leveledUp = fixture.render();
            assertTrue(countColor(leveledUp, cardArea, LEVEL) > 0,
                    "Rising to level 2 shows the orange level-up card under the LEVEL panel");
            assertEquals(0, countColor(leveledUp, boardArea, LEVEL), "The card does not cover the board");

            // 새 판을 시작하면 이전 판의 레벨업 카드는 사라지고, 첫 레벨에서는 다시 뜨지 않는다.
            fixture.engine.stop();
            fixture.screen.onShow();
            assertEquals(0, countColor(fixture.render(), cardArea, LEVEL), "A new game starts without the card");
            fixture.dispose();
        });
    }

    @Test
    void anvilUsesSteelInThePreviewAndOnTheBoard() throws Exception {
        edt(() -> {
            Fixture fixture = new Fixture();
            fixture.events().onNextBlocksChanged(
                    new int[]{CellCodes.cell(TetrominoType.J.id(), AnvilItem.SYMBOL)});
            BufferedImage preview = fixture.render();
            // 미리보기는 우측 패널 열, 보드 첫 줄은 좌측 상단에 있다. 레이아웃 세부 좌표에 묶이지 않게 영역으로 찾는다.
            assertTrue(countColor(preview, new Rectangle(WIDTH / 2, 0, WIDTH / 2, HEIGHT / 4), STEEL) > 0,
                    "Anvil preview uses the steel silhouette color");

            int[][] anvilBoard = board(Map.of(0, CellCodes.cell(TetrominoType.J.id(), AnvilItem.SYMBOL)));
            fixture.events().onBoardUpdated(new BoardSnapshot(anvilBoard));
            BufferedImage falling = fixture.render();
            assertTrue(countColor(falling, new Rectangle(0, 0, WIDTH / 2, HEIGHT / 6), STEEL) > 0,
                    "Anvil cells on the board use the same steel color");
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
        final GameEngine engine;
        final GameScreen screen;

        Fixture() {
            this(new DefaultSpeedPolicy());
        }

        Fixture(SpeedPolicy speedPolicy) {
            engine = new GameEngine(new BlockBoardDriver(new Board(), () -> TetrominoType.O),
                    listener, new ScoreCalculator(), speedPolicy, IDLE, IDLE, () -> Difficulty.NORMAL);
            screen = new GameScreen(router, engine, KeyBindings.defaults(), score -> { });
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
        @Override public void onFeverActivated(int seconds) { delegate.onFeverActivated(seconds); }
        @Override public void onSlowDownActivated(int previousLevel, int currentLevel) {
            delegate.onSlowDownActivated(previousLevel, currentLevel);
        }
        @Override public void onGameOver(int score) { delegate.onGameOver(score); }
    }
}

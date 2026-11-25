/*
 * 엔진 연출 알림(하드드롭·폭탄·무게추)을 받아 게임 화면이 그리는 연출을 검증한다.
 * 기준: 컴포넌트 시트의 "하드드롭 이펙트"·"폭탄 폭발 이펙트"·"무게추 분쇄 이펙트", BombScreen·AnvilScreen 목업.
 */
package com.team.tetris.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

import java.awt.Graphics2D;
import java.awt.GraphicsEnvironment;
import java.awt.image.BufferedImage;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import javax.swing.SwingUtilities;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.team.tetris.block.Board;
import com.team.tetris.block.TetrominoType;
import com.team.tetris.common.Difficulty;
import com.team.tetris.common.events.BoardSnapshot;
import com.team.tetris.common.events.CellCodes;
import com.team.tetris.common.events.CellPosition;
import com.team.tetris.common.events.GameEventListener;
import com.team.tetris.game.BlockBoardDriver;
import com.team.tetris.game.DefaultSpeedPolicy;
import com.team.tetris.game.GameAction;
import com.team.tetris.game.GameEngine;
import com.team.tetris.game.ScoreCalculator;
import com.team.tetris.game.item.AnvilItem;
import com.team.tetris.game.item.BombItem;
import com.team.tetris.settings.KeyBindings;

class GameScreenEffectsTest {
    private static final int WIDTH = 600;
    private static final int HEIGHT = 772;
    /** 600×772에서 보드 칸은 28px이고, 보드 왼쪽 위는 (38, 70)이다. */
    private static final int PITCH = 28;
    private static final int BOARD_X = 38;
    private static final int BOARD_Y = 70;
    /** 디자인 토큰: accent(노랑), cream(흰), P2(빨강). */
    private static final int ACCENT = 0xFFD34E;
    private static final int CREAM = 0xFFF6E8;
    private static final int BOMB_TOAST = 0xFF7F7F;
    private static final int ANVIL_TOAST = 0xC9C4D6;
    /** 연출이 끝나기를 기다리는 시간. 폭탄 0.33초·무게추 0.27초·하드드롭 0.11초보다 넉넉히 잡는다. */
    private static final int AFTER_EFFECTS_MS = 800;
    private static final GameEngine.DropTimer IDLE = new GameEngine.DropTimer() {
        @Override public void start(int intervalMillis, Runnable tick) { }
        @Override public void stop() { }
    };

    @BeforeEach
    void requireDisplay() {
        assumeFalse(GraphicsEnvironment.isHeadless(), "ScreenRouter is a window");
    }

    @Test
    void hardDropNoticeDrawsATrailAlongThePathAndThenClears() throws Exception {
        BufferedImage[] images = new BufferedImage[3];
        Fixture[] fixture = new Fixture[1];
        edt(() -> {
            fixture[0] = new Fixture();
            images[0] = fixture[0].render();
            int[][] before = board(List.of(new CellPosition(0, 4), new CellPosition(0, 5),
                    new CellPosition(1, 4), new CellPosition(1, 5)), TetrominoType.O.id());
            int[][] after = board(List.of(new CellPosition(18, 4), new CellPosition(18, 5),
                    new CellPosition(19, 4), new CellPosition(19, 5)), TetrominoType.O.id());
            fixture[0].events().onHardDropped(new BoardSnapshot(before), new BoardSnapshot(after));
            images[1] = fixture[0].render();
        });
        // 착지 순간 보드가 1px 흔들리므로 그림 전체 비교 대신, 경로 위 칸과 옆 칸의 가운데 색을 비교한다.
        assertEquals(cell(images[0], 10, 4), cell(images[0], 10, 0), "Before the drop both cells are empty slots");
        assertNotEquals(cell(images[1], 10, 4), cell(images[1], 10, 0), "The trail tints the drop path");
        assertEquals(cell(images[1], 10, 0), cell(images[1], 10, 8), "Columns away from the piece get no trail");

        Thread.sleep(AFTER_EFFECTS_MS);
        edt(() -> {
            images[2] = fixture[0].render();
            fixture[0].dispose();
        });
        assertEquals(cell(images[0], 10, 4), cell(images[2], 10, 4), "The one-shot trail is gone after its timer");
    }

    @Test
    void aRealHardDropReachesTheScreenThroughTheEngineNotice() throws Exception {
        edt(() -> {
            ScreenRouter router = new ScreenRouter();
            GameScreen[] screen = new GameScreen[1];
            GameEngine engine = new GameEngine(new BlockBoardDriver(new Board(), () -> TetrominoType.O),
                    new GameEventListener() {
                        @Override public void onBoardUpdated(BoardSnapshot s) { screen[0].eventListener().onBoardUpdated(s); }
                        @Override public void onLandingUpdated(BoardSnapshot f, BoardSnapshot l) {
                            screen[0].eventListener().onLandingUpdated(f, l);
                        }
                        @Override public void onScoreChanged(int score) { }
                        @Override public void onNextBlocksChanged(int[] types) { }
                        @Override public void onPauseStateChanged(boolean paused) { }
                        @Override public void onHardDropped(BoardSnapshot before, BoardSnapshot after) {
                            screen[0].eventListener().onHardDropped(before, after);
                        }
                        @Override public void onGameOver(int score) { }
                    }, new ScoreCalculator(), new DefaultSpeedPolicy(), IDLE, IDLE, () -> Difficulty.NORMAL);
            screen[0] = new GameScreen(router, engine, KeyBindings.defaults(), score -> { });
            try {
                engine.start();
                engine.handle(GameAction.HARD_DROP);
                BufferedImage image = render(screen[0]);
                // O 블록은 4·5열에서 나와 바닥까지 떨어진다. 경로 중간 칸에 잔상이 보인다.
                assertNotEquals(cell(image, 10, 4), cell(image, 10, 0), "The engine's hard-drop notice starts the trail");
            } finally {
                engine.close();
                router.dispose();
            }
        });
    }

    @Test
    void bombFlashesACheckerOverTheBlastAndLeavesSmokeThatClears() throws Exception {
        BufferedImage[] images = new BufferedImage[4];
        Fixture[] fixture = new Fixture[1];
        edt(() -> {
            fixture[0] = new Fixture();
            images[0] = fixture[0].render();
            // 폭탄 범위는 5×5다 (PR #75).
            fixture[0].events().onBombExploded(new CellPosition(10, 4), square(10, 4, 2));
            images[1] = fixture[0].render();
        });
        // ② 폭발 플래시: 모서리는 노랑, 변 가운데는 흰색 체커. 범위 밖은 그대로.
        assertEquals(ACCENT, cell(images[1], 8, 2), "Corner of the 5×5 blast flashes yellow");
        assertEquals(CREAM, cell(images[1], 8, 3), "Next to the corner flashes cream");
        assertEquals(ACCENT, cell(images[1], 9, 3), "Two steps from the bomb flashes yellow");
        assertEquals(ACCENT, cell(images[1], 12, 6));
        assertEquals(cell(images[0], 10, 7), cell(images[1], 10, 7), "Cells outside the blast are untouched");
        assertEquals(cell(images[0], 8, 1), cell(images[1], 8, 1), "Just outside the 5×5 blast is untouched");
        assertEquals(0, countInToastArea(images[1], BOMB_TOAST), "The explosion itself does not show the toast");

        Thread.sleep(200);
        edt(() -> images[2] = fixture[0].render());
        // ③ 다음 순간: 체커는 사라지고 빈칸 위 회색 연기 점만 남는다.
        assertNotEquals(ACCENT, cell(images[2], 8, 2), "The flash is over");
        assertNotEquals(cell(images[0], 9, 4), cell(images[2], 9, 4), "A smoke dot sits on the edge cell");

        Thread.sleep(AFTER_EFFECTS_MS);
        edt(() -> {
            images[3] = fixture[0].render();
            fixture[0].dispose();
        });
        assertEquals(cell(images[0], 9, 4), cell(images[3], 9, 4), "The smoke is gone after its timer");
        assertEquals(cell(images[0], 10, 4), cell(images[3], 10, 4));
    }

    @Test
    void bombAreaFollowsTheCellsTheEngineReports() throws Exception {
        BufferedImage[] images = new BufferedImage[2];
        edt(() -> {
            Fixture fixture = new Fixture();
            images[0] = fixture.render();
            // 화면은 범위를 따로 정하지 않고 엔진이 알려 준 칸을 그대로 칠한다 (예: 3×3만 알려 주면 3×3).
            fixture.events().onBombExploded(new CellPosition(10, 4), square(10, 4, 1));
            images[1] = fixture.render();
            fixture.dispose();
        });
        assertEquals(ACCENT, cell(images[1], 9, 3), "The corner of the reported 3×3 flashes");
        assertEquals(cell(images[0], 8, 2), cell(images[1], 8, 2), "Cells the engine did not report stay as they are");
    }

    @Test
    void bombAtTheBoardEdgeOnlyFlashesTheCellsInsideTheBoard() throws Exception {
        BufferedImage[] images = new BufferedImage[1];
        edt(() -> {
            Fixture fixture = new Fixture();
            // 왼쪽 아래 구석: 엔진은 보드 밖 칸을 빼고 2×2만 알려 준다.
            fixture.events().onBombExploded(new CellPosition(19, 0), List.of(new CellPosition(18, 0),
                    new CellPosition(18, 1), new CellPosition(19, 0), new CellPosition(19, 1)));
            images[0] = fixture.render();
            fixture.dispose();
        });
        assertEquals(CREAM, cell(images[0], 18, 0));
        assertEquals(ACCENT, cell(images[0], 18, 1));
    }

    @Test
    void anvilPuffsSmokeOverTheCrushedCellsAndThenClears() throws Exception {
        BufferedImage[] images = new BufferedImage[3];
        Fixture[] fixture = new Fixture[1];
        edt(() -> {
            fixture[0] = new Fixture();
            images[0] = fixture[0].render();
            fixture[0].events().onAnvilCrushed(List.of(new CellPosition(15, 2), new CellPosition(15, 3)));
            images[1] = fixture[0].render();
        });
        assertNotEquals(pixel(images[0], 15, 2, 0.3f, 0.55f), pixel(images[1], 15, 2, 0.3f, 0.55f),
                "A smoke puff covers the crushed cell");
        assertEquals(cell(images[0], 15, 8), cell(images[1], 15, 8), "Cells away from the anvil are untouched");
        assertEquals(0, countInToastArea(images[1], ANVIL_TOAST), "Crushing alone does not show the toast");

        Thread.sleep(AFTER_EFFECTS_MS);
        edt(() -> {
            images[2] = fixture[0].render();
            fixture[0].dispose();
        });
        assertEquals(pixel(images[0], 15, 2, 0.3f, 0.55f), pixel(images[2], 15, 2, 0.3f, 0.55f),
                "The smoke is gone after its timer");
    }

    @Test
    void anvilToastShowsOnceWhenTheAnvilAppearsNotWhileItCrushes() throws Exception {
        BufferedImage[] images = new BufferedImage[2];
        Fixture[] fixture = new Fixture[1];
        int anvil = CellCodes.cell(TetrominoType.J.id(), AnvilItem.SYMBOL);
        edt(() -> {
            fixture[0] = new Fixture();
            // 무게추가 맨 위에 나타난다.
            fixture[0].events().onLandingUpdated(new BoardSnapshot(board(List.of(new CellPosition(0, 4),
                    new CellPosition(0, 5), new CellPosition(1, 3), new CellPosition(1, 4), new CellPosition(1, 5),
                    new CellPosition(1, 6)), anvil)), null);
            images[0] = fixture[0].render();
        });
        assertTrue(countInToastArea(images[0], ANVIL_TOAST) > 0, "The ANVIL! toast appears as soon as the anvil does");

        // 토스트가 끝난 뒤에도 같은 무게추가 내려가며 부수는 동안에는 다시 뜨지 않는다.
        Thread.sleep(2_000);
        edt(() -> {
            fixture[0].events().onLandingUpdated(new BoardSnapshot(board(List.of(new CellPosition(5, 4),
                    new CellPosition(5, 5), new CellPosition(6, 3), new CellPosition(6, 4), new CellPosition(6, 5),
                    new CellPosition(6, 6)), anvil)), null);
            fixture[0].events().onAnvilCrushed(List.of(new CellPosition(6, 3), new CellPosition(6, 4)));
            images[1] = fixture[0].render();
            fixture[0].dispose();
        });
        assertEquals(0, countInToastArea(images[1], ANVIL_TOAST), "The same anvil does not show the toast again");
    }

    @Test
    void bombToastShowsOnceWhenABombPieceAppears() throws Exception {
        BufferedImage[] images = new BufferedImage[2];
        Fixture[] fixture = new Fixture[1];
        int plain = TetrominoType.T.id();
        int bomb = CellCodes.cell(TetrominoType.T.id(), BombItem.SYMBOL);
        edt(() -> {
            fixture[0] = new Fixture();
            // 폭탄 없는 블록은 토스트가 없다.
            fixture[0].events().onLandingUpdated(new BoardSnapshot(board(List.of(new CellPosition(0, 4),
                    new CellPosition(1, 3), new CellPosition(1, 4), new CellPosition(1, 5)), plain)), null);
            images[0] = fixture[0].render();
            // 폭탄이 붙은 블록이 나타난다.
            int[][] piece = board(List.of(new CellPosition(1, 3), new CellPosition(1, 4), new CellPosition(1, 5)), plain);
            piece[0][4] = bomb;
            fixture[0].events().onLandingUpdated(new BoardSnapshot(piece), null);
            images[1] = fixture[0].render();
            fixture[0].dispose();
        });
        assertEquals(0, countInToastArea(images[0], BOMB_TOAST), "A plain piece shows no toast");
        assertTrue(countInToastArea(images[1], BOMB_TOAST) > 0, "The BOMB! toast appears as soon as the bomb does");
    }

    /** 아이템 토스트가 뜨는 보드 위쪽 영역(보드 높이의 15~35%)에서 색을 센다. */
    private static int countInToastArea(BufferedImage image, int rgb) {
        int count = 0;
        int top = BOARD_Y + PITCH * 3;
        int bottom = BOARD_Y + PITCH * 7;
        for (int y = top; y < bottom; y++) {
            for (int x = BOARD_X; x < BOARD_X + PITCH * 10; x++) {
                if ((image.getRGB(x, y) & 0xFFFFFF) == rgb) count++;
            }
        }
        return count;
    }

    private static List<CellPosition> square(int centerRow, int centerCol, int radius) {
        List<CellPosition> cells = new ArrayList<>();
        for (int row = centerRow - radius; row <= centerRow + radius; row++) {
            for (int col = centerCol - radius; col <= centerCol + radius; col++) cells.add(new CellPosition(row, col));
        }
        return cells;
    }

    private static int[][] board(List<CellPosition> cells, int id) {
        int[][] board = new int[20][10];
        for (int[] row : board) Arrays.fill(row, BoardSnapshot.EMPTY);
        for (CellPosition cell : cells) board[cell.row()][cell.col()] = id;
        return board;
    }

    /** 칸 가운데 픽셀 색. */
    private static int cell(BufferedImage image, int row, int col) {
        return pixel(image, row, col, 0.5f, 0.5f);
    }

    /** 칸 안의 비율 위치 픽셀 색. */
    private static int pixel(BufferedImage image, int row, int col, float fx, float fy) {
        return image.getRGB(BOARD_X + col * PITCH + Math.round(PITCH * fx),
                BOARD_Y + row * PITCH + Math.round(PITCH * fy)) & 0xFFFFFF;
    }

    private static BufferedImage render(GameScreen screen) {
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

    private static void edt(Runnable action) throws Exception {
        try {
            SwingUtilities.invokeAndWait(action);
        } catch (InvocationTargetException failure) {
            if (failure.getCause() instanceof AssertionError assertion) throw assertion;
            if (failure.getCause() instanceof Exception exception) throw exception;
            throw failure;
        }
    }

    /** 엔진은 시작하지 않는다. 연출 알림은 화면의 리스너에 직접 보낸다. */
    private static final class Fixture {
        final ScreenRouter router = new ScreenRouter();
        final GameEngine engine = new GameEngine(new BlockBoardDriver(new Board(), () -> TetrominoType.O),
                new GameEventListener() {
                    @Override public void onBoardUpdated(BoardSnapshot snapshot) { }
                    @Override public void onScoreChanged(int score) { }
                    @Override public void onNextBlocksChanged(int[] types) { }
                    @Override public void onPauseStateChanged(boolean paused) { }
                    @Override public void onGameOver(int score) { }
                }, new ScoreCalculator(), new DefaultSpeedPolicy(), IDLE, IDLE, () -> Difficulty.NORMAL);
        final GameScreen screen = new GameScreen(router, engine, KeyBindings.defaults(), score -> { });

        GameEventListener events() {
            return screen.eventListener();
        }

        BufferedImage render() {
            return GameScreenEffectsTest.render(screen);
        }

        void dispose() {
            engine.close();
            router.dispose();
        }
    }
}

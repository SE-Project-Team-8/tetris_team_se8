package com.team.tetris.game;

import com.team.tetris.block.Board;
import com.team.tetris.block.Rotation;
import com.team.tetris.block.Tetromino;
import com.team.tetris.block.TetrominoType;
import com.team.tetris.common.events.AnvilIds;
import com.team.tetris.common.events.BoardSnapshot;
import com.team.tetris.common.events.GameEventListener;
import com.team.tetris.common.events.LineDeleteIds;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import javax.swing.SwingUtilities;
import java.util.Arrays;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class AnvilDriverTest {
    @Test
    void earnedAnvilHasSixCellsAndFourCellWidthInPreviewAndBoard() {
        Board board = new Board();
        BlockBoardDriver driver = anvilDriver(board);
        spawnEarnedAnvil(driver, board);
        assertArrayEquals(new int[]{-1, -1, -1, -1, 35, 35, -1, -1, -1, -1},
                driver.snapshot().cells()[0]);
        assertArrayEquals(new int[]{-1, -1, -1, 35, 35, 35, 35, -1, -1, -1},
                driver.snapshot().cells()[1]);
        assertEquals(6, anvilCells(driver.snapshot()));
        assertFalse(LineDeleteIds.isItemPreview(AnvilIds.ANVIL_ID));
        assertFalse(driver.awardsDropPoints());
    }

    @Test
    void movesSidewaysBeforeContactButCannotCrossWallsOrRotate() {
        Board board = new Board();
        BlockBoardDriver driver = anvilDriver(board);
        spawnEarnedAnvil(driver, board);
        for (int step = 0; step < 3; step++) assertTrue(driver.moveLeft());
        assertFalse(driver.moveLeft());
        for (int step = 0; step < 6; step++) assertTrue(driver.moveRight());
        assertFalse(driver.moveRight());
        assertFalse(driver.rotateClockwise());
        assertEquals(6, anvilCells(driver.snapshot()));
    }

    @Test
    void firstContactLocksSidewaysMovementAndCrushingDoesNotUnlockIt() {
        Board board = new Board();
        BlockBoardDriver driver = anvilDriver(board);
        spawnEarnedAnvil(driver, board);
        placeSquare(board, 6, 4);
        placeSquare(board, 16, 3);
        placeSquare(board, 12, 0);
        placeSquare(board, 14, 8);

        for (int step = 0; step < 4; step++) assertTrue(driver.moveDown());
        assertTrue(board.cellAt(6, 4).isPresent(), "Touching happens before crushing");
        assertFalse(driver.moveLeft());
        assertFalse(driver.moveRight());
        assertTrue(driver.moveDown());
        assertTrue(board.cellAt(6, 4).isEmpty());
        assertTrue(board.cellAt(7, 4).isPresent(), "Only cells reached by the weight are erased");
        assertTrue(driver.moveDown());
        assertTrue(board.cellAt(7, 4).isEmpty());
        assertFalse(driver.moveLeft(), "The first-contact restriction is permanent");
        assertFalse(driver.moveRight());

        assertEquals(12, driver.hardDrop());
        assertFalse(driver.moveLeft(), "Floor contact also forbids sideways movement");
        assertEquals(new GameEngine.LockResult(0, false), driver.lockAndClearLines());
        for (int row = 2; row < board.rows(); row++) {
            for (int col = 3; col <= 6; col++) assertTrue(board.cellAt(row, col).isEmpty());
        }
        assertEquals(TetrominoType.O, board.cellAt(12, 0).orElseThrow());
        assertEquals(TetrominoType.O, board.cellAt(14, 8).orElseThrow());
        assertEquals(0, anvilCells(driver.snapshot()), "The used weight disappears at the floor");
    }

    @Test
    void hardDropCrushesEveryLayerAndStopsAtTheFloor() {
        Board board = new Board();
        BlockBoardDriver driver = anvilDriver(board);
        spawnEarnedAnvil(driver, board);
        placeSquare(board, 4, 3);
        placeSquare(board, 10, 5);
        placeSquare(board, 18, 3);
        assertEquals(18, driver.hardDrop());
        assertEquals(0, driver.hardDrop());
        assertFalse(driver.moveDown());
        assertFalse(driver.moveRight());
        assertEquals(0, driver.lockAndClearLines().clearedLines());
        for (int[] row : driver.snapshot().cells()) {
            for (int cell : row) assertEquals(BoardSnapshot.EMPTY, cell);
        }
    }

    @Test
    void anvilAlreadyTouchingABlockAtSpawnCannotMoveSideways() {
        Board board = new Board();
        BlockBoardDriver driver = anvilDriver(board);
        earnTenAndQueueItem(driver, board);
        driver.hardDrop();
        driver.lockAndClearLines();
        board.clear();
        placeSquare(board, 2, 4);
        assertTrue(driver.spawnNextBlock());
        assertFalse(driver.moveLeft());
        assertFalse(driver.moveRight());
        assertTrue(driver.moveDown());
        assertTrue(board.cellAt(2, 4).isEmpty());
    }

    @Test
    void usingAnvilDoesNotLoseTheNextTwentyLineMilestone() {
        Board board = new Board();
        AtomicInteger selections = new AtomicInteger();
        BlockBoardDriver driver = new BlockBoardDriver(board, () -> TetrominoType.O, new Random(1),
                () -> selections.getAndIncrement() == 0 ? ItemType.ANVIL : ItemType.LINE_DELETE);
        spawnEarnedAnvil(driver, board);
        driver.hardDrop();
        assertEquals(0, driver.lockAndClearLines().clearedLines());
        for (int round = 0; round < 5; round++) {
            assertTrue(driver.spawnNextBlock());
            fillTwoRowsExceptTheCenter(board);
            driver.hardDrop();
            assertEquals(2, driver.lockAndClearLines().clearedLines());
        }
        assertTrue(driver.spawnNextBlock());
        assertTrue(LineDeleteIds.isItemPreview(driver.nextBlockTypes()[0]));
        assertEquals(2, selections.get());
    }

    @Test
    void blockedAnvilSpawnEndsTheGameWithoutCrushingTheTop() {
        Board board = new Board();
        BlockBoardDriver driver = anvilDriver(board);
        earnTenAndQueueItem(driver, board);
        driver.hardDrop();
        driver.lockAndClearLines();
        board.clear();
        placeSquare(board, 0, 4);
        assertFalse(driver.spawnNextBlock());
        assertEquals(TetrominoType.O, board.cellAt(0, 4).orElseThrow());
        assertEquals(0, anvilCells(driver.snapshot()));
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1})
    void realDefaultSelectorCanGenerateEitherRequiredItem(int choice) {
        Board board = new Board();
        Random fixedChoice = new Random() {
            @Override public int nextInt(int bound) { return bound == 2 ? choice : 3; }
        };
        BlockBoardDriver driver = new BlockBoardDriver(board, () -> TetrominoType.O, fixedChoice);
        earnTenAndQueueItem(driver, board);
        int expected = choice == 0 ? LineDeleteIds.preview(TetrominoType.O.id(), 3) : AnvilIds.ANVIL_ID;
        assertArrayEquals(new int[]{expected}, driver.nextBlockTypes());
    }

    @Test
    void newGameResetsTheContactLatchAndPendingItemProgress() {
        Board board = new Board();
        BlockBoardDriver driver = anvilDriver(board);
        spawnEarnedAnvil(driver, board);
        driver.hardDrop();
        assertFalse(driver.moveLeft());
        driver.reset();
        assertArrayEquals(new int[]{TetrominoType.O.id()}, driver.nextBlockTypes());
        assertTrue(driver.spawnNextBlock());
        assertTrue(driver.awardsDropPoints());
        spawnEarnedAnvil(driver, board);
        assertTrue(driver.moveLeft());
    }

    @Test
    void gravitySoftDropHardDropAndCrushedCellsAwardNoPoints() throws Exception {
        Board board = new Board();
        BlockBoardDriver driver = anvilDriver(board);
        driver.setItemMode(true);
        ManualTimer timer = new ManualTimer();
        GameEngine engine = new GameEngine(driver, new Events(), new ScoreCalculator(), timer);
        SwingUtilities.invokeAndWait(() -> {
            engine.start();
            for (int round = 0; round < 5; round++) {
                fillTwoRowsExceptTheCenter(board);
                engine.handle(GameAction.HARD_DROP);
            }
            assertArrayEquals(new int[]{AnvilIds.ANVIL_ID}, driver.nextBlockTypes());
            engine.handle(GameAction.HARD_DROP); // The pre-existing regular preview falls first.
            assertEquals(6, anvilCells(driver.snapshot()));
            int scoreBeforeWeight = engine.getScore();
            timer.tick.run();
            assertEquals(scoreBeforeWeight, engine.getScore(), "Gravity is free for weights");
            engine.handle(GameAction.SOFT_DROP);
            assertEquals(scoreBeforeWeight, engine.getScore(), "Soft drop is free for weights");
            engine.handle(GameAction.HARD_DROP);
            assertEquals(scoreBeforeWeight, engine.getScore(), "Hard drop and crushing are free");
            assertEquals(10, engine.getClearedLines(), "Crushed cells are not scored as cleared rows");
            assertTrue(driver.awardsDropPoints(), "Normal scoring resumes for the next regular block");
            assertTrue(board.cellAt(19, 4).isEmpty());
            engine.close();
        });
    }

    private static BlockBoardDriver anvilDriver(Board board) {
        return new BlockBoardDriver(board, () -> TetrominoType.O, new Random(1), () -> ItemType.ANVIL);
    }

    private static void earnTenAndQueueItem(BlockBoardDriver driver, Board board) {
        driver.setItemMode(true);
        driver.reset();
        for (int round = 0; round < 5; round++) {
            assertTrue(driver.spawnNextBlock());
            assertArrayEquals(new int[]{TetrominoType.O.id()}, driver.nextBlockTypes());
            fillTwoRowsExceptTheCenter(board);
            driver.hardDrop();
            assertEquals(2, driver.lockAndClearLines().clearedLines());
        }
        assertTrue(driver.spawnNextBlock());
    }

    private static void spawnEarnedAnvil(BlockBoardDriver driver, Board board) {
        earnTenAndQueueItem(driver, board);
        assertArrayEquals(new int[]{AnvilIds.ANVIL_ID}, driver.nextBlockTypes());
        driver.hardDrop();
        assertEquals(0, driver.lockAndClearLines().clearedLines());
        board.clear(); // Isolate the fixture terrain without resetting earned game progress.
        assertTrue(driver.spawnNextBlock());
    }

    private static void fillTwoRowsExceptTheCenter(Board board) {
        for (int col : new int[]{0, 2, 6, 8}) placeSquare(board, 18, col);
    }

    private static void placeSquare(Board board, int row, int col) {
        assertTrue(board.tryPlace(new Tetromino(TetrominoType.O, Rotation.SPAWN, row, col)));
    }

    private static long anvilCells(BoardSnapshot snapshot) {
        return Arrays.stream(snapshot.cells()).flatMapToInt(Arrays::stream)
                .filter(cell -> cell == AnvilIds.ANVIL_ID).count();
    }

    private static final class ManualTimer implements GameEngine.DropTimer {
        Runnable tick;
        public void start(int intervalMillis, Runnable tick) { this.tick = tick; }
        public void stop() { }
    }

    private static final class Events implements GameEventListener {
        public void onBoardUpdated(BoardSnapshot snapshot) { }
        public void onScoreChanged(int score) { }
        public void onNextBlocksChanged(int[] types) { }
        public void onPauseStateChanged(boolean paused) { }
        public void onGameOver(int finalScore) { }
    }
}

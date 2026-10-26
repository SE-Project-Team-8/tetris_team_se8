package com.team.tetris.game.item;

import com.team.tetris.block.Board;
import com.team.tetris.block.Cell;
import com.team.tetris.block.Rotation;
import com.team.tetris.block.Tetromino;
import com.team.tetris.block.TetrominoType;
import com.team.tetris.common.constants.GameConstants;
import com.team.tetris.common.events.BoardSnapshot;
import com.team.tetris.common.events.CellCodes;
import com.team.tetris.game.BlockBoardDriver;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link ItemRegistry#defaults()}에 등록된 모든 아이템이 지켜야 하는 계약.
 * 아이템을 추가하면 자동으로 이 검사를 받으므로, 등록만으로 기본 동작(문자, 미리보기, 생성, 고정)이 검증된다.
 */
class ItemContractTest {
    static List<Item> items() {
        return ItemRegistry.defaults().items();
    }

    static List<StandaloneItem> standaloneItems() {
        return items().stream().filter(StandaloneItem.class::isInstance).map(StandaloneItem.class::cast).toList();
    }

    static List<EmbeddedItem> embeddedItems() {
        return items().stream().filter(EmbeddedItem.class::isInstance).map(EmbeddedItem.class::cast).toList();
    }

    @ParameterizedTest
    @MethodSource("items")
    void hasAVisibleSymbolThatNoOtherItemUses(Item item) {
        assertTrue(CellCodes.isSymbol(item.symbol()), "symbol must be drawable");
        assertFalse(item.name().isBlank());
        assertEquals(1, items().stream().filter(other -> other.symbol() == item.symbol()).count(),
                "requirement 4.2: every item has its own character");
        assertEquals(1, items().stream().filter(other -> other.name().equals(item.name())).count());
        for (TetrominoType type : TetrominoType.values()) {
            int code = CellCodes.cell(type.id(), item.symbol());
            assertEquals(item.symbol(), CellCodes.symbol(code));
            assertEquals(type.id(), CellCodes.baseType(code));
        }
    }

    @ParameterizedTest
    @MethodSource("standaloneItems")
    void standaloneItemFitsTheBoardSpawnsAtTheTopAndPaintsItsSymbol(StandaloneItem item) {
        List<Cell> shape = item.previewShape();
        assertFalse(shape.isEmpty());
        int width = shape.stream().mapToInt(Cell::col).max().orElseThrow()
                - shape.stream().mapToInt(Cell::col).min().orElseThrow() + 1;
        assertTrue(width <= GameConstants.BOARD_COLS, "preview shape must fit the board");

        Board board = new Board();
        FallingPiece piece = item.spawn(board);
        assertNotNull(piece, "an empty board never blocks a spawn");
        assertEquals(0, piece.cells().stream().mapToInt(Cell::row).min().orElseThrow(), "spawns on the top row");
        int[][] cells = emptyGrid();
        piece.paint(cells);
        for (Cell cell : piece.cells()) {
            assertTrue(cell.row() >= 0 && cell.row() < GameConstants.BOARD_ROWS
                    && cell.col() >= 0 && cell.col() < GameConstants.BOARD_COLS, "inside the board: " + cell);
            int code = cells[cell.row()][cell.col()];
            assertEquals(item.symbol(), CellCodes.symbol(code));
            assertEquals(item.paletteType().id(), CellCodes.baseType(code));
        }
        assertEquals(piece.cells().size(), Arrays.stream(cells).flatMapToInt(Arrays::stream)
                .filter(code -> code != BoardSnapshot.EMPTY).count(), "paints exactly its own cells");

        Board blocked = new Board();
        for (int col = 0; col < GameConstants.BOARD_COLS; col += 2) {
            assertTrue(blocked.tryPlace(new Tetromino(TetrominoType.O, Rotation.SPAWN, 0, col)));
        }
        assertNull(item.spawn(blocked), "a blocked spawn area ends the game instead of overwriting it");
        for (int col = 0; col < GameConstants.BOARD_COLS; col++) {
            assertTrue(blocked.cellAt(0, col).isPresent(), "a failed spawn leaves the board untouched");
        }
    }

    @ParameterizedTest
    @MethodSource("standaloneItems")
    void standaloneItemCanBeHardDroppedAndLockedOnAnEmptyBoard(StandaloneItem item) {
        Board board = new Board();
        FallingPiece piece = item.spawn(board);
        assertTrue(piece.hardDrop(board) > 0);
        assertNotNull(piece.lock(board));
    }

    @ParameterizedTest
    @MethodSource("embeddedItems")
    void embeddedItemReportsRowsInsideTheBoard(EmbeddedItem item) {
        Board board = new Board();
        assertTrue(board.tryPlace(new Tetromino(TetrominoType.O, Rotation.SPAWN, 18, 4)));
        Set<Integer> rows = item.onLock(board, new Cell(18, 4));
        assertNotNull(rows);
        for (int row : rows) assertTrue(row >= 0 && row < GameConstants.BOARD_ROWS, "row " + row);
    }

    /** 등록만 하면 실제 드라이버 경로(10줄 → 미리보기 → 생성 → 낙하 → 고정)를 예외 없이 통과해야 한다. */
    @ParameterizedTest
    @MethodSource("items")
    void playsThroughTheRealDriverAfterTenLines(Item item) {
        Board board = new Board();
        BlockBoardDriver driver = new BlockBoardDriver(board, () -> TetrominoType.O, new Random(1), () -> item);
        driver.setItemMode(true);
        driver.reset();
        for (int round = 0; round < 5; round++) {
            assertTrue(driver.spawnNextBlock());
            for (int col : new int[]{0, 2, 6, 8}) {
                assertTrue(board.tryPlace(new Tetromino(TetrominoType.O, Rotation.SPAWN, 18, col)));
            }
            driver.hardDrop();
            assertEquals(2, driver.lockAndClearLines().clearedLines());
        }
        assertTrue(driver.spawnNextBlock(), "the block queued before the milestone is a regular one");
        int preview = driver.nextBlockTypes()[0];
        assertEquals(item.symbol(), CellCodes.symbol(preview), "the next preview entry is the earned item");
        assertEquals(item instanceof EmbeddedItem, CellCodes.itemCellIndex(preview) >= 0,
                "embedded items mark one cell; standalone items mark the whole piece");
        driver.hardDrop();
        driver.lockAndClearLines();

        assertTrue(driver.spawnNextBlock(), "the item piece spawns on a cleared board");
        long symbolCells = Arrays.stream(driver.snapshot().cells()).flatMapToInt(Arrays::stream)
                .filter(code -> code != BoardSnapshot.EMPTY && CellCodes.symbol(code) == item.symbol()).count();
        assertTrue(symbolCells >= 1, "the falling item shows its symbol on the board");
        driver.hardDrop();
        assertNotNull(driver.lockAndClearLines());
        assertTrue(driver.spawnNextBlock(), "play continues with a regular block");
        assertEquals(TetrominoType.O.id(), driver.nextBlockTypes()[0], "one milestone yields exactly one item");
    }

    private static int[][] emptyGrid() {
        int[][] cells = new int[GameConstants.BOARD_ROWS][GameConstants.BOARD_COLS];
        for (int[] row : cells) Arrays.fill(row, BoardSnapshot.EMPTY);
        return cells;
    }
}

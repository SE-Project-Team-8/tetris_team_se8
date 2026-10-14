package com.team.tetris.game;

import com.team.tetris.block.Board;
import com.team.tetris.block.Anvil;
import com.team.tetris.block.Bomb;
import com.team.tetris.block.Cell;
import com.team.tetris.block.CollisionChecker;
import com.team.tetris.block.DropCalculator;
import com.team.tetris.block.Rotation;
import com.team.tetris.block.Tetromino;
import com.team.tetris.block.TetrominoGenerator;
import com.team.tetris.block.TetrominoType;
import com.team.tetris.block.UniformTetrominoGenerator;
import com.team.tetris.common.constants.GameConstants;
import com.team.tetris.common.events.BoardSnapshot;
import com.team.tetris.common.events.AnvilIds;
import com.team.tetris.common.events.ItemCellIds;

import java.util.ArrayDeque;
import java.util.List;
import java.util.Objects;
import java.util.Random;
import java.util.function.Supplier;

/** Adapts the block domain to the game engine; used on the Swing EDT. */
public final class BlockBoardDriver implements GameEngine.BoardDriver {
    private final Board board;
    private final TetrominoGenerator generator;
    private final ArrayDeque<QueuedBlock> preview = new ArrayDeque<>();
    private final Random itemRandom;
    private final Supplier<ItemType> itemSelector;
    private Tetromino current;
    private Anvil currentAnvil;
    private boolean anvilHorizontalLocked;
    private ItemType currentItem = ItemType.NONE;
    private int currentItemIndex = -1;
    private ItemType lockedItem = ItemType.NONE;
    private int clearedLines;
    private int pendingItems;
    private boolean itemMode;

    private record QueuedBlock(TetrominoType type, ItemType item, int itemIndex) { }

    public BlockBoardDriver() {
        this(new Board(), new UniformTetrominoGenerator(new Random()));
    }

    public BlockBoardDriver(Board board, TetrominoGenerator generator) {
        this(board, generator, new Random());
    }

    public BlockBoardDriver(Board board, TetrominoGenerator generator, Random itemRandom) {
        this(board, generator, itemRandom, uniformItems(itemRandom));
    }

    /** The selector chooses an item only after a ten-line milestone is earned. */
    public BlockBoardDriver(Board board, TetrominoGenerator generator, Random itemRandom,
                            Supplier<ItemType> itemSelector) {
        this.board = Objects.requireNonNull(board, "board");
        this.generator = Objects.requireNonNull(generator, "generator");
        this.itemRandom = Objects.requireNonNull(itemRandom, "itemRandom");
        this.itemSelector = Objects.requireNonNull(itemSelector, "itemSelector");
        if (board.rows() != GameConstants.BOARD_ROWS || board.cols() != GameConstants.BOARD_COLS) {
            throw new IllegalArgumentException("Game board must be 20 rows by 10 columns");
        }
    }

    @Override
    public void reset() {
        board.clear();
        current = null;
        currentAnvil = null;
        anvilHorizontalLocked = false;
        currentItem = ItemType.NONE;
        currentItemIndex = -1;
        lockedItem = ItemType.NONE;
        clearedLines = 0;
        pendingItems = 0;
        preview.clear();
        fillPreview();
    }

    @Override
    public boolean spawnNextBlock() {
        if (current != null || currentAnvil != null) {
            throw new IllegalStateException("The current block must be locked first");
        }
        if (preview.isEmpty()) throw new IllegalStateException("Reset the board before spawning");
        QueuedBlock next = preview.removeFirst();
        fillPreview();
        if (next.item() == ItemType.ANVIL) {
            Anvil candidate = new Anvil(0, (board.cols() - Anvil.WIDTH) / 2);
            if (!canPlaceAnvil(candidate)) return false;
            currentAnvil = candidate;
            anvilHorizontalLocked = false;
            refreshAnvilContact();
            return true;
        }
        Tetromino candidate = spawnCandidate(next.type());
        if (!CollisionChecker.canPlace(board, candidate)) return false;
        current = candidate;
        currentItem = next.item();
        currentItemIndex = next.itemIndex();
        return true;
    }

    @Override
    public boolean moveLeft() {
        if (currentAnvil != null) return moveAnvilSideways(-1);
        return adopt(requireCurrent().moveBy(0, -1));
    }

    @Override
    public boolean moveRight() {
        if (currentAnvil != null) return moveAnvilSideways(1);
        return adopt(requireCurrent().moveBy(0, 1));
    }

    @Override
    public boolean moveDown() {
        if (currentAnvil != null) return moveAnvilDown();
        return adopt(requireCurrent().moveBy(1, 0));
    }

    @Override
    public boolean rotateClockwise() {
        if (currentAnvil != null) return false; // The weight always remains four cells wide.
        Tetromino rotated = requireCurrent().rotateClockwise();
        if (adopt(rotated)) return true;

        int minCol = rotated.cells().stream().mapToInt(Cell::col).min().orElseThrow();
        int maxCol = rotated.cells().stream().mapToInt(Cell::col).max().orElseThrow();
        // Shift only far enough to bring occupied cells back inside a side wall.
        int correction = 0;
        if (minCol < 0) correction = -minCol;
        else if (maxCol >= board.cols()) correction = board.cols() - 1 - maxCol;
        return correction != 0 && adopt(rotated.moveBy(0, correction));
    }

    /** Select the rules for the next game. */
    public void setItemMode(boolean itemMode) {
        this.itemMode = itemMode;
    }

    @Override
    public int hardDrop() {
        if (currentAnvil != null) {
            int distance = 0;
            while (moveAnvilDown()) distance++;
            return distance;
        }
        int distance = DropCalculator.dropDistance(board, requireCurrent());
        current = current.moveBy(distance, 0);
        return distance;
    }

    @Override
    public GameEngine.LockResult lockAndClearLines() {
        if (currentAnvil != null) {
            if (currentAnvil.row() + Anvil.HEIGHT != board.rows()) {
                throw new IllegalStateException("The anvil must reach the floor before being consumed");
            }
            currentAnvil = null;
            anvilHorizontalLocked = false;
            lockedItem = ItemType.ANVIL;
            return new GameEngine.LockResult(0, false);
        }
        Tetromino landed = requireCurrent();
        if (!board.tryPlace(landed)) {
            throw new IllegalStateException("A valid falling block could not be locked");
        }
        int forcedRow = currentItem == ItemType.LINE_DELETE
                ? landed.cells().get(currentItemIndex).row() : -1;
        if (currentItem == ItemType.BOMB) {
            // Explode before clearing; rows this piece completed stay intact so they still clear and score.
            board.eraseCells(Bomb.blastCells(board, landed.cells().get(currentItemIndex)));
        }
        lockedItem = currentItem;
        current = null;
        currentItem = ItemType.NONE;
        currentItemIndex = -1;
        int removed = board.clearLinesIncluding(forcedRow);
        if (itemMode) {
            int previousMilestones = clearedLines / 10;
            clearedLines += removed;
            pendingItems += clearedLines / 10 - previousMilestones;
        }
        return new GameEngine.LockResult(removed, false);
    }

    @Override
    public boolean awardsDropPoints() {
        return currentAnvil == null;
    }

    @Override
    public ItemType lockedItem() {
        return lockedItem;
    }

    @Override
    public BoardSnapshot snapshot() {
        int[][] cells = new int[board.rows()][board.cols()];
        for (int row = 0; row < board.rows(); row++) {
            for (int col = 0; col < board.cols(); col++) {
                cells[row][col] = board.cellAt(row, col)
                        .map(TetrominoType::id).orElse(BoardSnapshot.EMPTY);
            }
        }
        if (current != null) {
            var occupied = current.cells();
            for (int index = 0; index < occupied.size(); index++) {
                Cell cell = occupied.get(index);
                cells[cell.row()][cell.col()] = index == currentItemIndex
                        ? ItemCellIds.boardCell(currentItem.id(), current.type().id()) : current.type().id();
            }
        }
        if (currentAnvil != null) {
            for (Cell cell : currentAnvil.cells()) cells[cell.row()][cell.col()] = AnvilIds.ANVIL_ID;
        }
        return new BoardSnapshot(cells);
    }

    @Override
    public int[] nextBlockTypes() {
        return preview.stream().mapToInt(next -> next.item() == ItemType.ANVIL ? AnvilIds.ANVIL_ID
                : next.item().isAttachedToBlock()
                        ? ItemCellIds.preview(next.item().id(), next.type().id(), next.itemIndex())
                        : next.type().id()).toArray();
    }

    private Tetromino spawnCandidate(TetrominoType type) {
        Tetromino origin = new Tetromino(type, Rotation.SPAWN, 0, 0);
        int minRow = origin.cells().stream().mapToInt(Cell::row).min().orElseThrow();
        int minCol = origin.cells().stream().mapToInt(Cell::col).min().orElseThrow();
        int maxCol = origin.cells().stream().mapToInt(Cell::col).max().orElseThrow();
        int occupiedWidth = maxCol - minCol + 1;
        int left = Math.floorDiv(board.cols() - occupiedWidth, 2);
        return origin.moveBy(-minRow, left - minCol);
    }

    private void fillPreview() {
        while (preview.size() < GameConstants.NEXT_QUEUE_SIZE) {
            boolean earnedItem = itemMode && pendingItems > 0;
            ItemType item = earnedItem ? Objects.requireNonNull(itemSelector.get(), "item type") : ItemType.NONE;
            if (earnedItem && item == ItemType.NONE) {
                throw new IllegalStateException("An earned item must not be a regular block");
            }
            TetrominoType type = item == ItemType.ANVIL ? null
                    : Objects.requireNonNull(generator.next(), "next block type");
            int itemIndex = item.isAttachedToBlock() ? itemRandom.nextInt(4) : -1;
            preview.addLast(new QueuedBlock(type, item, itemIndex));
            if (earnedItem) pendingItems--;
        }
    }

    /** Every earnable item has the same chance; adding an ItemType value extends the choice. */
    private static Supplier<ItemType> uniformItems(Random itemRandom) {
        List<ItemType> items = ItemType.earnable();
        return () -> items.get(itemRandom.nextInt(items.size()));
    }

    private boolean canPlaceAnvil(Anvil candidate) {
        for (Cell cell : candidate.cells()) {
            if (cell.row() < 0 || cell.row() >= board.rows()
                    || cell.col() < 0 || cell.col() >= board.cols()
                    || board.cellAt(cell.row(), cell.col()).isPresent()) return false;
        }
        return true;
    }

    private void refreshAnvilContact() {
        int below = currentAnvil.row() + Anvil.HEIGHT;
        if (below >= board.rows()) {
            anvilHorizontalLocked = true;
            return;
        }
        for (int col = currentAnvil.col(); col < currentAnvil.col() + Anvil.WIDTH; col++) {
            if (board.cellAt(below, col).isPresent()) {
                anvilHorizontalLocked = true;
                return;
            }
        }
    }

    private boolean moveAnvilSideways(int deltaCol) {
        refreshAnvilContact();
        if (anvilHorizontalLocked) return false;
        Anvil candidate = currentAnvil.moveBy(0, deltaCol);
        if (!canPlaceAnvil(candidate)) return false;
        currentAnvil = candidate;
        refreshAnvilContact();
        return true;
    }

    private boolean moveAnvilDown() {
        refreshAnvilContact();
        Anvil candidate = currentAnvil.moveBy(1, 0);
        if (candidate.row() + Anvil.HEIGHT > board.rows()) return false;
        // Latch contact before crushing; removing the obstacle must never unlock horizontal movement.
        if (candidate.cells().stream().anyMatch(cell -> board.cellAt(cell.row(), cell.col()).isPresent())) {
            anvilHorizontalLocked = true;
        }
        board.eraseCells(candidate.cells());
        currentAnvil = candidate;
        refreshAnvilContact();
        return true;
    }

    private boolean adopt(Tetromino candidate) {
        if (!CollisionChecker.canPlace(board, candidate)) return false;
        current = candidate;
        return true;
    }

    private Tetromino requireCurrent() {
        if (current == null) throw new IllegalStateException("No falling block");
        return current;
    }
}

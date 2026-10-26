package com.team.tetris.game;

import com.team.tetris.block.Board;
import com.team.tetris.block.TetrominoGenerator;
import com.team.tetris.block.TetrominoType;
import com.team.tetris.block.WeightedTetrominoGenerator;
import com.team.tetris.common.Difficulty;
import com.team.tetris.common.constants.GameConstants;
import com.team.tetris.common.events.BoardSnapshot;
import com.team.tetris.common.events.CellCodes;
import com.team.tetris.game.item.Anvil;
import com.team.tetris.game.item.AnvilPiece;
import com.team.tetris.game.item.FallingPiece;
import com.team.tetris.game.item.LockOutcome;

import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Random;
import java.util.function.Supplier;

/**
 * Adapts the block domain to the game engine; used on the Swing EDT.
 * The falling piece's own rules live in {@link FallingPiece} implementations; this class owns the
 * board, the preview queue and the item opportunity counter.
 */
public final class BlockBoardDriver implements GameEngine.BoardDriver {
    private static final int ANVIL_CODE = CellCodes.cell(Anvil.PALETTE.id(), ItemType.ANVIL.symbol());
    private final Board board;
    private final TetrominoGenerator generator;
    private final ArrayDeque<QueuedBlock> preview = new ArrayDeque<>();
    private final Random itemRandom;
    private final Supplier<ItemType> itemSelector;
    private FallingPiece current;
    private int clearedLines;
    private int pendingItems;
    private boolean itemMode;

    private record QueuedBlock(TetrominoType type, ItemType item, int itemIndex) { }

    public BlockBoardDriver() {
        this(new Board(), new WeightedTetrominoGenerator(new Random(), Difficulty.NORMAL));
    }

    @Override
    public void configureDifficulty(Difficulty difficulty) {
        Objects.requireNonNull(difficulty, "difficulty");
        if (generator instanceof WeightedTetrominoGenerator weighted) weighted.setDifficulty(difficulty);
    }

    public BlockBoardDriver(Board board, TetrominoGenerator generator) {
        this(board, generator, new Random());
    }

    public BlockBoardDriver(Board board, TetrominoGenerator generator, Random itemRandom) {
        this(board, generator, itemRandom,
                () -> itemRandom.nextInt(2) == 0 ? ItemType.LINE_DELETE : ItemType.ANVIL);
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
        clearedLines = 0;
        pendingItems = 0;
        preview.clear();
        fillPreview();
    }

    @Override
    public boolean spawnNextBlock() {
        if (current != null) throw new IllegalStateException("The current block must be locked first");
        if (preview.isEmpty()) throw new IllegalStateException("Reset the board before spawning");
        QueuedBlock next = preview.removeFirst();
        fillPreview();
        FallingPiece piece = next.item() == ItemType.ANVIL
                ? AnvilPiece.spawn(board, ItemType.ANVIL.symbol())
                : TetrominoPiece.spawn(board, next.type(), next.item(), next.itemIndex());
        if (piece == null) return false;
        current = piece;
        return true;
    }

    @Override
    public boolean moveLeft() {
        return requireCurrent().moveLeft(board);
    }

    @Override
    public boolean moveRight() {
        return requireCurrent().moveRight(board);
    }

    @Override
    public boolean moveDown() {
        return requireCurrent().moveDown(board);
    }

    @Override
    public boolean rotateClockwise() {
        return requireCurrent().rotateClockwise(board);
    }

    @Override
    public int hardDrop() {
        return requireCurrent().hardDrop(board);
    }

    /** Select the rules for the next game. */
    public void setItemMode(boolean itemMode) {
        this.itemMode = itemMode;
    }

    @Override
    public GameEngine.LockResult lockAndClearLines() {
        LockOutcome outcome = requireCurrent().lock(board);
        current = null;
        if (!outcome.clearsLines()) return new GameEngine.LockResult(0, false);
        BoardSnapshot boardBeforeClear = snapshot();
        List<Integer> clearedRows = board.clearLinesIncludingAndGetRows(outcome.forcedRows());
        int removed = clearedRows.size();
        if (itemMode) {
            int previousMilestones = clearedLines / 10;
            clearedLines += removed;
            pendingItems += clearedLines / 10 - previousMilestones;
        }
        return new GameEngine.LockResult(removed, false, clearedRows,
                clearedRows.isEmpty() ? null : boardBeforeClear);
    }

    @Override
    public boolean awardsDropPoints() {
        return current == null || current.awardsDropPoints();
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
        if (current != null) current.paint(cells);
        return new BoardSnapshot(cells);
    }

    @Override
    public BoardSnapshot fallingSnapshot() {
        return current == null ? null : pieceSnapshot(current);
    }

    @Override
    public BoardSnapshot landingSnapshot() {
        FallingPiece landing = current == null ? null : current.landingPreview(board);
        return landing == null ? null : pieceSnapshot(landing);
    }

    private BoardSnapshot pieceSnapshot(FallingPiece piece) {
        int[][] cells = new int[board.rows()][board.cols()];
        for (int[] row : cells) {
            Arrays.fill(row, BoardSnapshot.EMPTY);
        }
        piece.paint(cells);
        return new BoardSnapshot(cells);
    }

    @Override
    public int[] nextBlockTypes() {
        return preview.stream().mapToInt(next -> switch (next.item()) {
            case NONE -> next.type().id();
            case LINE_DELETE -> CellCodes.preview(next.type().id(), next.item().symbol(), next.itemIndex());
            case ANVIL -> ANVIL_CODE;
        }).toArray();
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
            int itemIndex = item == ItemType.LINE_DELETE ? itemRandom.nextInt(4) : -1;
            preview.addLast(new QueuedBlock(type, item, itemIndex));
            if (earnedItem) pendingItems--;
        }
    }

    private FallingPiece requireCurrent() {
        if (current == null) throw new IllegalStateException("No falling block");
        return current;
    }
}

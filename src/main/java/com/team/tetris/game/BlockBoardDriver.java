/*
 * block 패키지를 GameEngine.BoardDriver 계약에 맞게 연결하는 어댑터. 보드와 다음 블록 큐를 소유한다.
 * 조각 규칙은 FallingPiece, 아이템 효과는 Item, 등장 규칙은 ItemPolicy에 맡기고 여기서는 위임만 한다.
 */
package com.team.tetris.game;

import com.team.tetris.block.Board;
import com.team.tetris.block.TetrominoGenerator;
import com.team.tetris.block.TetrominoType;
import com.team.tetris.block.WeightedTetrominoGenerator;
import com.team.tetris.common.Difficulty;
import com.team.tetris.common.constants.GameConstants;
import com.team.tetris.common.events.BoardSnapshot;
import com.team.tetris.game.item.EmbeddedItem;
import com.team.tetris.game.item.FallingPiece;
import com.team.tetris.game.item.Item;
import com.team.tetris.game.item.ItemPolicy;
import com.team.tetris.game.item.ItemRegistry;
import com.team.tetris.game.item.ItemSelector;
import com.team.tetris.game.item.LockOutcome;
import com.team.tetris.game.item.StandaloneItem;
import com.team.tetris.game.item.WeightedItemSelector;

import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Random;
import java.util.random.RandomGenerator;

/**
 * Adapts the block domain to the game engine; used on the Swing EDT.
 *
 * This class owns the board and the preview queue only. The falling piece's rules live in
 * {@link FallingPiece} implementations, item effects in {@link Item} implementations, and the
 * "when does an item appear" rule in {@link ItemPolicy}. Adding an item changes none of this file.
 */
public final class BlockBoardDriver implements GameEngine.BoardDriver {
    /** Every tetromino has four cells; an embedded item attaches to one of them at random. */
    private static final int CELLS_PER_TETROMINO = 4;
    private final Board board;
    private final TetrominoGenerator generator;
    private final RandomGenerator random;
    private final ItemPolicy itemModePolicy;
    private final ArrayDeque<QueuedPiece> preview = new ArrayDeque<>();
    private ItemPolicy itemPolicy = ItemPolicy.none();
    private FallingPiece current;
    private Item lockedItem;

    public BlockBoardDriver() {
        this(ItemRegistry.defaults());
    }

    /** The real game: weighted block generation and every registered item with equal odds. */
    public BlockBoardDriver(ItemRegistry items) {
        this(new Board(), new WeightedTetrominoGenerator(new Random(), Difficulty.NORMAL), new Random(), items);
    }

    public BlockBoardDriver(Board board, TetrominoGenerator generator) {
        this(board, generator, new Random(), ItemRegistry.defaults());
    }

    public BlockBoardDriver(Board board, TetrominoGenerator generator, RandomGenerator random, ItemRegistry items) {
        this(board, generator, random, WeightedItemSelector.uniform(items, random));
    }

    /** The selector chooses an item only after a ten-line milestone is earned (item mode only). */
    public BlockBoardDriver(Board board, TetrominoGenerator generator, RandomGenerator random, ItemSelector selector) {
        this(board, generator, random, ItemPolicy.everyLines(GameConstants.LINES_PER_ITEM, selector));
    }

    /** @param itemModePolicy the item rule used while item mode is on; normal mode never spawns items */
    public BlockBoardDriver(Board board, TetrominoGenerator generator, RandomGenerator random,
                            ItemPolicy itemModePolicy) {
        this.board = Objects.requireNonNull(board, "board");
        this.generator = Objects.requireNonNull(generator, "generator");
        this.random = Objects.requireNonNull(random, "random");
        this.itemModePolicy = Objects.requireNonNull(itemModePolicy, "itemModePolicy");
        if (board.rows() != GameConstants.BOARD_ROWS || board.cols() != GameConstants.BOARD_COLS) {
            throw new IllegalArgumentException("Game board must be 20 rows by 10 columns");
        }
    }

    @Override
    public void configureDifficulty(Difficulty difficulty) {
        Objects.requireNonNull(difficulty, "difficulty");
        if (generator instanceof WeightedTetrominoGenerator weighted) weighted.setDifficulty(difficulty);
    }

    /** Select the rules for the next game. */
    public void setItemMode(boolean itemMode) {
        itemPolicy = itemMode ? itemModePolicy : ItemPolicy.none();
    }

    @Override
    public void reset() {
        board.clear();
        current = null;
        lockedItem = null;
        itemPolicy.reset();
        preview.clear();
        fillPreview();
    }

    @Override
    public boolean spawnNextBlock() {
        if (current != null) throw new IllegalStateException("The current block must be locked first");
        if (preview.isEmpty()) throw new IllegalStateException("Reset the board before spawning");
        QueuedPiece next = preview.removeFirst();
        fillPreview();
        FallingPiece piece = next.spawn(board);
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

    @Override
    public GameEngine.LockResult lockAndClearLines() {
        FallingPiece locking = requireCurrent();
        LockOutcome outcome = locking.lock(board);
        lockedItem = locking.item();
        current = null;
        if (!outcome.clearsLines()) return new GameEngine.LockResult(0, false);
        BoardSnapshot boardBeforeClear = snapshot();
        List<Integer> clearedRows = board.clearLinesIncludingAndGetRows(outcome.forcedRows());
        itemPolicy.onLinesCleared(clearedRows.size());
        return new GameEngine.LockResult(clearedRows.size(), false, clearedRows,
                clearedRows.isEmpty() ? null : boardBeforeClear);
    }

    @Override
    public Item lockedItem() {
        return lockedItem;
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
        return preview.stream().mapToInt(QueuedPiece::previewCode).toArray();
    }

    private void fillPreview() {
        while (preview.size() < GameConstants.NEXT_QUEUE_SIZE) {
            preview.addLast(queue(itemPolicy.nextItem().orElse(null)));
        }
    }

    /** The sealed Item hierarchy makes this switch exhaustive: a new item kind would fail to compile here. */
    private QueuedPiece queue(Item item) {
        return switch (item) {
            case null -> new QueuedPiece.Regular(nextType(), null, -1);
            case StandaloneItem standalone -> new QueuedPiece.Standalone(standalone);
            case EmbeddedItem embedded ->
                    new QueuedPiece.Regular(nextType(), embedded, random.nextInt(CELLS_PER_TETROMINO));
        };
    }

    private TetrominoType nextType() {
        return Objects.requireNonNull(generator.next(), "next block type");
    }

    private FallingPiece requireCurrent() {
        if (current == null) throw new IllegalStateException("No falling block");
        return current;
    }
}

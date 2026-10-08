package com.team.tetris.game;

import com.team.tetris.block.Board;
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

import java.util.ArrayDeque;
import java.util.Objects;
import java.util.Random;

/** Adapts the block domain to the game engine; used on the Swing EDT. */
public final class BlockBoardDriver implements GameEngine.BoardDriver {
    private final Board board;
    private final TetrominoGenerator generator;
    private final ArrayDeque<TetrominoType> preview = new ArrayDeque<>();
    private Tetromino current;

    public BlockBoardDriver() {
        this(new Board(), new UniformTetrominoGenerator(new Random()));
    }

    public BlockBoardDriver(Board board, TetrominoGenerator generator) {
        this.board = Objects.requireNonNull(board, "board");
        this.generator = Objects.requireNonNull(generator, "generator");
        if (board.rows() != GameConstants.BOARD_ROWS || board.cols() != GameConstants.BOARD_COLS) {
            throw new IllegalArgumentException("Game board must be 20 rows by 10 columns");
        }
    }

    @Override
    public void reset() {
        board.clear();
        current = null;
        preview.clear();
        fillPreview();
    }

    @Override
    public boolean spawnNextBlock() {
        if (current != null) throw new IllegalStateException("The current block must be locked first");
        if (preview.isEmpty()) throw new IllegalStateException("Reset the board before spawning");
        TetrominoType type = preview.removeFirst();
        fillPreview();
        Tetromino candidate = spawnCandidate(type);
        if (!CollisionChecker.canPlace(board, candidate)) return false;
        current = candidate;
        return true;
    }

    @Override
    public boolean moveLeft() {
        return adopt(requireCurrent().moveBy(0, -1));
    }

    @Override
    public boolean moveRight() {
        return adopt(requireCurrent().moveBy(0, 1));
    }

    @Override
    public boolean moveDown() {
        return adopt(requireCurrent().moveBy(1, 0));
    }

    @Override
    public boolean rotateClockwise() {
        return adopt(requireCurrent().rotateClockwise());
    }

    @Override
    public int hardDrop() {
        int distance = DropCalculator.dropDistance(board, requireCurrent());
        current = current.moveBy(distance, 0);
        return distance;
    }

    @Override
    public GameEngine.LockResult lockAndClearLines() {
        Tetromino landed = requireCurrent();
        if (!board.tryPlace(landed)) {
            throw new IllegalStateException("A valid falling block could not be locked");
        }
        current = null;
        return new GameEngine.LockResult(board.clearFullLines(), false);
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
            for (Cell cell : current.cells()) {
                cells[cell.row()][cell.col()] = current.type().id();
            }
        }
        return new BoardSnapshot(cells);
    }

    @Override
    public int[] nextBlockTypes() {
        return preview.stream().mapToInt(TetrominoType::id).toArray();
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
            preview.addLast(Objects.requireNonNull(generator.next(), "next block type"));
        }
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

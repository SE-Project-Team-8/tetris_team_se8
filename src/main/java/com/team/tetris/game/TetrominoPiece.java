/*
 * 일반 테트로미노의 낙하 조각(FallingPiece 구현). 이동·회전(벽 보정)·하드드롭·고정 규칙을 가지며,
 * 네 칸 중 하나에 포함형 아이템을 붙여 고정 시 아이템의 onLock을 호출한다.
 */
package com.team.tetris.game;

import com.team.tetris.block.Board;
import com.team.tetris.block.Cell;
import com.team.tetris.block.CollisionChecker;
import com.team.tetris.block.DropCalculator;
import com.team.tetris.block.Rotation;
import com.team.tetris.block.Tetromino;
import com.team.tetris.block.TetrominoType;
import com.team.tetris.common.events.CellCodes;
import com.team.tetris.game.item.EmbeddedItem;
import com.team.tetris.game.item.FallingPiece;
import com.team.tetris.game.item.LockOutcome;

import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * 일반 테트로미노의 낙하 규칙. 네 칸 중 하나에 포함형 아이템을 붙일 수 있으며, 아이템은 이동·회전해도
 * 같은 칸을 따라간다. 이동·회전은 불변 {@link Tetromino} 후보를 만들어 충돌 검사 후 채택한다.
 * 고정 시 아이템의 보드 효과({@link EmbeddedItem#onLock})를 적용한다.
 */
public final class TetrominoPiece implements FallingPiece {
    private final EmbeddedItem item;
    private final int itemIndex;
    private Tetromino tetromino;

    TetrominoPiece(Tetromino tetromino, EmbeddedItem item, int itemIndex) {
        this.tetromino = Objects.requireNonNull(tetromino, "tetromino");
        if ((item == null) != (itemIndex < 0)) {
            throw new IllegalArgumentException("An attached item needs exactly one cell index");
        }
        if (itemIndex >= tetromino.cells().size()) {
            throw new IllegalArgumentException("itemIndex outside the piece: " + itemIndex);
        }
        this.item = item;
        this.itemIndex = itemIndex;
    }

    /**
     * 실제 점유 폭으로 중앙 정렬하고 가장 위 점유 셀이 0행에 오도록 생성한다. 생성 위치가 막히면 null.
     *
     * @param item 붙일 포함형 아이템. 없으면 null이고 {@code itemIndex}는 -1
     */
    public static TetrominoPiece spawn(Board board, TetrominoType type, EmbeddedItem item, int itemIndex) {
        Tetromino origin = new Tetromino(type, Rotation.SPAWN, 0, 0);
        int minRow = origin.cells().stream().mapToInt(Cell::row).min().orElseThrow();
        int minCol = origin.cells().stream().mapToInt(Cell::col).min().orElseThrow();
        int maxCol = origin.cells().stream().mapToInt(Cell::col).max().orElseThrow();
        int occupiedWidth = maxCol - minCol + 1;
        int left = Math.floorDiv(board.cols() - occupiedWidth, 2);
        Tetromino candidate = origin.moveBy(-minRow, left - minCol);
        if (!CollisionChecker.canPlace(board, candidate)) return null;
        return new TetrominoPiece(candidate, item, itemIndex);
    }

    @Override
    public boolean moveLeft(Board board) {
        return adopt(board, tetromino.moveBy(0, -1));
    }

    @Override
    public boolean moveRight(Board board) {
        return adopt(board, tetromino.moveBy(0, 1));
    }

    @Override
    public boolean moveDown(Board board) {
        return adopt(board, tetromino.moveBy(1, 0));
    }

    @Override
    public boolean rotateClockwise(Board board) {
        Tetromino rotated = tetromino.rotateClockwise();
        if (adopt(board, rotated)) return true;

        int minCol = rotated.cells().stream().mapToInt(Cell::col).min().orElseThrow();
        int maxCol = rotated.cells().stream().mapToInt(Cell::col).max().orElseThrow();
        // 점유 칸이 옆벽 안으로 들어올 만큼만 민다. 고정 블록을 통과하는 회전은 허용하지 않는다.
        int correction = 0;
        if (minCol < 0) correction = -minCol;
        else if (maxCol >= board.cols()) correction = board.cols() - 1 - maxCol;
        return correction != 0 && adopt(board, rotated.moveBy(0, correction));
    }

    @Override
    public int hardDrop(Board board) {
        int distance = DropCalculator.dropDistance(board, tetromino);
        tetromino = tetromino.moveBy(distance, 0);
        return distance;
    }

    @Override
    public boolean awardsDropPoints() {
        return true;
    }

    @Override
    public LockOutcome lock(Board board) {
        if (!board.tryPlace(tetromino)) {
            throw new IllegalStateException("A valid falling block could not be locked");
        }
        Set<Integer> forcedRows = item == null ? Set.of()
                : Objects.requireNonNull(item.onLock(board, tetromino.cells().get(itemIndex)), "forced rows");
        return LockOutcome.clearLines(forcedRows);
    }

    @Override
    public List<Cell> cells() {
        return tetromino.cells();
    }

    @Override
    public void paint(int[][] cells) {
        List<Cell> occupied = tetromino.cells();
        int typeId = tetromino.type().id();
        for (int index = 0; index < occupied.size(); index++) {
            Cell cell = occupied.get(index);
            cells[cell.row()][cell.col()] = index == itemIndex ? CellCodes.cell(typeId, item.symbol()) : typeId;
        }
    }

    @Override
    public FallingPiece landingPreview(Board board) {
        int distance = DropCalculator.dropDistance(board, tetromino);
        return distance > 0 ? new TetrominoPiece(tetromino.moveBy(distance, 0), item, itemIndex) : null;
    }

    @Override
    public EmbeddedItem item() {
        return item;
    }

    private boolean adopt(Board board, Tetromino candidate) {
        if (!CollisionChecker.canPlace(board, candidate)) return false;
        tetromino = candidate;
        return true;
    }
}

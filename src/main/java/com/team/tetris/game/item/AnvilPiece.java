/*
 * 무게추의 낙하 조각(FallingPiece 구현). 접촉 전 좌우 이동, 첫 접촉 후 좌우 잠금, 내려가며 아래 블록 파괴,
 * 회전 없음, 낙하 점수 없음, 바닥에서 소모(LockOutcome.consumed).
 */
package com.team.tetris.game.item;

import com.team.tetris.block.Board;
import com.team.tetris.block.Cell;
import com.team.tetris.common.events.CellCodes;

import java.util.List;
import java.util.Objects;

/**
 * 무게추의 낙하 규칙. 접촉 전에는 좌우로 움직이고, 바닥이나 블록에 한 번 닿으면 좌우가 영구히 잠기며,
 * 내려갈 때마다 새 위치의 고정 블록을 지운다. 회전하지 않고 낙하 점수를 주지 않으며 바닥에서 소모된다.
 * 명세: docs/items/anvil.md
 */
public final class AnvilPiece implements FallingPiece {
    private final int code;
    private Anvil anvil;
    private boolean horizontalLocked;

    private AnvilPiece(Anvil anvil, StandaloneItem item) {
        this.anvil = Objects.requireNonNull(anvil, "anvil");
        this.code = CellCodes.cell(item.paletteType().id(), item.symbol());
    }

    /** 위쪽 중앙에 생성한다. 생성 공간이 점유되어 있으면 null이며 보드를 바꾸지 않는다. */
    static AnvilPiece spawn(Board board, StandaloneItem item) {
        Anvil candidate = new Anvil(0, (board.cols() - Anvil.WIDTH) / 2);
        if (!canPlace(board, candidate)) return null;
        AnvilPiece piece = new AnvilPiece(candidate, item);
        piece.refreshContact(board);
        return piece;
    }

    @Override
    public boolean moveLeft(Board board) {
        return moveSideways(board, -1);
    }

    @Override
    public boolean moveRight(Board board) {
        return moveSideways(board, 1);
    }

    @Override
    public boolean moveDown(Board board) {
        refreshContact(board);
        Anvil candidate = anvil.moveBy(1, 0);
        if (candidate.row() + Anvil.HEIGHT > board.rows()) return false;
        // 부수기 전에 접촉을 먼저 기록한다. 장애물을 지웠다고 좌우 잠금이 풀리면 안 된다.
        if (candidate.cells().stream().anyMatch(cell -> board.cellAt(cell.row(), cell.col()).isPresent())) {
            horizontalLocked = true;
        }
        board.eraseCells(candidate.cells());
        anvil = candidate;
        refreshContact(board);
        return true;
    }

    /** 무게추는 항상 너비 4칸을 유지한다. */
    @Override
    public boolean rotateClockwise(Board board) {
        return false;
    }

    @Override
    public int hardDrop(Board board) {
        int distance = 0;
        while (moveDown(board)) distance++;
        return distance;
    }

    @Override
    public boolean awardsDropPoints() {
        return false;
    }

    @Override
    public LockOutcome lock(Board board) {
        if (anvil.row() + Anvil.HEIGHT != board.rows()) {
            throw new IllegalStateException("The anvil must reach the floor before being consumed");
        }
        return LockOutcome.consumed();
    }

    @Override
    public List<Cell> cells() {
        return anvil.cells();
    }

    @Override
    public void paint(int[][] cells) {
        for (Cell cell : anvil.cells()) cells[cell.row()][cell.col()] = code;
    }

    /** 무게추는 아래 블록을 모두 지우므로 착지 위치 고스트를 그리지 않는다. */
    @Override
    public FallingPiece landingPreview(Board board) {
        return null;
    }

    private boolean moveSideways(Board board, int deltaCol) {
        refreshContact(board);
        if (horizontalLocked) return false;
        Anvil candidate = anvil.moveBy(0, deltaCol);
        if (!canPlace(board, candidate)) return false;
        anvil = candidate;
        refreshContact(board);
        return true;
    }

    private void refreshContact(Board board) {
        int below = anvil.row() + Anvil.HEIGHT;
        if (below >= board.rows()) {
            horizontalLocked = true;
            return;
        }
        for (int col = anvil.col(); col < anvil.col() + Anvil.WIDTH; col++) {
            if (board.cellAt(below, col).isPresent()) {
                horizontalLocked = true;
                return;
            }
        }
    }

    private static boolean canPlace(Board board, Anvil candidate) {
        for (Cell cell : candidate.cells()) {
            if (cell.row() < 0 || cell.row() >= board.rows()
                    || cell.col() < 0 || cell.col() >= board.cols()
                    || board.cellAt(cell.row(), cell.col()).isPresent()) return false;
        }
        return true;
    }
}

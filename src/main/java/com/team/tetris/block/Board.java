package com.team.tetris.block;

import com.team.tetris.common.constants.GameConstants;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * 고정된 블록 셀을 소유하는 보드. 현재 낙하 블록·점수·타이머는 엔진이 소유한다.
 *
 * 내부 배열의 null은 빈 칸이며 외부로 노출하지 않는다. 셀 변경은 배치·행 삭제·초기화로
 * 제한된다. 한 세션의 엔진이 호출을 직렬화해야 하며 이 클래스는 스레드 안전하지 않다.
 * 실패 시 부분 배치를 하지 않는 보장은 동시성 트랜잭션을 의미하지 않는다.
 */
public final class Board implements BoardView {
    private final int rows;
    private final int cols;
    private TetrominoType[][] cells;

    /** 공용 상수의 20행 × 10열 빈 게임 보드를 생성한다. */
    public Board() {
        this(GameConstants.BOARD_ROWS, GameConstants.BOARD_COLS);
    }

    /**
     * 테스트 등에서 사용할 지정 크기의 빈 보드를 생성한다.
     * 실제 게임은 기본 생성자를 사용하며 화면 배율 변경으로 논리 크기를 바꾸지 않는다.
     *
     * @param rows 양의 행 수
     * @param cols 양의 열 수
     * @throws IllegalArgumentException 행 또는 열 수가 0 이하인 경우
     */
    public Board(int rows, int cols) {
        if (rows <= 0 || cols <= 0) {
            throw new IllegalArgumentException(
                    "Board size must be positive: rows=" + rows + ", cols=" + cols);
        }
        this.rows = rows;
        this.cols = cols;
        this.cells = new TetrominoType[rows][cols];
    }

    /** {@inheritDoc} */
    @Override
    public int rows() {
        return rows;
    }

    /** {@inheritDoc} */
    @Override
    public int cols() {
        return cols;
    }

    /** {@inheritDoc} */
    @Override
    public Optional<TetrominoType> cellAt(int row, int col) {
        Objects.checkIndex(row, rows);
        Objects.checkIndex(col, cols);
        return Optional.ofNullable(cells[row][col]);
    }

    /**
     * 후보의 네 셀을 검사한 뒤 모두 고정한다. 실패하면 한 셀도 변경하지 않는다.
     *
     * 착지 여부는 검사하지 않는다. 엔진은 하강 실패 또는 하드드롭 후 호출해야 한다.
     * 이 메서드는 줄 삭제를 자동 수행하지 않으므로 엔진이 삭제 수를 별도로 얻을 수 있다.
     *
     * @param piece 고정할 블록
     * @return 고정 성공 시 true, 경계·점유 충돌이면 false
     * @throws NullPointerException piece가 null인 경우
     */
    public boolean tryPlace(Tetromino piece) {
        Objects.requireNonNull(piece, "piece");
        List<Cell> occupied = piece.cells();
        if (!CollisionChecker.canPlaceCells(this, occupied)) {
            return false;
        }
        // 검사와 기록 사이에는 외부 호출이나 좌표 재계산을 넣지 않는다.
        for (Cell cell : occupied) {
            cells[cell.row()][cell.col()] = piece.type();
        }
        return true;
    }

    /**
     * 완성 행을 한 번에 제거하고 생존 행의 상대적 순서를 유지하며 아래로 압축한다.
     *
     * 새 배열을 완성한 후 참조를 교체해 부분 압축 상태를 남기지 않는다.
     * 시간·추가 공간은 모두 O(rows × cols)이며, 삭제할 행이 없으면 배열을 만들지 않는다.
     * 점수·속도·이벤트는 계산하지 않는다.
     *
     * @return 삭제한 행 수. 완성 행이 없으면 0
     */
    public int clearFullLines() {
        List<Integer> fullRows = LineClearer.findFullRows(this);
        if (fullRows.isEmpty()) {
            return 0;
        }

        TetrominoType[][] compacted = new TetrominoType[rows][cols];
        int nextFull = fullRows.size() - 1;
        int destination = rows - 1;
        for (int source = rows - 1; source >= 0; source--) {
            if (nextFull >= 0 && source == fullRows.get(nextFull)) {
                nextFull--;
            } else {
                // 행 참조를 재사용하지 않아 이후 한 행의 변경이 다른 행에 전파되지 않는다.
                System.arraycopy(cells[source], 0, compacted[destination], 0, cols);
                destination--;
            }
        }
        cells = compacted;
        return fullRows.size();
    }

    /**
     * 크기를 유지하며 모든 고정 셀을 비운다. 반복 호출해도 같은 빈 상태다.
     * 게임 재시작에 필요한 현재 블록·점수·큐 초기화는 엔진이 별도로 수행한다.
     */
    public void clear() {
        for (TetrominoType[] row : cells) {
            Arrays.fill(row, null);
        }
    }
}

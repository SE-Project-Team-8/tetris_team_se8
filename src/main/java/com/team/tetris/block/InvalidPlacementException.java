package com.team.tetris.block;

import java.util.Objects;

/**
 * 하드드롭 계산의 시작 블록이 이미 보드 밖이거나 기존 셀과 겹칠 때 발생한다.
 * 일반 이동·회전의 충돌은 이 예외가 아니라 false로 표현한다.
 */
public final class InvalidPlacementException extends IllegalArgumentException {
    private static final long serialVersionUID = 1L;
    // 예외가 보드나 변경 가능한 상태를 붙잡지 않도록 작은 실패 문맥만 보관한다.
    /** 배치가 거절된 블록 종류. */
    private final TetrominoType type;
    /** 실패 당시 회전 상태. */
    private final Rotation rotation;
    /** 실패 당시 로컬 격자의 기준 행. */
    private final int row;
    /** 실패 당시 로컬 격자의 기준 열. */
    private final int col;
    /** 충돌 검사에 사용한 보드 행 수. */
    private final int rows;
    /** 충돌 검사에 사용한 보드 열 수. */
    private final int cols;

    /**
     * 보드 참조 없이 실패한 배치의 재현에 필요한 값만 저장한다.
     *
     * @param piece 배치할 수 없었던 시작 블록
     * @param rows 검사한 보드 행 수
     * @param cols 검사한 보드 열 수
     * @throws NullPointerException piece가 null인 경우
     */
    public InvalidPlacementException(Tetromino piece, int rows, int cols) {
        super("Cannot calculate drop distance from invalid placement: "
                + Objects.requireNonNull(piece, "piece") + ", board=" + rows + "x" + cols);
        this.type = piece.type();
        this.rotation = piece.rotation();
        this.row = piece.row();
        this.col = piece.col();
        this.rows = rows;
        this.cols = cols;
    }

    /**
     * 저장한 문맥으로 실패 당시 후보를 복원한다.
     * @return 실패 당시의 불변 블록 값
     */
    public Tetromino piece() {
        return new Tetromino(type, rotation, row, col);
    }

    /**
     * 실패 당시 보드의 세로 크기를 조회한다.
     * @return 실패 당시 보드 행 수
     */
    public int rows() {
        return rows;
    }

    /**
     * 실패 당시 보드의 가로 크기를 조회한다.
     * @return 실패 당시 보드 열 수
     */
    public int cols() {
        return cols;
    }
}

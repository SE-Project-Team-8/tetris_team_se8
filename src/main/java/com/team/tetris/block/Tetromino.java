package com.team.tetris.block;

import java.util.List;
import java.util.Objects;

/**
 * 현재 위치와 회전을 포함한 불변 낙하 블록.
 *
 * {@code row}, {@code col}은 점유 셀의 최소 좌표가 아니라 로컬 회전 격자의
 * 왼쪽 위 기준점이다. 예를 들어 I의 초기 점유 행은 {@code row + 1}이다.
 * 이동·회전은 보드와 무관하게 후보를 만들며, 엔진이 충돌 검사 후 후보를 채택한다.
 *
 * @param type 블록 종류
 * @param rotation 회전 상태
 * @param row 로컬 격자의 기준 행
 * @param col 로컬 격자의 기준 열
 */
public record Tetromino(TetrominoType type, Rotation rotation, int row, int col) {

    /**
     * 후보를 생성한다. 음수 기준 좌표를 허용하며 실제 점유 셀로 충돌을 검사한다.
     *
     * @throws NullPointerException 종류 또는 회전이 null인 경우
     * @throws ArithmeticException 점유 셀 좌표가 int 범위를 넘는 경우
     */
    public Tetromino {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(rotation, "rotation");
        // 생성 시 표현 가능성을 보장해 유효한 객체의 cells()는 항상 성공하게 한다.
        for (Cell offset : type.offsets(rotation)) {
            Math.addExact(row, offset.row());
            Math.addExact(col, offset.col());
        }
    }

    /**
     * 보드 좌표계의 네 점유 셀을 반환한다. 목록 순서에는 의미가 없다.
     *
     * @return 수정할 수 없는 셀 목록. 로컬 형태 데이터와 공유되는 배열은 없다.
     */
    public List<Cell> cells() {
        return type.offsets(rotation).stream()
                .map(offset -> new Cell(row + offset.row(), col + offset.col()))
                .toList();
    }

    /**
     * 행·열 이동량을 적용한 새 후보를 만든다. 정상적인 벽 충돌은 여기서 검사하지 않는다.
     *
     * @param deltaRow 행 이동량(하강 1칸은 1)
     * @param deltaCol 열 이동량(왼쪽 1칸은 -1)
     * @return 이동한 후보. 원본은 그대로 유지된다.
     * @throws ArithmeticException 기준점 또는 점유 셀의 좌표가 int 범위를 넘는 경우
     */
    public Tetromino moveBy(int deltaRow, int deltaCol) {
        return new Tetromino(type, rotation,
                Math.addExact(row, deltaRow), Math.addExact(col, deltaCol));
    }

    /**
     * 기준점을 유지한 채 시계 방향 90도 회전한 후보를 만든다.
     * 벽 차기나 보드 경계 보정은 수행하지 않는다.
     *
     * @return 회전한 후보
     * @throws ArithmeticException 새 점유 셀의 좌표가 int 범위를 넘는 경우
     */
    public Tetromino rotateClockwise() {
        return new Tetromino(type, rotation.clockwise(), row, col);
    }
}

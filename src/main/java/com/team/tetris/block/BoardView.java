package com.team.tetris.block;

import java.util.Optional;

/**
 * 고정된 셀에 대한 읽기 전용 계약. 충돌 계산에는 보드 변경 권한이 필요하지 않다.
 *
 * 크기는 양수이며 객체 수명 동안 일정하다. 조회는 non-null Optional을 반환한다.
 * 이 인터페이스는 불변 스냅샷이 아니므로 엔진은 한 계산 중 보드를 변경하지 않아야 한다.
 */
public interface BoardView {
    /**
     * 보드의 고정된 세로 크기를 조회한다.
     * @return 양의 보드 행 수
     */
    int rows();

    /**
     * 보드의 고정된 가로 크기를 조회한다.
     * @return 양의 보드 열 수
     */
    int cols();

    /**
     * 유효한 좌표의 고정 셀을 조회한다. 낙하 중인 블록은 포함하지 않는다.
     *
     * @param row 행 좌표
     * @param col 열 좌표
     * @return 셀의 종류 또는 빈 칸을 나타내는 Optional.empty()
     * @throws IndexOutOfBoundsException 행 또는 열이 보드 범위 밖인 경우
     */
    Optional<TetrominoType> cellAt(int row, int col);
}

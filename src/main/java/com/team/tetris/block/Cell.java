package com.team.tetris.block;

/**
 * 보드 또는 블록 로컬 격자의 불변 좌표. 인자 순서는 항상 행, 열이다.
 *
 * 아래쪽이 행의 양의 방향, 오른쪽이 열의 양의 방향이다. 벽 밖으로 이동한
 * 후보도 표현해야 하므로 음수를 허용한다. 보드 경계 검사는 이 값 객체가 아니라
 * {@link CollisionChecker}가 수행한다.
 *
 * @param row 행 좌표
 * @param col 열 좌표
 */
public record Cell(int row, int col) {
}

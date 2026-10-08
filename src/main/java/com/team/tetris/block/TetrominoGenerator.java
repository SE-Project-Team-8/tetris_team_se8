package com.team.tetris.block;

/**
 * 다음 블록 종류를 공급하는 정책 경계.
 * 생성 위치·회전·미리보기 큐는 엔진이 관리하며 구현은 null을 반환하지 않는다.
 * 1차 요구사항의 게임에는 매번 동일 확률인 {@link UniformTetrominoGenerator}를 사용한다.
 */
@FunctionalInterface
public interface TetrominoGenerator {
    /**
     * 정책에 따라 다음 종류 하나를 선택한다. 보드나 미리보기 큐는 변경하지 않는다.
     * @return 다음 블록 종류. 현재 게임 정책에서는 연속 중복도 정상이다.
     */
    TetrominoType next();
}

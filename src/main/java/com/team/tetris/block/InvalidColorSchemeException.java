package com.team.tetris.block;

/** 팔레트의 종류 누락, 색상 중복 또는 RGB 범위 오류를 나타내는 계약 예외. */
public final class InvalidColorSchemeException extends IllegalArgumentException {
    private static final long serialVersionUID = 1L;

    /**
     * 팔레트 검증에서 발견한 위반 사항을 담는다. 복구와 사용자 안내는 호출 경계가 맡는다.
     *
     * @param message 잘못된 종류·색상 및 기대 조건을 설명하는 개발자용 메시지
     */
    public InvalidColorSchemeException(String message) {
        super(message);
    }
}

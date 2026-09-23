package com.team.tetris.block;

/** 외부 전달 ID가 일곱 블록 중 어떤 종류에도 대응하지 않을 때 발생한다. */
public final class InvalidTetrominoIdException extends IllegalArgumentException {
    private static final long serialVersionUID = 1L;
    /** 변환에 실패한 원래 종류 ID. */
    private final int id;

    /**
     * 종류 ID 변환 실패를 기록한다.
     * @param id 해석할 수 없는 ID(빈 칸 ID도 포함)
     */
    public InvalidTetrominoIdException(int id) {
        super("Unknown tetromino id: " + id + " (expected 0..6)");
        this.id = id;
    }

    /**
     * 메시지 파싱 없이 실패한 값을 조회한다.
     * @return 실패한 원래 ID
     */
    public int id() {
        return id;
    }
}

/*
 * 낙하 중인 조각이 보드와 상호작용하는 규칙(이동·회전·낙하·고정·그리기)의 계약.
 * TetrominoPiece(일반 블록)와 AnvilPiece(무게추)가 구현하고, BlockBoardDriver는 종류를 묻지 않고 위임만 한다.
 */
package com.team.tetris.game.item;

import com.team.tetris.block.Board;
import com.team.tetris.block.Cell;

import java.util.List;

/**
 * 낙하 중인 조각이 보드와 상호작용하는 규칙. 일반 테트로미노와 블록형 아이템(무게추 등)이 각각 구현한다.
 *
 * 드라이버는 조각의 종류를 묻지 않고 이 메서드들에 위임만 한다. 새 블록형 아이템은 이 인터페이스를
 * 구현하는 조각 하나를 추가하면 되고, 드라이버·엔진은 바뀌지 않는다. 보드 변경은 {@link #lock(Board)}와,
 * 조각이 스스로 정한 경우(무게추의 파괴) 이동 메서드 안에서만 일어난다.
 */
public interface FallingPiece {
    boolean moveLeft(Board board);

    boolean moveRight(Board board);

    /** 정확히 한 칸만 내려간다. 실패해도 스스로 고정하지 않는다. */
    boolean moveDown(Board board);

    boolean rotateClockwise(Board board);

    /** 마지막 유효 위치까지 내려가고 실제 이동 칸 수를 돌려준다. 고정하지 않는다. */
    int hardDrop(Board board);

    /** 자동·수동 낙하에 낙하 점수를 주는지. 무게추처럼 점수 없는 조각은 false. */
    boolean awardsDropPoints();

    /**
     * 보드에 고정하거나(일반 블록) 소모하고(무게추), 이어서 줄 삭제를 진행할지와 강제로 지울 행을 알려 준다.
     * 포함형 아이템의 보드 효과는 이 시점에 적용된다.
     */
    LockOutcome lock(Board board);

    /** 현재 점유 칸을 보드 좌표로 돌려준다. */
    List<Cell> cells();

    /** 보드 크기의 스냅샷 배열에 점유 칸의 셀 코드를 기록한다. */
    void paint(int[][] cells);

    /** 지금 하드드롭하면 놓일 위치의 조각(고스트). 이미 착지했거나 고스트를 쓰지 않는 조각은 null. */
    FallingPiece landingPreview(Board board);
}

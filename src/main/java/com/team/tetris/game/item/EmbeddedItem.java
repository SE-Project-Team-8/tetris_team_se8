package com.team.tetris.game.item;

import com.team.tetris.block.Board;
import com.team.tetris.block.Cell;

import java.util.Set;

/** 기본 블록의 한 칸에 붙는 형태의 아이템. 예: 줄 삭제 {@code L}. 어느 칸에 붙을지는 큐가 무작위로 정한다. */
public non-sealed interface EmbeddedItem extends Item {
    /**
     * 아이템을 실은 블록이 고정된 직후, 완성 행을 지우기 전에 호출된다.
     * 보드를 직접 바꿔도 되고(칸 파괴 등), 돌려준 행은 꽉 차 있지 않아도 완성 행과 함께 지워지며
     * 지워진 줄은 기존 줄 삭제 점수 규칙을 따른다.
     *
     * @param board 블록이 이미 기록된 보드
     * @param itemCell 아이템 문자가 붙어 있던 칸
     * @return 강제로 지울 행 번호. 없으면 빈 집합
     */
    Set<Integer> onLock(Board board, Cell itemCell);
}

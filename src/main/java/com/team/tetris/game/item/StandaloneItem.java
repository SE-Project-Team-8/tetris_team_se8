package com.team.tetris.game.item;

import com.team.tetris.block.Board;
import com.team.tetris.block.Cell;
import com.team.tetris.block.TetrominoType;

import java.util.List;

/** 블록 자체가 아이템인 형태. 예: 무게추. 낙하 중 조작 규칙은 {@link #spawn}이 만드는 {@link FallingPiece}가 가진다. */
public non-sealed interface StandaloneItem extends Item {
    /** 화면에서 색을 빌려 올 블록 종류. 아이템 전용 색이 없으므로 기존 팔레트(색맹 팔레트 포함)를 그대로 쓴다. */
    TetrominoType paletteType();

    /** 다음 블록 영역에 그릴 모양. 원점 기준 좌표이며 너비는 보드 안에 들어가야 한다. */
    List<Cell> previewShape();

    /** 위쪽 중앙에 생성한 낙하 조각. 생성 공간이 점유되어 있으면 null을 돌려주고 보드를 바꾸지 않는다. */
    FallingPiece spawn(Board board);
}

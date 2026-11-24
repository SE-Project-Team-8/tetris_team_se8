/*
 * 폭발형 아이템이 고정될 때 터진 범위. 엔진이 화면에 폭발 연출 알림을 보내는 데 쓴다 (이슈 #68).
 */
package com.team.tetris.game.item;

import com.team.tetris.block.Cell;

import java.util.List;
import java.util.Objects;

/**
 * 폭발 한 번. 지우기는 아이템이 이미 했고, 이 값은 어디서 얼마나 터졌는지를 알리기만 한다.
 *
 * @param center 폭발 중심 (아이템 칸)
 * @param cells  폭발 범위의 칸. 보드 안이며, 고정 순간 이미 꽉 찬 행은 빠진다. 빈 칸도 포함한다
 */
public record Blast(Cell center, List<Cell> cells) {
    public Blast {
        Objects.requireNonNull(center, "center");
        cells = List.copyOf(Objects.requireNonNull(cells, "cells"));
    }
}

package com.team.tetris.game.item;

/** 아이템 기회를 얻었을 때 어떤 아이템이 나올지 고르는 정책. 테스트는 특정 아이템을 고정해 넣을 수 있다. */
@FunctionalInterface
public interface ItemSelector {
    /** 다음 아이템. null을 돌려주지 않는다. */
    Item next();
}

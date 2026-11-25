/*
 * 대전 모드의 두 플레이어. 화면에서는 P1이 왼쪽 보드, P2가 오른쪽 보드다.
 */
package com.team.tetris.common.battle;

public enum Player {
    P1, P2;

    public Player opponent() {
        return this == P1 ? P2 : P1;
    }
}

package com.team.tetris.common.events;

import java.util.List;

public interface GameEventListener {

    /** One-way game-to-UI contract; changes require agreement between 담당자2 and B. */
    void onBoardUpdated(BoardSnapshot snapshot);

    default void onLandingUpdated(BoardSnapshot fallingPiece, BoardSnapshot landingPiece) { }

    default void onLinesCleared(List<Integer> rows) { }

    default void onLinesCleared(List<Integer> rows, BoardSnapshot boardBeforeClear) {
        onLinesCleared(rows);
    }

    void onScoreChanged(int score);

    void onNextBlocksChanged(int[] blockTypes);

    void onPauseStateChanged(boolean paused);

    default void onFeverActivated(int seconds) { }

    default void onSlowDownActivated(int previousLevel, int currentLevel) { }

    /**
     * 하드드롭으로 블록이 떨어졌다. 고정·줄 삭제 알림보다 먼저 온다. 잔상·착지 눌림 연출용 (design-spec §3.3).
     *
     * @param fallingBefore 떨어지기 전 낙하 블록의 칸만 담은 보드 (나머지는 EMPTY)
     * @param fallingAfter  떨어진 뒤(고정 직전) 낙하 블록의 칸만 담은 보드
     */
    default void onHardDropped(BoardSnapshot fallingBefore, BoardSnapshot fallingAfter) { }

    /**
     * 폭탄 아이템이 터졌다. 블록 고정 직후, 줄 삭제 알림보다 먼저 온다. 3×3 점선 사각형 연출용 (design-spec §4, 이슈 #68).
     *
     * @param center     폭발 중심 (폭탄 칸)
     * @param blastCells 폭발 범위의 칸. 보드 밖과 고정 순간 이미 꽉 찬 행은 빠지고, 빈 칸도 들어 있다
     */
    default void onBombExploded(CellPosition center, List<CellPosition> blastCells) { }

    /**
     * 무게추가 낙하하며 블록을 부쉈다. 한 칸 낙하(자동·소프트드롭)마다, 또는 하드드롭 한 번에 모아서 온다.
     * 연기 퍼프 연출용 (design-spec §4, 이슈 #68). 부순 칸이 없으면 오지 않는다.
     *
     * @param crushedCells 실제로 블록이 있다가 부서진 칸
     */
    default void onAnvilCrushed(List<CellPosition> crushedCells) { }

    void onGameOver(int score);
}
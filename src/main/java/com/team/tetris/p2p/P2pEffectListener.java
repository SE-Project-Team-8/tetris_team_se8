package com.team.tetris.p2p;

import com.team.tetris.common.battle.Player;
import com.team.tetris.common.events.BoardSnapshot;
import com.team.tetris.common.events.CellPosition;

import java.util.List;

/** Effects for either player's board, delivered on the Swing EDT. Snapshots are read-only. */
public interface P2pEffectListener {
    default void linesCleared(Player player, List<Integer> rows, BoardSnapshot boardBeforeClear) { }

    default void hardDropped(Player player, BoardSnapshot fallingBefore, BoardSnapshot fallingAfter) { }

    default void bombExploded(Player player, CellPosition center, List<CellPosition> blastCells) { }

    default void anvilCrushed(Player player, List<CellPosition> crushedCells) { }

    default void feverActivated(Player player, int seconds) { }

    default void slowDownActivated(Player player, int previousLevel, int currentLevel) { }
}

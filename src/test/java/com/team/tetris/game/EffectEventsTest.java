/*
 * 화면 연출 알림 테스트 (이슈 #68, 하드드롭): 폭탄이 터진 중심과 범위, 무게추가 부순 칸, 하드드롭의 출발·도착 위치,
 * 그리고 줄 삭제 알림과의 순서. 알림만 더했으므로 보드·점수 결과는 기존 테스트가 그대로 지킨다.
 */
package com.team.tetris.game;

import com.team.tetris.block.Board;
import com.team.tetris.block.Rotation;
import com.team.tetris.block.Tetromino;
import com.team.tetris.block.TetrominoType;
import com.team.tetris.common.Difficulty;
import com.team.tetris.common.events.BoardSnapshot;
import com.team.tetris.common.events.CellPosition;
import com.team.tetris.common.events.GameEventListener;
import com.team.tetris.game.item.AnvilItem;
import com.team.tetris.game.item.BombItem;
import com.team.tetris.game.item.Item;
import com.team.tetris.game.item.ItemPolicy;
import com.team.tetris.game.item.LineDeleteItem;
import org.junit.jupiter.api.Test;

import javax.swing.SwingUtilities;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

class EffectEventsTest {
    @Test
    void aBombReportsItsCentreAndRangeBeforeTheLineClear() throws Exception {
        Rig rig = new Rig(new BombItem());
        edt(() -> {
            rig.engine.start();
            place(rig.board, TetrominoType.I, 18, 0);   // row 19: columns 0-3
            place(rig.board, TetrominoType.I, 18, 6);   // row 19: columns 6-9, so the O completes it
            rig.engine.handle(GameAction.HARD_DROP);

            assertEquals(List.of("hardDropped", "bombExploded", "linesCleared"), rig.events.order);
            assertEquals(new CellPosition(18, 4), rig.events.bombCenter, "the bomb rides the O's top-left cell");
            assertEquals(List.of(
                    cell(16, 2), cell(16, 3), cell(16, 4), cell(16, 5), cell(16, 6),
                    cell(17, 2), cell(17, 3), cell(17, 4), cell(17, 5), cell(17, 6),
                    cell(18, 2), cell(18, 3), cell(18, 4), cell(18, 5), cell(18, 6)),
                    rig.events.blastCells, "5×5 clipped at the floor and excluding the already full row 19");
            rig.engine.close();
        });
    }

    @Test
    void piecesWithoutABombReportNoExplosion() throws Exception {
        for (Item item : new Item[]{null, new LineDeleteItem()}) {
            Rig rig = new Rig(item);
            edt(() -> {
                rig.engine.start();
                rig.engine.handle(GameAction.HARD_DROP);
                assertFalse(rig.events.order.contains("bombExploded"), String.valueOf(item));
                assertFalse(rig.events.order.contains("anvilCrushed"), String.valueOf(item));
                rig.engine.close();
            });
        }
    }

    @Test
    void anAnvilReportsTheCellsItCrushesStepByStep() throws Exception {
        Rig rig = new Rig(new AnvilItem());
        edt(() -> {
            rig.engine.start();   // the anvil occupies rows 0-1, columns 3-6
            place(rig.board, TetrominoType.O, 2, 3);   // rows 2-3, columns 3-4
            rig.engine.handle(GameAction.SOFT_DROP);
            assertEquals(List.of(List.of(cell(2, 3), cell(2, 4))), rig.events.crushed, "only the row it just reached");
            rig.engine.handle(GameAction.SOFT_DROP);
            assertEquals(List.of(cell(3, 3), cell(3, 4)), rig.events.crushed.get(1));
            rig.engine.handle(GameAction.SOFT_DROP);
            assertEquals(2, rig.events.crushed.size(), "nothing left to crush, so no event");
            rig.engine.close();
        });
    }

    @Test
    void anAnvilHardDropReportsEverythingItCrushedAtOnce() throws Exception {
        Rig rig = new Rig(new AnvilItem());
        edt(() -> {
            rig.engine.start();
            place(rig.board, TetrominoType.O, 6, 5);    // rows 6-7, columns 5-6
            place(rig.board, TetrominoType.O, 17, 3);   // rows 17-18, columns 3-4
            rig.engine.handle(GameAction.HARD_DROP);
            assertEquals(1, rig.events.crushed.size());
            assertEquals(List.of(cell(6, 5), cell(6, 6), cell(7, 5), cell(7, 6), cell(17, 3), cell(17, 4),
                    cell(18, 3), cell(18, 4)), sorted(rig.events.crushed.getFirst()));
            assertTrue(rig.events.order.indexOf("anvilCrushed") < rig.events.order.indexOf("hardDropped"));
            rig.engine.close();
        });
    }

    @Test
    void aHardDropReportsWhereThePieceFellFromAndTo() throws Exception {
        Rig rig = new Rig(null);
        edt(() -> {
            rig.engine.start();
            rig.engine.handle(GameAction.MOVE_LEFT);
            rig.engine.handle(GameAction.HARD_DROP);
            assertEquals(List.of(cell(0, 3), cell(0, 4), cell(1, 3), cell(1, 4)), occupied(rig.events.dropFrom));
            assertEquals(List.of(cell(18, 3), cell(18, 4), cell(19, 3), cell(19, 4)), occupied(rig.events.dropTo));
            assertEquals(List.of("hardDropped"), rig.events.order, "a soft move is not a hard drop");
            rig.engine.close();
        });
    }

    @Test
    void cellPositionsCannotBeNegative() {
        assertThrows(IllegalArgumentException.class, () -> new CellPosition(-1, 0));
        assertThrows(IllegalArgumentException.class, () -> new CellPosition(0, -1));
    }

    private static CellPosition cell(int row, int col) {
        return new CellPosition(row, col);
    }

    private static List<CellPosition> sorted(List<CellPosition> cells) {
        return cells.stream().sorted((a, b) -> a.row() != b.row() ? Integer.compare(a.row(), b.row())
                : Integer.compare(a.col(), b.col())).toList();
    }

    private static List<CellPosition> occupied(BoardSnapshot snapshot) {
        List<CellPosition> cells = new ArrayList<>();
        for (int row = 0; row < snapshot.rows(); row++) {
            for (int col = 0; col < snapshot.cols(); col++) {
                if (snapshot.cells()[row][col] != BoardSnapshot.EMPTY) cells.add(cell(row, col));
            }
        }
        return cells;
    }

    /** row+1 행(I) 또는 row·row+1 행(O)에 col부터 놓는다. */
    private static void place(Board board, TetrominoType type, int row, int col) {
        assertTrue(board.tryPlace(new Tetromino(type, Rotation.SPAWN, row, col)), type + " at " + row + "," + col);
    }

    private static void edt(Runnable action) throws Exception {
        try {
            SwingUtilities.invokeAndWait(action);
        } catch (InvocationTargetException failure) {
            if (failure.getCause() instanceof AssertionError assertion) throw assertion;
            if (failure.getCause() instanceof Exception exception) throw exception;
            throw failure;
        }
    }

    /** O만 나오고, item이 있으면 첫 블록부터 그 아이템인(포함형이면 왼쪽 위 칸) 아이템 모드 엔진. 자동 낙하는 없다. */
    private static final class Rig {
        final Board board = new Board();
        final Events events = new Events();
        final GameEngine engine;

        Rig(Item item) {
            ItemPolicy policy = item == null ? ItemPolicy.none() : new ItemPolicy() {
                @Override public void onLinesCleared(int lines) { }
                @Override public Optional<Item> nextItem() { return Optional.of(item); }
                @Override public void reset() { }
            };
            Random firstCell = new Random() {
                @Override public int nextInt(int bound) { return 0; }
            };
            BlockBoardDriver driver = new BlockBoardDriver(board, () -> TetrominoType.O, firstCell, policy);
            driver.setItemMode(true);
            engine = new GameEngine(driver, events, new ScoreCalculator(), new DefaultSpeedPolicy(),
                    new Idle(), new Idle(), () -> Difficulty.NORMAL);
        }
    }

    private static final class Idle implements GameEngine.DropTimer {
        @Override public void start(int intervalMillis, Runnable tick) { }
        @Override public void stop() { }
    }

    private static final class Events implements GameEventListener {
        final List<String> order = new ArrayList<>();
        final List<List<CellPosition>> crushed = new ArrayList<>();
        CellPosition bombCenter;
        List<CellPosition> blastCells;
        BoardSnapshot dropFrom;
        BoardSnapshot dropTo;

        @Override public void onHardDropped(BoardSnapshot fallingBefore, BoardSnapshot fallingAfter) {
            order.add("hardDropped");
            dropFrom = fallingBefore;
            dropTo = fallingAfter;
        }

        @Override public void onBombExploded(CellPosition center, List<CellPosition> cells) {
            order.add("bombExploded");
            bombCenter = center;
            blastCells = cells;
        }

        @Override public void onAnvilCrushed(List<CellPosition> cells) {
            order.add("anvilCrushed");
            crushed.add(cells);
        }

        @Override public void onLinesCleared(List<Integer> rows, BoardSnapshot boardBeforeClear) {
            order.add("linesCleared");
        }

        @Override public void onBoardUpdated(BoardSnapshot snapshot) { }
        @Override public void onScoreChanged(int score) { }
        @Override public void onNextBlocksChanged(int[] blockTypes) { }
        @Override public void onPauseStateChanged(boolean paused) { }
        @Override public void onGameOver(int score) { }
    }
}

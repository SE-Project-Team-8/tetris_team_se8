/*
 * GameEngine과 AttackLink 테스트: 2줄 이상 지우면 보냄, 블록을 고정할 때마다 받은 줄을 다음 블록 생성 전에 넣음,
 * 낙하 중에는 넣지 않음, 자기 줄 삭제가 먼저, 넘치거나 생성 자리가 막히면 게임 종료 (rules.md 2·4·5절).
 */
package com.team.tetris.game;

import com.team.tetris.block.Board;
import com.team.tetris.block.Rotation;
import com.team.tetris.block.Tetromino;
import com.team.tetris.block.TetrominoType;
import com.team.tetris.common.Difficulty;
import com.team.tetris.common.battle.GarbageLine;
import com.team.tetris.common.events.BoardSnapshot;
import com.team.tetris.common.events.CellCodes;
import com.team.tetris.common.events.GameEventListener;
import org.junit.jupiter.api.Test;

import javax.swing.SwingUtilities;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class VersusEngineTest {
    /** 4·5열만 빈 줄. 가운데에 떨어지는 O가 채운다. */
    private static final GarbageLine HOLES_AT_4_5 = new GarbageLine(0b1111001111);
    /** 9열만 빈 줄. */
    private static final GarbageLine HOLE_AT_9 = new GarbageLine(0b0111111111);

    @Test
    void clearingTwoLinesSendsThemOnce() throws Exception {
        Fixture f = new Fixture();
        edt(() -> {
            f.engine.start();
            fillAroundTheMiddle(f.board, 18);
            fillAroundTheMiddle(f.board, 19);
            assertTrue(f.engine.handle(GameAction.HARD_DROP));
            assertEquals(List.of(List.of(HOLES_AT_4_5, HOLES_AT_4_5)), f.link.sent);
        });
    }

    @Test
    void clearingOneLineSendsNothing() throws Exception {
        Fixture f = new Fixture();
        edt(() -> {
            f.engine.start();
            fillAroundTheMiddle(f.board, 19);
            f.engine.handle(GameAction.HARD_DROP);
            assertEquals(1, f.engine.getClearedLines());
            assertEquals(List.of(), f.link.sent);
        });
    }

    @Test
    void waitingLinesEnterOnlyAfterTheFallingPieceLocks() throws Exception {
        Fixture f = new Fixture();
        edt(() -> {
            f.engine.start();
            f.link.incoming.add(HOLE_AT_9);
            assertTrue(f.engine.handle(GameAction.MOVE_LEFT));
            assertTrue(f.engine.handle(GameAction.SOFT_DROP));
            assertEquals(0, f.link.takes, "nothing is taken while the piece is still falling");
            assertFalse(CellCodes.isGarbage(f.events.board.cells()[19][0]));

            f.engine.handle(GameAction.HARD_DROP);
            assertEquals(1, f.link.takes);
            int[][] cells = f.events.board.cells();
            assertTrue(CellCodes.isGarbage(cells[19][0]), "the received line is the new bottom row");
            assertEquals(BoardSnapshot.EMPTY, cells[19][9]);
            assertEquals(TetrominoType.O.id(), cells[17][3], "the locked O moved up by one row");
            assertEquals(TetrominoType.O.id(), cells[0][4], "the next piece spawned after the insertion");
            assertTrue(f.link.incoming.isEmpty());
        });
    }

    @Test
    void everyLockTakesTheWaitingLines() throws Exception {
        Fixture f = new Fixture();
        edt(() -> {
            f.engine.start();
            f.engine.handle(GameAction.HARD_DROP);
            f.engine.handle(GameAction.HARD_DROP);
            assertEquals(2, f.link.takes);
            assertEquals(GameState.RUNNING, f.engine.getState());
        });
    }

    @Test
    void thePiecesOwnClearHappensBeforeReceivedLinesEnter() throws Exception {
        Fixture f = new Fixture();
        edt(() -> {
            f.engine.start();
            fillAroundTheMiddle(f.board, 18);
            fillAroundTheMiddle(f.board, 19);
            f.link.incoming.add(HOLE_AT_9);
            f.engine.handle(GameAction.HARD_DROP);
            assertEquals(2, f.engine.getClearedLines(), "the received line did not get in the way of the clear");
            int[][] cells = f.events.board.cells();
            assertTrue(CellCodes.isGarbage(cells[19][0]));
            assertEquals(BoardSnapshot.EMPTY, cells[18][0], "only the received line is left on the floor");
        });
    }

    @Test
    void blocksPushedAboveTheBoardEndTheGame() throws Exception {
        Fixture f = new Fixture();
        edt(() -> {
            f.engine.start();
            place(f.board, TetrominoType.O, 0, 0);      // rows 0-1, away from the falling piece
            f.link.incoming.add(HOLE_AT_9);
            f.engine.handle(GameAction.HARD_DROP);
            assertEquals(GameState.GAME_OVER, f.engine.getState(), "rules.md 5: pushed out means defeat (D-03)");
            assertEquals(1, f.events.gameOvers);
        });
    }

    @Test
    void aBlockedSpawnAfterInsertionEndsTheGame() throws Exception {
        Fixture f = new Fixture();
        edt(() -> {
            f.engine.start();
            for (int i = 0; i < 4; i++) f.engine.handle(GameAction.MOVE_LEFT);
            place(f.board, TetrominoType.O, 2, 4);      // rows 2-3 under the spawn area
            f.link.incoming.add(HOLE_AT_9);
            f.link.incoming.add(HOLE_AT_9);
            f.engine.handle(GameAction.HARD_DROP);
            assertEquals(GameState.GAME_OVER, f.engine.getState(), "the O rose into the spawn area");
            assertEquals(TetrominoType.O.id(), f.events.board.cells()[0][4], "nothing was pushed out");
        });
    }

    @Test
    void anAttackIsSentEvenWhenTheSenderThenLoses() throws Exception {
        Fixture f = new Fixture();
        edt(() -> {
            f.engine.start();
            fillAroundTheMiddle(f.board, 18);
            fillAroundTheMiddle(f.board, 19);
            place(f.board, TetrominoType.O, 0, 0);
            for (int i = 0; i < 3; i++) f.link.incoming.add(HOLE_AT_9);
            f.engine.handle(GameAction.HARD_DROP);
            assertEquals(1, f.link.sent.size());
            assertEquals(GameState.GAME_OVER, f.engine.getState());
        });
    }

    @Test
    void anAttackLinkIsRequired() {
        assertThrows(NullPointerException.class, () -> new GameEngine(new BlockBoardDriver(), new Events(),
                () -> Difficulty.NORMAL, null));
    }

    /** row 행의 0~3열과 6~9열을 채운다. 가운데 4·5열은 비운다. */
    private static void fillAroundTheMiddle(Board board, int row) {
        place(board, TetrominoType.I, row - 1, 0);
        place(board, TetrominoType.I, row - 1, 6);
    }

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

    /** O만 나오는 보드와, 손으로 움직이는 타이머를 쓰는 엔진. */
    private static final class Fixture {
        final Board board = new Board();
        final Link link = new Link();
        final Events events = new Events();
        final GameEngine engine = new GameEngine(new BlockBoardDriver(board, () -> TetrominoType.O), events,
                new ScoreCalculator(), new DefaultSpeedPolicy(), new IdleTimer(), new IdleTimer(),
                () -> Difficulty.NORMAL, link);
    }

    private static final class Link implements GameEngine.AttackLink {
        final List<List<GarbageLine>> sent = new ArrayList<>();
        final List<GarbageLine> incoming = new ArrayList<>();
        int takes;

        @Override public void send(List<GarbageLine> lines) { sent.add(List.copyOf(lines)); }

        @Override public List<GarbageLine> takeIncoming() {
            takes++;
            List<GarbageLine> taken = List.copyOf(incoming);
            incoming.clear();
            return taken;
        }
    }

    /** 자동 낙하가 끼어들지 않도록 아무것도 하지 않는 타이머. */
    private static final class IdleTimer implements GameEngine.DropTimer {
        @Override public void start(int intervalMillis, Runnable tick) { }
        @Override public void stop() { }
    }

    private static final class Events implements GameEventListener {
        BoardSnapshot board;
        int gameOvers;
        @Override public void onBoardUpdated(BoardSnapshot snapshot) { board = snapshot; }
        @Override public void onScoreChanged(int score) { }
        @Override public void onNextBlocksChanged(int[] blockTypes) { }
        @Override public void onPauseStateChanged(boolean paused) { }
        @Override public void onGameOver(int score) { gameOvers++; }
    }
}

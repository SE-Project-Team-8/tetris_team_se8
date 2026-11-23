/*
 * VersusMatch 테스트: 두 엔진이 따로 진행됨, 공격이 상대 대기열로 가고 상대가 블록을 고정할 때 들어감,
 * 먼저 못 쌓은 쪽의 패배, 일시정지·중단·다시 시작, 화면으로 가는 알림 (rules.md 1~5절).
 */
package com.team.tetris.game.versus;

import com.team.tetris.block.Board;
import com.team.tetris.block.Rotation;
import com.team.tetris.block.Tetromino;
import com.team.tetris.block.TetrominoType;
import com.team.tetris.common.Difficulty;
import com.team.tetris.common.battle.GarbageLine;
import com.team.tetris.common.battle.Player;
import com.team.tetris.common.battle.VersusMode;
import com.team.tetris.common.events.BoardSnapshot;
import com.team.tetris.common.events.CellCodes;
import com.team.tetris.common.events.GameEventListener;
import com.team.tetris.game.BlockBoardDriver;
import com.team.tetris.game.DefaultSpeedPolicy;
import com.team.tetris.game.GameAction;
import com.team.tetris.game.GameEngine;
import com.team.tetris.game.ScoreCalculator;
import com.team.tetris.game.item.ItemRegistry;
import org.junit.jupiter.api.Test;

import javax.swing.SwingUtilities;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class VersusMatchTest {
    /** 4·5열만 빈 줄. 가운데에 떨어지는 O가 지우고 남긴 모양. */
    private static final GarbageLine HOLES_AT_4_5 = new GarbageLine(0b1111001111);

    @Test
    void startingRunsBothPlayersAndAnnouncesEmptyQueuesAndLevels() throws Exception {
        Fixture f = new Fixture();
        edt(() -> {
            f.match.start(VersusMode.NORMAL);
            assertEquals(VersusMatch.State.RUNNING, f.match.state());
            assertEquals(VersusMode.NORMAL, f.match.mode());
            assertEquals(List.of(), f.events.queues.get(Player.P1));
            assertEquals(List.of(), f.events.queues.get(Player.P2));
            assertEquals(1, f.events.levels.get(Player.P1));
            assertEquals(1, f.events.levels.get(Player.P2));
            assertNotNull(f.views.get(Player.P1).board, "each player's board reaches its own view");
            assertNotNull(f.views.get(Player.P2).board);
        });
    }

    @Test
    void onePlayersMoveDoesNotTouchTheOtherBoard() throws Exception {
        Fixture f = new Fixture();
        edt(() -> {
            f.match.start(VersusMode.NORMAL);
            BoardSnapshot p2Before = f.views.get(Player.P2).board;
            assertTrue(f.match.handle(Player.P1, GameAction.MOVE_LEFT));
            assertEquals(TetrominoType.O.id(), f.views.get(Player.P1).board.cells()[0][3]);
            assertSame(p2Before, f.views.get(Player.P2).board, "P2 received no board update");
        });
    }

    @Test
    void anAttackGoesToTheOpponentsQueueAndEntersOnTheirNextLock() throws Exception {
        Fixture f = new Fixture();
        edt(() -> {
            f.match.start(VersusMode.NORMAL);
            fillAroundTheMiddle(f.boards.get(Player.P1), 18);
            fillAroundTheMiddle(f.boards.get(Player.P1), 19);

            f.match.handle(Player.P1, GameAction.HARD_DROP);
            assertEquals(List.of(HOLES_AT_4_5, HOLES_AT_4_5), f.match.queue(Player.P2));
            assertEquals(List.of(HOLES_AT_4_5, HOLES_AT_4_5), f.events.queues.get(Player.P2));
            assertEquals(List.of(), f.match.queue(Player.P1), "the attacker's own queue is untouched");
            assertFalse(CellCodes.isGarbage(f.views.get(Player.P2).board.cells()[19][0]),
                    "nothing enters while P2's piece is still falling");

            f.match.handle(Player.P2, GameAction.HARD_DROP);
            assertEquals(List.of(), f.match.queue(Player.P2));
            assertEquals(List.of(), f.events.queues.get(Player.P2), "the screen hears that the queue emptied");
            int[][] p2 = f.views.get(Player.P2).board.cells();
            assertTrue(CellCodes.isGarbage(p2[18][0]));
            assertTrue(CellCodes.isGarbage(p2[19][0]));
        });
    }

    @Test
    void theFirstPlayerWhoCannotStackLoses() throws Exception {
        Fixture f = new Fixture();
        edt(() -> {
            f.match.start(VersusMode.NORMAL);
            place(f.boards.get(Player.P1), TetrominoType.O, 2, 4);   // right under P1's spawn area
            f.match.handle(Player.P2, GameAction.HARD_DROP);
            int p2Score = f.match.score(Player.P2);

            f.match.handle(Player.P1, GameAction.HARD_DROP);
            assertEquals(VersusMatch.State.ENDED, f.match.state());
            assertEquals(List.of(new MatchResult(Player.P2, MatchResult.Reason.TOP_OUT,
                    f.match.score(Player.P1), p2Score)), f.events.results);
            assertEquals(Player.P2, f.events.results.getFirst().winnerIfAny().orElseThrow());
            assertFalse(f.match.handle(Player.P2, GameAction.MOVE_LEFT), "the winner's board stopped too");
            assertEquals(1, f.events.results.size());
        });
    }

    @Test
    void pausingStopsBothBoardsUntilResumed() throws Exception {
        Fixture f = new Fixture();
        edt(() -> {
            f.match.start(VersusMode.NORMAL);
            assertTrue(f.match.pause());
            assertEquals(VersusMatch.State.PAUSED, f.match.state());
            assertEquals(List.of(true), f.events.pauses);
            assertFalse(f.match.handle(Player.P1, GameAction.MOVE_LEFT));
            assertFalse(f.match.handle(Player.P2, GameAction.MOVE_LEFT));
            assertFalse(f.match.pause());

            assertTrue(f.match.resume());
            assertEquals(List.of(true, false), f.events.pauses);
            assertTrue(f.match.handle(Player.P1, GameAction.MOVE_LEFT));
            assertFalse(f.match.resume());
        });
    }

    @Test
    void stoppingEndsWithoutAResult() throws Exception {
        Fixture f = new Fixture();
        edt(() -> {
            f.match.start(VersusMode.NORMAL);
            f.match.pause();
            assertTrue(f.match.stop());
            assertEquals(VersusMatch.State.READY, f.match.state());
            assertEquals(List.of(), f.events.results);
            assertEquals(List.of(true, false), f.events.pauses, "a paused match is no longer paused");
            assertFalse(f.match.handle(Player.P1, GameAction.MOVE_LEFT));
            assertFalse(f.match.stop());
        });
    }

    @Test
    void startingAgainClearsTheBoardsQueuesAndScores() throws Exception {
        Fixture f = new Fixture();
        edt(() -> {
            f.match.start(VersusMode.NORMAL);
            fillAroundTheMiddle(f.boards.get(Player.P1), 18);
            fillAroundTheMiddle(f.boards.get(Player.P1), 19);
            f.match.handle(Player.P1, GameAction.HARD_DROP);
            assertFalse(f.match.queue(Player.P2).isEmpty());

            f.match.start(VersusMode.NORMAL);
            assertEquals(VersusMatch.State.RUNNING, f.match.state());
            assertEquals(List.of(), f.match.queue(Player.P2));
            assertEquals(0, f.match.score(Player.P1));
            assertEquals(1, f.match.level(Player.P1));
        });
    }

    @Test
    void aFinishedMatchCanBeStartedAgain() throws Exception {
        Fixture f = new Fixture();
        edt(() -> {
            f.match.start(VersusMode.NORMAL);
            place(f.boards.get(Player.P1), TetrominoType.O, 2, 4);
            f.match.handle(Player.P1, GameAction.HARD_DROP);
            assertEquals(VersusMatch.State.ENDED, f.match.state());

            f.match.start(VersusMode.NORMAL);
            assertEquals(VersusMatch.State.RUNNING, f.match.state());
            assertTrue(f.match.handle(Player.P1, GameAction.MOVE_LEFT));
            assertTrue(f.match.handle(Player.P2, GameAction.MOVE_LEFT));
        });
    }

    @Test
    void pauseAndQuitAreNotPlayerMoves() throws Exception {
        Fixture f = new Fixture();
        edt(() -> {
            f.match.start(VersusMode.NORMAL);
            assertThrows(IllegalArgumentException.class, () -> f.match.handle(Player.P1, GameAction.PAUSE));
            assertThrows(IllegalArgumentException.class, () -> f.match.handle(Player.P2, GameAction.QUIT));
        });
    }

    @Test
    void movesBeforeTheFirstStartAreIgnored() throws Exception {
        Fixture f = new Fixture();
        edt(() -> {
            assertEquals(VersusMatch.State.READY, f.match.state());
            assertFalse(f.match.handle(Player.P1, GameAction.MOVE_LEFT));
            assertFalse(f.match.pause());
        });
    }

    @Test
    void theStandardSetupBuildsRealEngines() throws Exception {
        edt(() -> {
            VersusMatch match = new VersusMatch(VersusMatch.standard(ItemRegistry.defaults(), () -> Difficulty.NORMAL));
            try {
                match.start(VersusMode.NORMAL);
                assertEquals(VersusMatch.State.RUNNING, match.state());
            } finally {
                match.close();
            }
            assertEquals(VersusMatch.State.READY, match.state());
        });
    }

    @Test
    void resultsKnowTheirWinnerAndScores() {
        MatchResult result = new MatchResult(Player.P1, MatchResult.Reason.TOP_OUT, 300, 120);
        assertFalse(result.isDraw());
        assertEquals(300, result.scoreOf(Player.P1));
        assertEquals(120, result.scoreOf(Player.P2));
        assertThrows(IllegalArgumentException.class,
                () -> new MatchResult(null, MatchResult.Reason.TOP_OUT, 0, 0), "a top-out always has a winner");
        assertThrows(NullPointerException.class, () -> new MatchResult(Player.P1, null, 0, 0));
    }

    /** row 행의 0~3열과 6~9열을 채운다. 가운데 4·5열은 비운다. */
    static void fillAroundTheMiddle(Board board, int row) {
        place(board, TetrominoType.I, row - 1, 0);
        place(board, TetrominoType.I, row - 1, 6);
    }

    static void place(Board board, TetrominoType type, int row, int col) {
        assertTrue(board.tryPlace(new Tetromino(type, Rotation.SPAWN, row, col)), type + " at " + row + "," + col);
    }

    static void edt(Runnable action) throws Exception {
        try {
            SwingUtilities.invokeAndWait(action);
        } catch (InvocationTargetException failure) {
            if (failure.getCause() instanceof AssertionError assertion) throw assertion;
            if (failure.getCause() instanceof Exception exception) throw exception;
            throw failure;
        }
    }

    /** 플레이어마다 O만 나오는 보드와, 자동 낙하가 끼어들지 않는 엔진으로 만든 대전. */
    static final class Fixture {
        final Map<Player, Board> boards = new EnumMap<>(Player.class);
        final Map<Player, View> views = new EnumMap<>(Player.class);
        final Events events = new Events();
        final VersusMatch match;

        Fixture() {
            match = new VersusMatch(new VersusMatch.PlayerSetup() {
                @Override public BlockBoardDriver driver(Player player) {
                    Board board = new Board();
                    boards.put(player, board);
                    return new BlockBoardDriver(board, () -> TetrominoType.O);
                }

                @Override public GameEngine engine(Player player, BlockBoardDriver driver,
                                                   GameEventListener listener, GameEngine.AttackLink link) {
                    return new GameEngine(driver, listener, new ScoreCalculator(), new DefaultSpeedPolicy(),
                            new IdleTimer(), new IdleTimer(), () -> Difficulty.NORMAL, link);
                }
            });
            match.setListener(events);
            for (Player player : Player.values()) {
                View view = new View();
                views.put(player, view);
                match.setPlayerListener(player, view);
            }
        }
    }

    static final class IdleTimer implements GameEngine.DropTimer {
        @Override public void start(int intervalMillis, Runnable tick) { }
        @Override public void stop() { }
    }

    static final class Events implements VersusListener {
        final Map<Player, List<GarbageLine>> queues = new EnumMap<>(Player.class);
        final Map<Player, Integer> levels = new EnumMap<>(Player.class);
        final List<Boolean> pauses = new ArrayList<>();
        final List<Integer> times = new ArrayList<>();
        final List<MatchResult> results = new ArrayList<>();

        @Override public void onQueueChanged(Player player, List<GarbageLine> lines) { queues.put(player, lines); }
        @Override public void onLevelChanged(Player player, int level) { levels.put(player, level); }
        @Override public void onTimeChanged(int remainingSeconds) { times.add(remainingSeconds); }
        @Override public void onPauseChanged(boolean paused) { pauses.add(paused); }
        @Override public void onMatchEnded(MatchResult result) { results.add(result); }
    }

    static final class View implements GameEventListener {
        BoardSnapshot board;
        @Override public void onBoardUpdated(BoardSnapshot snapshot) { board = snapshot; }
        @Override public void onScoreChanged(int score) { }
        @Override public void onNextBlocksChanged(int[] blockTypes) { }
        @Override public void onPauseStateChanged(boolean paused) { }
        @Override public void onGameOver(int score) { }
    }
}

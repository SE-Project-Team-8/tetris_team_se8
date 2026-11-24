/*
 * VersusMatch 테스트: 두 엔진이 따로 진행됨, 공격이 상대 대기열로 가고 상대가 블록을 고정할 때 들어감,
 * 먼저 못 쌓은 쪽의 패배, 일시정지·중단·다시 시작, 화면으로 가는 알림, 시간제한 방식 (rules.md 1~6절).
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
import com.team.tetris.common.constants.GameConstants;
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
import java.util.function.Function;

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
    void eachPlayerLevelsUpEveryTenOwnClearedLinesLikeTheSinglePlayerGame() throws Exception {
        Fixture f = new Fixture();
        edt(() -> {
            f.match.start(VersusMode.NORMAL);
            for (int round = 0; round < 4; round++) {
                fillAroundTheMiddle(f.boards.get(Player.P1), 18);
                fillAroundTheMiddle(f.boards.get(Player.P1), 19);
                f.match.handle(Player.P1, GameAction.HARD_DROP);
            }
            assertEquals(1, f.match.level(Player.P1), "8 lines");
            fillAroundTheMiddle(f.boards.get(Player.P1), 18);
            fillAroundTheMiddle(f.boards.get(Player.P1), 19);
            f.match.handle(Player.P1, GameAction.HARD_DROP);
            assertEquals(2, f.match.level(Player.P1), "10 lines: DefaultSpeedPolicy, the same rule as one player");
            assertEquals(2, f.events.levels.get(Player.P1), "the screen hears the new level");
            assertEquals(1, f.match.level(Player.P2), "the other player's lines do not count");
            assertEquals(1, f.events.levels.get(Player.P2));
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

    // ---- 시간제한 방식 (rules.md 6절, D-10)

    @Test
    void theTimeLimitCountsDownEverySecond() throws Exception {
        Fixture f = new Fixture();
        edt(() -> {
            f.match.start(VersusMode.TIME_LIMIT);
            int limit = GameConstants.VERSUS_TIME_LIMIT_SECONDS;
            assertEquals(180, limit, "three minutes (A8)");
            assertEquals(limit, f.match.remainingSeconds());
            assertEquals(List.of(limit), f.events.times, "the screen hears the full time at the start");
            assertTrue(f.clock.running);
            assertEquals(1000, f.clock.intervalMillis);
            assertEquals(1000, f.clock.initialDelayMillis);

            f.clock.seconds(3);
            assertEquals(limit - 3, f.match.remainingSeconds());
            assertEquals(List.of(limit, limit - 1, limit - 2, limit - 3), f.events.times);
            assertEquals(VersusMatch.State.RUNNING, f.match.state());
        });
    }

    @Test
    void whenTimeRunsOutTheHigherScoreWins() throws Exception {
        Fixture f = new Fixture();
        edt(() -> {
            f.match.start(VersusMode.TIME_LIMIT);
            f.match.handle(Player.P2, GameAction.HARD_DROP);
            int p2Score = f.match.score(Player.P2);
            assertTrue(p2Score > 0, "a hard drop scores");

            f.clock.seconds(GameConstants.VERSUS_TIME_LIMIT_SECONDS - 1);
            assertEquals(List.of(), f.events.results, "one second is still left");
            f.clock.seconds(1);
            assertEquals(List.of(new MatchResult(Player.P2, MatchResult.Reason.TIME_UP, 0, p2Score)),
                    f.events.results);
            assertEquals(0, f.match.remainingSeconds());
            assertEquals(VersusMatch.State.ENDED, f.match.state());
            assertFalse(f.clock.running);
            assertFalse(f.match.handle(Player.P1, GameAction.MOVE_LEFT), "both boards stopped");
            assertFalse(f.match.handle(Player.P2, GameAction.MOVE_LEFT));
        });
    }

    @Test
    void equalScoresWhenTimeRunsOutAreADraw() throws Exception {
        Fixture f = new Fixture();
        edt(() -> {
            f.match.start(VersusMode.TIME_LIMIT);
            f.clock.seconds(GameConstants.VERSUS_TIME_LIMIT_SECONDS);
            MatchResult result = f.events.results.getFirst();
            assertTrue(result.isDraw(), "nobody scored more (A9)");
            assertEquals(MatchResult.Reason.TIME_UP, result.reason());
            assertEquals(1, f.events.results.size());
        });
    }

    @Test
    void theClockStopsWhilePausedAndKeepsThePartOfASecondAlreadyPlayed() throws Exception {
        Fixture f = new Fixture();
        edt(() -> {
            f.match.start(VersusMode.TIME_LIMIT);
            f.clock.seconds(10);
            f.clock.elapsedMillis = 400;
            f.match.pause();
            assertFalse(f.clock.running, "paused time does not count (A10)");
            f.clock.tick.run();   // a tick that was already on its way
            assertEquals(170, f.match.remainingSeconds(), "a late tick is ignored while paused");

            f.match.resume();
            assertTrue(f.clock.running);
            assertEquals(600, f.clock.initialDelayMillis, "only the rest of the cut second is left");
            assertEquals(1000, f.clock.intervalMillis);
            f.clock.seconds(1);
            assertEquals(169, f.match.remainingSeconds());
        });
    }

    @Test
    void aPlayerWhoCannotStackLosesBeforeTheTimeIsUp() throws Exception {
        Fixture f = new Fixture();
        edt(() -> {
            f.match.start(VersusMode.TIME_LIMIT);
            f.clock.seconds(5);
            place(f.boards.get(Player.P2), TetrominoType.O, 2, 4);
            f.match.handle(Player.P2, GameAction.HARD_DROP);
            assertEquals(Player.P1, f.events.results.getFirst().winner());
            assertEquals(MatchResult.Reason.TOP_OUT, f.events.results.getFirst().reason());
            assertFalse(f.clock.running, "the clock stops with the match");
            f.clock.tick.run();
            assertEquals(1, f.events.results.size(), "a late tick does not end the match again");
        });
    }

    @Test
    void stoppingOrStartingAgainResetsTheClock() throws Exception {
        Fixture f = new Fixture();
        edt(() -> {
            f.match.start(VersusMode.TIME_LIMIT);
            f.clock.seconds(30);
            assertTrue(f.match.stop());
            assertFalse(f.clock.running);

            f.match.start(VersusMode.TIME_LIMIT);
            assertEquals(GameConstants.VERSUS_TIME_LIMIT_SECONDS, f.match.remainingSeconds());
            assertTrue(f.clock.running);
            f.clock.seconds(5);

            f.match.start(VersusMode.NORMAL);
            assertFalse(f.clock.running, "a normal match has no clock");
            assertEquals(0, f.match.remainingSeconds());
        });
    }

    @Test
    void normalAndItemMatchesHaveNoTimeLimit() throws Exception {
        for (VersusMode mode : List.of(VersusMode.NORMAL, VersusMode.ITEM)) {
            Fixture f = new Fixture();
            edt(() -> {
                f.match.start(mode);
                assertFalse(f.clock.running, mode + " starts no clock");
                assertEquals(0, f.match.remainingSeconds());
                f.match.pause();
                f.match.resume();
                assertFalse(f.clock.running, mode + " resumes no clock");
                assertEquals(List.of(), f.events.times, mode + " announces no time");
            });
        }
    }

    @Test
    void theStandardSetupRunsATimeLimitedMatchOnARealTimer() throws Exception {
        edt(() -> {
            VersusMatch match = new VersusMatch(VersusMatch.standard(ItemRegistry.defaults(), () -> Difficulty.NORMAL));
            try {
                match.start(VersusMode.TIME_LIMIT);
                assertEquals(GameConstants.VERSUS_TIME_LIMIT_SECONDS, match.remainingSeconds());
                assertTrue(match.pause());
                assertTrue(match.resume());
            } finally {
                match.close();
            }
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

    /**
     * 플레이어마다 O만 나오는 보드와, 자동 낙하가 끼어들지 않는 엔진으로 만든 대전.
     * 제한 시간은 테스트가 직접 넘기는 {@link ManualClock}으로 잰다.
     */
    static final class Fixture {
        final Map<Player, Board> boards = new EnumMap<>(Player.class);
        final Map<Player, View> views = new EnumMap<>(Player.class);
        final Events events = new Events();
        final ManualClock clock = new ManualClock();
        final VersusMatch match;

        Fixture() {
            this(board -> new BlockBoardDriver(board, () -> TetrominoType.O));
        }

        /** @param drivers 플레이어의 보드로 드라이버를 만든다. 아이템 테스트가 아이템 규칙을 바꿔 끼운다. */
        Fixture(Function<Board, BlockBoardDriver> drivers) {
            match = new VersusMatch(new VersusMatch.PlayerSetup() {
                @Override public BlockBoardDriver driver(Player player) {
                    Board board = new Board();
                    boards.put(player, board);
                    return drivers.apply(board);
                }

                @Override public GameEngine engine(Player player, BlockBoardDriver driver,
                                                   GameEventListener listener, GameEngine.AttackLink link) {
                    return new GameEngine(driver, listener, new ScoreCalculator(), new DefaultSpeedPolicy(),
                            new IdleTimer(), new IdleTimer(), () -> Difficulty.NORMAL, link);
                }
            }, clock);
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

    /** 테스트가 1초씩 넘기는 타이머. 마지막으로 받은 일정과 동작 여부를 기억한다. */
    static final class ManualClock implements GameEngine.DropTimer {
        Runnable tick = () -> { };
        int initialDelayMillis;
        int intervalMillis;
        int elapsedMillis;
        boolean running;

        @Override public void start(int intervalMillis, Runnable tick) {
            start(intervalMillis, intervalMillis, tick);
        }

        @Override public void start(int initialDelayMillis, int intervalMillis, Runnable tick) {
            this.initialDelayMillis = initialDelayMillis;
            this.intervalMillis = intervalMillis;
            this.tick = tick;
            running = true;
        }

        @Override public void stop() { running = false; }

        @Override public int elapsedMillis() { return elapsedMillis; }

        /** 타이머가 도는 동안 count초를 흘려보낸다. 멈추면 그 뒤의 초는 흐르지 않는다. */
        void seconds(int count) {
            for (int i = 0; i < count && running; i++) tick.run();
        }
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
        int[] next = new int[0];
        @Override public void onBoardUpdated(BoardSnapshot snapshot) { board = snapshot; }
        @Override public void onScoreChanged(int score) { }
        @Override public void onNextBlocksChanged(int[] blockTypes) { next = blockTypes.clone(); }
        @Override public void onPauseStateChanged(boolean paused) { }
        @Override public void onGameOver(int score) { }
    }
}

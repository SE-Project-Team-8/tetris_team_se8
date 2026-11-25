/*
 * 로컬 대전 한 판의 진행: 플레이어마다 엔진·드라이버·공격 대기열 한 벌, 공격 전달, 승패, 일시정지,
 * 방식별 처리(아이템 방식의 아이템 등장, 시간제한 방식의 제한 시간).
 * 화면은 이 클래스만 상대한다 (docs/battle/ui-contract.md).
 */
package com.team.tetris.game.versus;

import com.team.tetris.common.Difficulty;
import com.team.tetris.common.battle.GarbageLine;
import com.team.tetris.common.battle.Player;
import com.team.tetris.common.battle.VersusMode;
import com.team.tetris.common.constants.GameConstants;
import com.team.tetris.common.events.BoardSnapshot;
import com.team.tetris.common.events.CellPosition;
import com.team.tetris.common.events.GameEventListener;
import com.team.tetris.game.BlockBoardDriver;
import com.team.tetris.game.GameAction;
import com.team.tetris.game.GameEngine;
import com.team.tetris.game.GameState;
import com.team.tetris.game.item.ItemRegistry;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * 로컬 2인 대전 한 판 (3차 요구사항 p.3~6, docs/battle/rules.md).
 *
 * 플레이어마다 {@link BlockBoardDriver}·{@link GameEngine}·{@link GarbageQueue}를 한 벌씩 둔다. 한 플레이어가
 * 2줄 이상 지우면 그 줄이 상대의 대기열에 들어가고, 상대가 블록을 고정할 때 상대 보드에 들어간다. 먼저 더 쌓을
 * 수 없게 된 쪽이 진다. 모든 메서드는 Swing EDT에서 호출한다(엔진과 같은 계약).
 *
 * 방식은 {@link #start(VersusMode)}에서 고른다. {@link VersusMode#ITEM}이면 두 드라이버의 아이템 등장 규칙을 켜고
 * (플레이어마다 누적 줄 수를 따로 센다), {@link VersusMode#TIME_LIMIT}이면 제한 시간을 1초씩 줄여 0이 될 때 점수가
 * 높은 쪽을 승자로 정한다(같으면 무승부). 제한 시간은 일시정지 중에 멈춘다 (rules.md 6·7절, D-10).
 */
public final class VersusMatch implements AutoCloseable {
    /** 대전의 진행 상태. */
    public enum State { READY, RUNNING, PAUSED, ENDED }

    private static final int SECOND_MILLIS = 1000;

    /** 플레이어 한 명의 드라이버와 엔진을 만든다. 테스트는 블록 순서와 타이머를 바꿔 끼운다. */
    public interface PlayerSetup {
        BlockBoardDriver driver(Player player);

        GameEngine engine(Player player, BlockBoardDriver driver, GameEventListener listener,
                          GameEngine.AttackLink link);
    }

    /** 실제 게임의 구성: 등록된 아이템과 설정의 난이도를 두 플레이어에 똑같이 쓴다 (D-11). */
    public static PlayerSetup standard(ItemRegistry items, Supplier<Difficulty> difficulty) {
        Objects.requireNonNull(items, "items");
        Objects.requireNonNull(difficulty, "difficulty");
        return new PlayerSetup() {
            @Override public BlockBoardDriver driver(Player player) {
                return new BlockBoardDriver(items);
            }

            @Override public GameEngine engine(Player player, BlockBoardDriver driver, GameEventListener listener,
                                               GameEngine.AttackLink link) {
                return new GameEngine(driver, listener, difficulty, link);
            }
        };
    }

    private final Map<Player, Seat> seats = new EnumMap<>(Player.class);
    private VersusListener listener = VersusListener.NONE;
    private State state = State.READY;
    private VersusMode mode = VersusMode.NORMAL;
    /** 시간제한 방식의 1초 타이머. 다른 방식에서는 돌지 않는다. */
    private final GameEngine.DropTimer clock;
    private int remainingSeconds;
    /** 일시정지로 끊긴 1초 중 이미 흐른 부분. 재개하면 남은 부분만 기다린다. */
    private int clockElapsedMillis;

    /** 실제 게임: 제한 시간은 Swing 타이머로 잰다. */
    public VersusMatch(PlayerSetup setup) {
        this(setup, GameEngine.newSwingTimer());
    }

    /** @param clock 시간제한 방식에서 1초마다 부를 타이머. 테스트는 직접 넘기는 타이머를 넣는다. */
    public VersusMatch(PlayerSetup setup, GameEngine.DropTimer clock) {
        Objects.requireNonNull(setup, "setup");
        this.clock = Objects.requireNonNull(clock, "clock");
        for (Player player : Player.values()) {
            seats.put(player, new Seat(player, setup));
        }
    }

    /** 대전 알림을 받을 곳. 한 곳만 둔다. */
    public void setListener(VersusListener listener) {
        this.listener = Objects.requireNonNull(listener, "listener");
    }

    /** 그 플레이어의 보드·점수·다음 블록 알림을 받을 곳. 1인 모드 화면이 받는 것과 같은 알림이다. */
    public void setPlayerListener(Player player, GameEventListener playerListener) {
        seat(player).view = Objects.requireNonNull(playerListener, "playerListener");
    }

    /** 새 판을 시작한다. 진행 중이던 판은 결과 없이 버린다. 시간제한은 기본 제한 시간(3분)이다. */
    public void start(VersusMode mode) {
        start(mode, GameConstants.VERSUS_TIME_LIMIT_SECONDS);
    }

    /**
     * 새 판을 시작한다. 진행 중이던 판은 결과 없이 버린다.
     *
     * @param timeLimitSeconds 시간제한 방식의 제한 시간(초). 방식 선택 화면에서 1~5분을 고른다. 다른 방식에서는 쓰지 않는다
     */
    public void start(VersusMode mode, int timeLimitSeconds) {
        this.mode = Objects.requireNonNull(mode, "mode");
        if (timeLimitSeconds <= 0) throw new IllegalArgumentException("timeLimitSeconds must be positive");
        clock.stop();
        stopEngines();
        remainingSeconds = mode == VersusMode.TIME_LIMIT ? timeLimitSeconds : 0;
        clockElapsedMillis = 0;
        for (Seat seat : seats.values()) {
            seat.queue.clear();
            seat.lastLevel = -1;
            // 엔진의 start()가 드라이버를 초기화하기 전에 정해야 아이템 진행 상황이 새 규칙으로 시작한다.
            seat.driver.setItemMode(mode == VersusMode.ITEM);
        }
        state = State.RUNNING;
        for (Seat seat : seats.values()) {
            listener.onQueueChanged(seat.player, List.of());
            seat.engine.start();
        }
        if (mode == VersusMode.TIME_LIMIT && state == State.RUNNING) {
            listener.onTimeChanged(remainingSeconds);
            startClock(SECOND_MILLIS);
        }
    }

    /** 플레이어의 조작 한 번. 진행 중일 때만 받는다. 일시정지와 나가기는 {@link #pause()}·{@link #stop()}을 쓴다. */
    public boolean handle(Player player, GameAction action) {
        Objects.requireNonNull(action, "action");
        if (action == GameAction.PAUSE || action == GameAction.QUIT) {
            throw new IllegalArgumentException("Use pause() or stop() for " + action);
        }
        Seat seat = seat(player);
        return state == State.RUNNING && seat.engine.handle(action);
    }

    /** 두 보드와 제한 시간을 함께 멈춘다. */
    public boolean pause() {
        if (state != State.RUNNING) return false;
        state = State.PAUSED;
        for (Seat seat : seats.values()) seat.engine.pause();
        if (mode == VersusMode.TIME_LIMIT) {
            clockElapsedMillis = Math.max(0, Math.min(SECOND_MILLIS - 1, clock.elapsedMillis()));
            clock.stop();
        }
        listener.onPauseChanged(true);
        return true;
    }

    /** 멈춘 곳에서 이어 간다. 제한 시간은 일시정지로 끊긴 1초의 남은 부분부터 다시 잰다. */
    public boolean resume() {
        if (state != State.PAUSED) return false;
        state = State.RUNNING;
        for (Seat seat : seats.values()) seat.engine.resume();
        if (mode == VersusMode.TIME_LIMIT) startClock(SECOND_MILLIS - clockElapsedMillis);
        listener.onPauseChanged(false);
        return true;
    }

    /** 승패 없이 그만둔다. 결과 알림은 오지 않는다. */
    public boolean stop() {
        if (state != State.RUNNING && state != State.PAUSED) return false;
        boolean wasPaused = state == State.PAUSED;
        clock.stop();
        stopEngines();
        state = State.READY;
        if (wasPaused) listener.onPauseChanged(false);
        return true;
    }

    @Override
    public void close() {
        stop();
        clock.stop();
        for (Seat seat : seats.values()) seat.engine.close();
    }

    public State state() { return state; }

    public VersusMode mode() { return mode; }

    /** 시간제한 대전의 남은 시간(초). 다른 방식에서는 0이다. */
    public int remainingSeconds() { return remainingSeconds; }

    public int score(Player player) { return seat(player).engine.getScore(); }

    public int level(Player player) { return seat(player).engine.getLevel(); }

    /** 그 플레이어의 공격 대기열. 위에서 아래 순서. */
    public List<GarbageLine> queue(Player player) { return seat(player).queue.lines(); }

    private void stopEngines() {
        for (Seat seat : seats.values()) {
            GameState engineState = seat.engine.getState();
            if (engineState == GameState.RUNNING || engineState == GameState.PAUSED) seat.engine.stop();
        }
    }

    private void startClock(int firstTickMillis) {
        clock.start(firstTickMillis, SECOND_MILLIS, this::tickSecond);
    }

    /** 제한 시간 1초가 지났다. 멈춘 뒤 늦게 도착한 호출은 무시한다. */
    private void tickSecond() {
        if (state != State.RUNNING || remainingSeconds <= 0) return;
        remainingSeconds--;
        listener.onTimeChanged(remainingSeconds);
        if (remainingSeconds == 0) timeUp();
    }

    /** 제한 시간이 끝났다. 점수가 높은 쪽이 이기고, 같으면 무승부다 (A9). */
    private void timeUp() {
        stopEngines();
        int p1 = score(Player.P1);
        int p2 = score(Player.P2);
        Player winner = p1 == p2 ? null : (p1 > p2 ? Player.P1 : Player.P2);
        finish(new MatchResult(winner, MatchResult.Reason.TIME_UP, p1, p2));
    }

    /** 한 플레이어가 더 쌓을 수 없게 되었다. 상대가 이긴다. */
    private void toppedOut(Seat loser) {
        if (state != State.RUNNING) return;
        Seat winner = seat(loser.player.opponent());
        if (winner.engine.getState() == GameState.RUNNING) winner.engine.stop();
        finish(new MatchResult(winner.player, MatchResult.Reason.TOP_OUT,
                score(Player.P1), score(Player.P2)));
    }

    private void finish(MatchResult result) {
        clock.stop();
        state = State.ENDED;
        listener.onMatchEnded(result);
    }

    private Seat seat(Player player) {
        return seats.get(Objects.requireNonNull(player, "player"));
    }

    /** 플레이어 한 명의 자리: 드라이버·엔진·대기열과, 엔진 알림을 화면으로 넘기는 연결. */
    private final class Seat implements GameEngine.AttackLink, GameEventListener {
        final Player player;
        final BlockBoardDriver driver;
        final GameEngine engine;
        final GarbageQueue queue = new GarbageQueue();
        GameEventListener view = NO_VIEW;
        int lastLevel = -1;

        Seat(Player player, PlayerSetup setup) {
            this.player = player;
            this.driver = Objects.requireNonNull(setup.driver(player), "driver");
            this.engine = Objects.requireNonNull(setup.engine(player, driver, this, this), "engine");
        }

        // ---- AttackLink: 엔진이 공격을 보내고 받는 통로

        @Override public void send(List<GarbageLine> lines) {
            Seat target = seat(player.opponent());
            if (target.queue.offer(lines) > 0) listener.onQueueChanged(target.player, target.queue.lines());
        }

        @Override public List<GarbageLine> takeIncoming() {
            if (queue.isEmpty()) return List.of();
            List<GarbageLine> drained = queue.drain();
            listener.onQueueChanged(player, List.of());
            return drained;
        }

        // ---- GameEventListener: 화면으로 넘기고, 레벨 변화와 패배를 가로챈다

        @Override public void onBoardUpdated(BoardSnapshot snapshot) {
            view.onBoardUpdated(snapshot);
            publishLevel();
        }

        @Override public void onLandingUpdated(BoardSnapshot falling, BoardSnapshot landing) {
            view.onLandingUpdated(falling, landing);
        }

        @Override public void onLinesCleared(List<Integer> rows, BoardSnapshot boardBeforeClear) {
            view.onLinesCleared(rows, boardBeforeClear);
        }

        @Override public void onScoreChanged(int score) {
            view.onScoreChanged(score);
        }

        @Override public void onNextBlocksChanged(int[] blockTypes) {
            view.onNextBlocksChanged(blockTypes);
        }

        @Override public void onPauseStateChanged(boolean paused) {
            view.onPauseStateChanged(paused);
        }

        @Override public void onFeverActivated(int seconds) {
            view.onFeverActivated(seconds);
        }

        @Override public void onSlowDownActivated(int previousLevel, int currentLevel) {
            view.onSlowDownActivated(previousLevel, currentLevel);
            publishLevel();
        }

        @Override public void onHardDropped(BoardSnapshot fallingBefore, BoardSnapshot fallingAfter) {
            view.onHardDropped(fallingBefore, fallingAfter);
        }

        @Override public void onBombExploded(CellPosition center, List<CellPosition> blastCells) {
            view.onBombExploded(center, blastCells);
        }

        @Override public void onAnvilCrushed(List<CellPosition> crushedCells) {
            view.onAnvilCrushed(crushedCells);
        }

        @Override public void onGameOver(int score) {
            view.onGameOver(score);
            toppedOut(this);
        }

        private void publishLevel() {
            int level = engine.getLevel();
            if (level != lastLevel) {
                lastLevel = level;
                listener.onLevelChanged(player, level);
            }
        }
    }

    private static final GameEventListener NO_VIEW = new GameEventListener() {
        @Override public void onBoardUpdated(BoardSnapshot snapshot) { }
        @Override public void onScoreChanged(int score) { }
        @Override public void onNextBlocksChanged(int[] blockTypes) { }
        @Override public void onPauseStateChanged(boolean paused) { }
        @Override public void onGameOver(int score) { }
    };
}

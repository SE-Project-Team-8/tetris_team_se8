/*
 * 로컬 대전 한 판의 진행: 플레이어마다 엔진·드라이버·공격 대기열 한 벌, 공격 전달, 승패, 일시정지.
 * 화면은 이 클래스만 상대한다 (docs/battle/ui-contract.md).
 */
package com.team.tetris.game.versus;

import com.team.tetris.common.Difficulty;
import com.team.tetris.common.battle.GarbageLine;
import com.team.tetris.common.battle.Player;
import com.team.tetris.common.battle.VersusMode;
import com.team.tetris.common.events.BoardSnapshot;
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
 */
public final class VersusMatch implements AutoCloseable {
    /** 대전의 진행 상태. */
    public enum State { READY, RUNNING, PAUSED, ENDED }

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

    public VersusMatch(PlayerSetup setup) {
        Objects.requireNonNull(setup, "setup");
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

    /** 새 판을 시작한다. 진행 중이던 판은 결과 없이 버린다. */
    public void start(VersusMode mode) {
        this.mode = Objects.requireNonNull(mode, "mode");
        stopEngines();
        for (Seat seat : seats.values()) {
            seat.queue.clear();
            seat.lastLevel = -1;
        }
        state = State.RUNNING;
        for (Seat seat : seats.values()) {
            listener.onQueueChanged(seat.player, List.of());
            seat.engine.start();
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

    /** 두 보드를 함께 멈춘다. */
    public boolean pause() {
        if (state != State.RUNNING) return false;
        state = State.PAUSED;
        for (Seat seat : seats.values()) seat.engine.pause();
        listener.onPauseChanged(true);
        return true;
    }

    public boolean resume() {
        if (state != State.PAUSED) return false;
        state = State.RUNNING;
        for (Seat seat : seats.values()) seat.engine.resume();
        listener.onPauseChanged(false);
        return true;
    }

    /** 승패 없이 그만둔다. 결과 알림은 오지 않는다. */
    public boolean stop() {
        if (state != State.RUNNING && state != State.PAUSED) return false;
        boolean wasPaused = state == State.PAUSED;
        stopEngines();
        state = State.READY;
        if (wasPaused) listener.onPauseChanged(false);
        return true;
    }

    @Override
    public void close() {
        stop();
        for (Seat seat : seats.values()) seat.engine.close();
    }

    public State state() { return state; }

    public VersusMode mode() { return mode; }

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

    /** 한 플레이어가 더 쌓을 수 없게 되었다. 상대가 이긴다. */
    private void toppedOut(Seat loser) {
        if (state != State.RUNNING) return;
        Seat winner = seat(loser.player.opponent());
        if (winner.engine.getState() == GameState.RUNNING) winner.engine.stop();
        finish(new MatchResult(winner.player, MatchResult.Reason.TOP_OUT,
                score(Player.P1), score(Player.P2)));
    }

    private void finish(MatchResult result) {
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

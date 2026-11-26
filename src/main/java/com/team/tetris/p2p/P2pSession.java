package com.team.tetris.p2p;

import com.team.tetris.common.Difficulty;
import com.team.tetris.common.battle.GarbageLine;
import com.team.tetris.common.battle.Player;
import com.team.tetris.common.battle.VersusMode;
import com.team.tetris.common.events.BoardSnapshot;
import com.team.tetris.common.events.CellPosition;
import com.team.tetris.common.events.GameEventListener;
import com.team.tetris.common.persistence.PropertiesFile;
import com.team.tetris.game.BlockBoardDriver;
import com.team.tetris.game.GameAction;
import com.team.tetris.game.GameEngine;
import com.team.tetris.game.item.ItemRegistry;
import com.team.tetris.game.versus.GarbageQueue;
import com.team.tetris.game.versus.MatchResult;
import com.team.tetris.game.versus.VersusMatch;

import javax.swing.SwingUtilities;
import javax.swing.Timer;
import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Properties;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/** Lobby and match coordination on the Swing EDT. Each peer runs its own game engine. */
public final class P2pSession implements AutoCloseable {
    public static final int PORT = 47820;
    public static final int MIN_TIME_LIMIT_MINUTES = 1;
    public static final int MAX_TIME_LIMIT_MINUTES = 5;
    private static final int DEFAULT_TIME_LIMIT_MINUTES = 3;
    private static final String LAST_IP_KEY = "lastIp";
    private static final long PING_INTERVAL = TimeUnit.MILLISECONDS.toNanos(500);
    // Provisional values for LAN play until the team agrees on the lag and disconnect thresholds.
    private static final long LAG_RESPONSE_TIME = TimeUnit.SECONDS.toNanos(1);
    private static final long DISCONNECT_TIME = TimeUnit.SECONDS.toNanos(5);
    private static final long LAG_ROUND_TRIP = TimeUnit.MILLISECONDS.toNanos(500);
    private static final long SECOND = TimeUnit.SECONDS.toNanos(1);

    public enum State { IDLE, CONNECTING, LOBBY, PLAYING, FINISHING }

    /** Display data for one player. Board and landing values are read-only snapshots. */
    public record BoardState(BoardSnapshot board, List<Integer> landing, int next, int score, int level,
                             List<GarbageLine> queue) { }

    private final ItemRegistry items;
    private final Supplier<Difficulty> difficulty;
    private final Runnable changed;
    private final int port;
    private final Path recentPeerFile = Path.of(System.getProperty("user.home"), ".tetris-team-se8", "p2p.properties");
    private final Timer pulse = new Timer(250, event -> tick());
    private final GarbageQueue incoming = new GarbageQueue();

    private P2pEffectListener effectListener;
    private P2pConnection connection;
    private long connectionId;
    private State state = State.IDLE;
    private Player local = Player.P1;
    private String status = "";
    private String connectedIp = "";
    private boolean recentIpSaveFailed;
    private boolean error;
    private VersusMode mode = VersusMode.NORMAL;
    private int timeLimitMinutes = DEFAULT_TIME_LIMIT_MINUTES;
    private int selection;
    private boolean localReady;
    private boolean remoteReady;
    private int round;

    private GameEngine engine;
    private BoardSnapshot board;
    private List<Integer> landing = List.of();
    private int next = BoardSnapshot.EMPTY;
    private BoardState localBoard;
    private BoardState remoteBoard;
    private boolean frameScheduled;
    private boolean paused;
    private long deadline;
    private long pausedTime;
    private int seconds;
    private MatchResult result;
    private MatchResult.Reason finishingReason;
    private Player finishingWinner;
    private long finishingSince;

    private long lastPing;
    private long lastPong;
    private long rtt = -1;
    private long pingToken;
    private boolean waitingPong;
    private boolean lagging;

    public P2pSession(ItemRegistry items, Supplier<Difficulty> difficulty, Runnable changed) {
        this(items, difficulty, changed, PORT);
    }

    P2pSession(ItemRegistry items, Supplier<Difficulty> difficulty, Runnable changed, int port) {
        this.items = items;
        this.difficulty = difficulty;
        this.changed = changed;
        this.port = port;
    }

    /** Registers the effect callbacks on the EDT. Pass null to remove the listener. */
    public void setEffectListener(P2pEffectListener listener) {
        effectListener = listener;
    }

    public void host() {
        open(null);
    }

    public boolean join(String address) {
        if (state != State.IDLE) return false;
        try {
            open(P2pConnection.parseIp(address));
            return true;
        } catch (UnknownHostException invalid) {
            fail("올바른 IP 주소를 입력하세요.");
            return false;
        }
    }

    private void open(InetAddress address) {
        if (state != State.IDLE) return;
        local = address == null ? Player.P1 : Player.P2;
        state = State.CONNECTING;
        status = isHost() ? "서버를 여는 중…" : "서버에 연결 중…";
        error = false;
        long attempt = ++connectionId;
        connection = new P2pConnection(new P2pConnection.Listener() {
            @Override public void listening(String addresses) {
                if (attempt != connectionId) return;
                status = "상대 접속 대기 중 · 포트 " + port + "\n내 IP  " + addresses;
                changed.run();
            }

            @Override public void connected() {
                if (attempt != connectionId) return;
                connectedIp = address == null ? "" : address.getHostAddress();
                if (address != null) rememberPeer();
                state = State.LOBBY;
                status = "상대와 연결되었습니다.";
                lastPong = System.nanoTime();
                lastPing = lastPong - PING_INTERVAL;
                pulse.start();
                changed.run();
            }

            @Override public void message(P2pProtocol.Message message) {
                if (attempt == connectionId) receive(message);
            }

            @Override public void failed(String reason) {
                if (attempt == connectionId) fail(reason);
            }
        });
        connection.open(address, port);
        changed.run();
    }

    private void rememberPeer() {
        Properties values = new Properties();
        values.setProperty(LAST_IP_KEY, connectedIp);
        try {
            PropertiesFile.write(recentPeerFile, values);
        } catch (IOException failure) {
            recentIpSaveFailed = true;
        }
    }

    /** The last server IP from a successful client connection, or an empty string on first use. */
    public String recentIp() throws IOException {
        return PropertiesFile.read(recentPeerFile).getProperty(LAST_IP_KEY, "");
    }

    public void selectMode(VersusMode selected) {
        selectMode(selected, timeLimitMinutes);
    }

    /** Selects a mode and, for a timed match, its duration. A changed selection clears both ready flags. */
    public void selectMode(VersusMode selected, int minutes) {
        if (state != State.LOBBY || !isHost() || (localReady && selected != mode)) return;
        Objects.requireNonNull(selected, "selected");
        if (minutes < MIN_TIME_LIMIT_MINUTES || minutes > MAX_TIME_LIMIT_MINUTES) {
            throw new IllegalArgumentException("P2P time limit must be between 1 and 5 minutes");
        }
        int selectedMinutes = selected == VersusMode.TIME_LIMIT ? minutes : timeLimitMinutes;
        if (mode == selected && timeLimitMinutes == selectedMinutes) return;
        updateMode(selection + 1, selected, selectedMinutes);
        send(new P2pProtocol.Mode(selection, mode, timeLimitMinutes));
        changed.run();
    }

    private void updateMode(int revision, VersusMode selected, int minutes) {
        selection = revision;
        mode = selected;
        timeLimitMinutes = minutes;
        remoteReady = false;
        localReady = false;
    }

    public void ready() {
        if (state != State.LOBBY || localReady) return;
        localReady = true;
        send(new P2pProtocol.Ready(round + 1, selection));
        checkStart();
        changed.run();
    }

    private void checkStart() {
        if (!isHost() || !localReady || !remoteReady) return;
        P2pProtocol.Start start = new P2pProtocol.Start(round + 1, mode, difficulty.get(), timeLimitMinutes);
        send(start);
        startRound(start);
    }

    private void startRound(P2pProtocol.Start start) {
        round = start.round();
        state = State.PLAYING;
        result = null;
        paused = false;
        board = null;
        landing = List.of();
        next = BoardSnapshot.EMPTY;
        localBoard = null;
        remoteBoard = null;
        frameScheduled = false;
        incoming.clear();
        seconds = isTimed() ? (int) TimeUnit.MINUTES.toSeconds(start.timeLimitMinutes()) : 0;
        deadline = System.nanoTime() + seconds * SECOND;

        VersusMatch.PlayerSetup setup = VersusMatch.standard(items, start::difficulty);
        BlockBoardDriver driver = setup.driver(local);
        driver.setItemMode(mode == VersusMode.ITEM);
        engine = setup.engine(local, driver, new GameEventListener() {
            @Override public void onBoardUpdated(BoardSnapshot snapshot) {
                board = snapshot;
                scheduleFrame();
            }
            @Override public void onLandingUpdated(BoardSnapshot falling, BoardSnapshot ghost) {
                landing = landingPositions(ghost);
            }
            @Override public void onScoreChanged(int score) { scheduleFrame(); }
            @Override public void onNextBlocksChanged(int[] types) {
                next = types.length == 0 ? BoardSnapshot.EMPTY : types[0];
                scheduleFrame();
            }
            @Override public void onPauseStateChanged(boolean value) { }

            @Override public void onLinesCleared(List<Integer> rows, BoardSnapshot boardBeforeClear) {
                publishEffect(new P2pProtocol.LinesCleared(rows, boardBeforeClear));
            }

            @Override public void onHardDropped(BoardSnapshot fallingBefore, BoardSnapshot fallingAfter) {
                publishEffect(new P2pProtocol.HardDropped(fallingBefore, fallingAfter));
            }

            @Override public void onBombExploded(CellPosition center, List<CellPosition> blastCells) {
                publishEffect(new P2pProtocol.BombExploded(center, blastCells));
            }

            @Override public void onAnvilCrushed(List<CellPosition> crushedCells) {
                publishEffect(new P2pProtocol.AnvilCrushed(crushedCells));
            }

            @Override public void onFeverActivated(int seconds) {
                publishEffect(new P2pProtocol.FeverActivated(seconds));
            }

            @Override public void onSlowDownActivated(int previousLevel, int currentLevel) {
                publishEffect(new P2pProtocol.SlowDownActivated(previousLevel, currentLevel));
            }

            @Override public void onGameOver(int score) {
                publishFrame();
                if (checkDeadline(System.nanoTime())) return;
                if (isHost()) finish(MatchResult.Reason.TOP_OUT, local.opponent());
                else send(new P2pProtocol.TopOut(round));
            }
        }, new GameEngine.AttackLink() {
            @Override public void send(List<GarbageLine> lines) {
                P2pSession.this.send(new P2pProtocol.Attack(round, lines));
            }
            @Override public List<GarbageLine> takeIncoming() { return incoming.drain(); }
        });
        engine.start();
        changed.run();
    }

    public void handle(GameAction action) {
        if (state != State.PLAYING) return;
        if (action == GameAction.PAUSE) {
            togglePause();
            return;
        }
        if (action != GameAction.QUIT && !paused && !checkDeadline(System.nanoTime())) engine.handle(action);
    }

    private void togglePause() {
        if (checkDeadline(System.nanoTime())) return;
        if (!isHost()) {
            send(new P2pProtocol.PauseRequest(round));
            return;
        }
        send(new P2pProtocol.Pause(round, !paused));
        applyPause(!paused);
    }

    private void applyPause(boolean value) {
        if (paused == value) return;
        long now = System.nanoTime();
        paused = value;
        if (paused) {
            pausedTime = Math.max(0, deadline - now);
            engine.pause();
        } else {
            deadline = now + pausedTime;
            engine.resume();
        }
        changed.run();
    }

    /** Returns true once the local engine has reached the time limit. */
    private boolean checkDeadline(long now) {
        if (state != State.PLAYING || !isTimed() || paused || now - deadline < 0) return false;
        seconds = 0;
        if (isHost()) finish(MatchResult.Reason.TIME_UP, null);
        else engine.stop();
        return true;
    }

    private void finish(MatchResult.Reason reason, Player winner) {
        freeze();
        finishingReason = reason;
        finishingWinner = winner;
        send(new P2pProtocol.Stop(round));
        changed.run();
    }

    private void freeze() {
        state = State.FINISHING;
        finishingSince = System.nanoTime();
        engine.stop();
    }

    private void finalizeResult(int clientScore) {
        int hostScore = engine.getScore();
        Player winner = finishingWinner;
        if (finishingReason == MatchResult.Reason.TIME_UP && hostScore != clientScore) {
            winner = hostScore > clientScore ? Player.P1 : Player.P2;
        }
        MatchResult ended = new MatchResult(winner, finishingReason, hostScore, clientScore);
        send(new P2pProtocol.Result(round, ended));
        returnToLobby(ended);
    }

    private void returnToLobby(MatchResult ended) {
        engine.close();
        engine = null;
        paused = false;
        result = ended;
        state = State.LOBBY;
        localReady = false;
        remoteReady = false;
        changed.run();
    }

    private void receive(P2pProtocol.Message message) {
        switch (message) {
            case P2pProtocol.Mode m -> {
                if (!isHost() && state == State.LOBBY && m.selection() > selection) {
                    updateMode(m.selection(), m.mode(), m.timeLimitMinutes());
                    changed.run();
                }
            }
            case P2pProtocol.Ready m -> {
                if (state == State.LOBBY && m.round() == round + 1 && m.selection() == selection) {
                    remoteReady = true;
                    checkStart();
                    changed.run();
                }
            }
            case P2pProtocol.Start m -> {
                if (!isHost() && state == State.LOBBY && localReady && remoteReady
                        && m.round() == round + 1 && m.mode() == mode
                        && m.timeLimitMinutes() == timeLimitMinutes) startRound(m);
            }
            case P2pProtocol.Board m -> {
                if (isCurrentRound(m.round())) {
                    remoteBoard = m.state();
                    changed.run();
                }
            }
            case P2pProtocol.Effect m -> {
                if (isCurrentRound(m.round())) notifyEffect(local.opponent(), m.data());
            }
            case P2pProtocol.Attack m -> {
                if (state == State.PLAYING && m.round() == round && incoming.offer(m.lines()) > 0) scheduleFrame();
            }
            case P2pProtocol.PauseRequest m -> {
                if (isHost() && state == State.PLAYING && m.round() == round) togglePause();
            }
            case P2pProtocol.Pause m -> {
                if (!isHost() && state == State.PLAYING && m.round() == round) applyPause(m.paused());
            }
            case P2pProtocol.TopOut m -> {
                if (isHost() && state == State.PLAYING && m.round() == round && !checkDeadline(System.nanoTime())) {
                    finish(MatchResult.Reason.TOP_OUT, local);
                }
            }
            case P2pProtocol.Stop m -> {
                if (!isHost() && state == State.PLAYING && m.round() == round) {
                    freeze();
                    send(new P2pProtocol.FinalScore(round, engine.getScore()));
                    changed.run();
                }
            }
            case P2pProtocol.FinalScore m -> {
                if (isHost() && state == State.FINISHING && m.round() == round) finalizeResult(m.score());
            }
            case P2pProtocol.Result m -> {
                if (!isHost() && state == State.FINISHING && m.round() == round) returnToLobby(m.result());
            }
            case P2pProtocol.Ping m -> send(new P2pProtocol.Pong(m.token()));
            case P2pProtocol.Pong m -> {
                if (waitingPong && m.token() == pingToken) {
                    lastPong = System.nanoTime();
                    rtt = lastPong - lastPing;
                    waitingPong = false;
                    lagging = rtt >= LAG_ROUND_TRIP;
                    changed.run();
                }
            }
        }
    }

    private boolean isCurrentRound(int candidate) {
        return candidate == round && (state == State.PLAYING || state == State.FINISHING);
    }

    private void publishEffect(P2pProtocol.EffectData data) {
        // Queue the effect before the board update that follows this engine event.
        send(new P2pProtocol.Effect(round, data));
        notifyEffect(local, data);
    }

    private void notifyEffect(Player player, P2pProtocol.EffectData data) {
        P2pEffectListener listener = effectListener;
        if (listener == null) return;
        switch (data) {
            case P2pProtocol.LinesCleared e -> listener.linesCleared(player, e.rows(), e.boardBeforeClear());
            case P2pProtocol.HardDropped e -> listener.hardDropped(player, e.fallingBefore(), e.fallingAfter());
            case P2pProtocol.BombExploded e -> listener.bombExploded(player, e.center(), e.cells());
            case P2pProtocol.AnvilCrushed e -> listener.anvilCrushed(player, e.cells());
            case P2pProtocol.FeverActivated e -> listener.feverActivated(player, e.seconds());
            case P2pProtocol.SlowDownActivated e -> listener.slowDownActivated(player, e.previousLevel(), e.currentLevel());
        }
    }

    private void scheduleFrame() {
        if (frameScheduled) return;
        frameScheduled = true;
        int scheduledRound = round;
        long scheduledConnection = connectionId;
        SwingUtilities.invokeLater(() -> {
            if (scheduledConnection != connectionId || scheduledRound != round) return;
            frameScheduled = false;
            publishFrame();
        });
    }

    private void publishFrame() {
        if (state != State.PLAYING || board == null) return;
        localBoard = new BoardState(board, landing, next, engine.getScore(), engine.getLevel(), incoming.lines());
        send(new P2pProtocol.Board(round, localBoard));
        changed.run();
    }

    private static List<Integer> landingPositions(BoardSnapshot snapshot) {
        if (snapshot == null) return List.of();
        List<Integer> positions = new ArrayList<>(4);
        for (int row = 0; row < snapshot.rows(); row++) {
            for (int col = 0; col < snapshot.cols(); col++) {
                if (snapshot.cells()[row][col] != BoardSnapshot.EMPTY) positions.add(row * snapshot.cols() + col);
            }
        }
        return List.copyOf(positions);
    }

    private void tick() {
        long now = System.nanoTime();
        if (now - lastPong >= DISCONNECT_TIME) {
            fail("일정 시간 상대의 응답이 없어 연결을 종료했습니다.");
            return;
        }
        if (state == State.FINISHING && now - finishingSince >= DISCONNECT_TIME) {
            fail("상대의 경기 종료 응답을 받지 못했습니다.");
            return;
        }
        if (!waitingPong && now - lastPing >= PING_INTERVAL) {
            waitingPong = true;
            lastPing = now;
            send(new P2pProtocol.Ping(++pingToken));
        }
        boolean showingLag = rtt >= LAG_ROUND_TRIP || now - lastPong >= LAG_RESPONSE_TIME;
        boolean refresh = lagging != showingLag;
        lagging = showingLag;

        if (state == State.PLAYING && isTimed() && !paused) {
            int time = (int) (Math.max(0, deadline - now + SECOND - 1) / SECOND);
            refresh |= time != seconds;
            seconds = time;
            checkDeadline(now);
        }
        if (refresh) changed.run();
    }

    private void send(P2pProtocol.Message message) {
        connection.send(message);
    }

    private void fail(String reason) {
        reset();
        status = reason;
        error = true;
        changed.run();
    }

    @Override
    public void close() {
        reset();
        changed.run();
    }

    private void reset() {
        ++connectionId;
        pulse.stop();
        if (connection != null) connection.close();
        if (engine != null) engine.close();
        connection = null;
        engine = null;
        state = State.IDLE;
        status = "";
        connectedIp = "";
        recentIpSaveFailed = false;
        error = false;
        mode = VersusMode.NORMAL;
        timeLimitMinutes = DEFAULT_TIME_LIMIT_MINUTES;
        selection = 0;
        round = 0;
        result = null;
        remoteReady = false;
        localReady = false;
        paused = false;
        lagging = false;
        waitingPong = false;
        rtt = -1;
        seconds = 0;
        localBoard = null;
        remoteBoard = null;
        board = null;
        landing = List.of();
        frameScheduled = false;
        incoming.clear();
    }

    public State state() { return state; }
    public boolean isHost() { return local == Player.P1; }
    public Player localPlayer() { return local; }
    public String status() { return status; }
    public String connectedIp() { return connectedIp; }
    public boolean recentIpSaveFailed() { return recentIpSaveFailed; }
    public boolean hasError() { return error; }
    public VersusMode mode() { return mode; }
    public int timeLimitMinutes() { return timeLimitMinutes; }
    public boolean isTimed() { return mode == VersusMode.TIME_LIMIT; }
    public boolean localReady() { return localReady; }
    public boolean remoteReady() { return remoteReady; }
    public boolean paused() { return paused; }
    public boolean lagging() { return lagging; }
    public long roundTripMillis() { return rtt < 0 ? -1 : TimeUnit.NANOSECONDS.toMillis(rtt); }
    public int seconds() { return seconds; }
    public MatchResult result() { return result; }
    public BoardState board(Player player) { return player == local ? localBoard : remoteBoard; }
}

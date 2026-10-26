package com.team.tetris.game;

import com.team.tetris.common.events.BoardSnapshot;
import com.team.tetris.common.events.GameEventListener;
import com.team.tetris.common.Difficulty;
import com.team.tetris.game.item.Item;

import javax.swing.SwingUtilities;
import javax.swing.Timer;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * Game coordinator. Invoke lifecycle/input methods on the Swing event dispatch thread
 * (EDT); timer callbacks and UI notifications also run there. Disk I/O belongs outside
 * this class. See docs/person2-integration.md for the block adapter contract.
 */
public final class GameEngine implements AutoCloseable {
    private static final int SURVIVAL_POINTS_PER_SECOND = 1;
    /** BlockBoardDriver adapts the block package without adding a game dependency to it. */
    public interface BoardDriver {
        /** Configure the next game before reset() fills the preview. */
        default void configureDifficulty(Difficulty difficulty) { }
        /** Empty the board and preview; do not spawn a block. */
        void reset();
        /** Consume the preview and spawn one piece; false means blocked spawn. */
        boolean spawnNextBlock();
        boolean moveLeft();
        boolean moveRight();
        /** Move exactly one cell if possible; never lock or spawn automatically. */
        boolean moveDown();
        boolean rotateClockwise();
        /** Move to the last legal row, return actual distance; do not lock/spawn. */
        int hardDrop();
        /** Weights award neither gravity/soft-drop nor hard-drop points. */
        default boolean awardsDropPoints() { return true; }
        /** Lock once and clear full rows; report overflow above the board as topOut. */
        LockResult lockAndClearLines();
        /** Item carried by the piece the last lockAndClearLines() locked; null for a regular block. */
        default Item lockedItem() { return null; }
        /** Composed 20 x 10 board; EMPTY = -1; falling piece is included. */
        BoardSnapshot snapshot();
        /** Cells occupied by the falling piece only, or null when unavailable. */
        default BoardSnapshot fallingSnapshot() { return null; }
        /** Landing ghost cells for the falling piece, or null when unavailable. */
        default BoardSnapshot landingSnapshot() { return null; }
        /** Preview block IDs use the block/UI team's agreed 0..6 mapping. */
        int[] nextBlockTypes();
    }

    public record LockResult(
            int clearedLines, boolean topOut, List<Integer> clearedRows, BoardSnapshot boardBeforeClear) {
        public LockResult {
            if (clearedLines < 0 || clearedLines > 4) {
                throw new IllegalArgumentException("clearedLines must be between 0 and 4");
            }
            clearedRows = List.copyOf(Objects.requireNonNull(clearedRows, "clearedRows"));
            if (!clearedRows.isEmpty() && clearedRows.size() != clearedLines) {
                throw new IllegalArgumentException("clearedRows must match clearedLines");
            }
            if (!clearedRows.isEmpty() && boardBeforeClear == null) {
                throw new IllegalArgumentException("A pre-clear board snapshot is required for cleared rows");
            }
        }

        public LockResult(int clearedLines, boolean topOut, List<Integer> clearedRows) {
            this(clearedLines, topOut, clearedRows, null);
        }

        public LockResult(int clearedLines, boolean topOut) {
            this(clearedLines, topOut, List.of(), null);
        }
    }

    /** Injectable periodic scheduler; start replaces the old schedule, on the EDT. */
    public interface DropTimer {
        void start(int intervalMillis, Runnable tick);
        void stop();
    }

    private static final class SwingDropTimer implements DropTimer {
        private Timer timer;

        @Override
        public void start(int intervalMillis, Runnable tick) {
            stop();
            timer = new Timer(intervalMillis, event -> tick.run());
            timer.setCoalesce(true);
            timer.start();
        }

        @Override
        public void stop() {
            if (timer != null) timer.stop();
        }
    }

    private final BoardDriver board;
    private final GameEventListener listener;
    private final ScoringPolicy scoring;
    private final SpeedPolicy speedPolicy;
    private final DropTimer timer;
    private final DropTimer survivalTimer;
    private final Supplier<Difficulty> difficultySource;
    private Difficulty difficulty = Difficulty.NORMAL;
    private GameState state = GameState.READY;
    private int score;
    private SpeedPolicy.Speed speed;
    private int generatedBlocks;
    private int clearedLines;
    private long timerGeneration;
    private long survivalGeneration;

    public GameEngine(BoardDriver board, GameEventListener listener) {
        this(board, listener, () -> Difficulty.NORMAL);
    }

    public GameEngine(BoardDriver board, GameEventListener listener, Supplier<Difficulty> difficultySource) {
        this(board, listener, new ScoreCalculator(), new DefaultSpeedPolicy(), new SwingDropTimer(),
                new SwingDropTimer(), difficultySource);
    }

    public GameEngine(BoardDriver board, GameEventListener listener, ScoringPolicy scoring, DropTimer timer) {
        this(board, listener, scoring, new DefaultSpeedPolicy(), timer);
    }

    public GameEngine(BoardDriver board, GameEventListener listener, ScoringPolicy scoring,
                      SpeedPolicy speedPolicy, DropTimer timer) {
        this(board, listener, scoring, speedPolicy, timer, new SwingDropTimer(), () -> Difficulty.NORMAL);
    }

    public GameEngine(BoardDriver board, GameEventListener listener, ScoringPolicy scoring,
                      SpeedPolicy speedPolicy, DropTimer timer, DropTimer survivalTimer,
                      Supplier<Difficulty> difficultySource) {
        this.board = Objects.requireNonNull(board, "board");
        this.listener = Objects.requireNonNull(listener, "listener");
        this.scoring = Objects.requireNonNull(scoring, "scoring");
        this.speedPolicy = Objects.requireNonNull(speedPolicy, "speedPolicy");
        this.speed = Objects.requireNonNull(speedPolicy.calculate(0, 0), "speed");
        this.timer = Objects.requireNonNull(timer, "timer");
        this.survivalTimer = Objects.requireNonNull(survivalTimer, "survivalTimer");
        this.difficultySource = Objects.requireNonNull(difficultySource, "difficultySource");
    }

    /** Start a fresh game. Resume a paused game with resume(), not start(). */
    public boolean start() {
        requireEdt();
        if (state == GameState.RUNNING || state == GameState.PAUSED) return false;
        cancelTimer();
        difficulty = Objects.requireNonNull(difficultySource.get(), "difficulty");
        board.configureDifficulty(difficulty);
        board.reset();
        score = 0;
        generatedBlocks = 0;
        clearedLines = 0;
        updateSpeed();
        transition(GameState.RUNNING);
        listener.onPauseStateChanged(false);
        listener.onScoreChanged(0);
        if (!spawn()) {
            gameOver();
            return true;
        }
        scheduleTimer();
        scheduleSurvivalTimer();
        publishBoard();
        publishPreview();
        return true;
    }

    public boolean pause() {
        requireEdt();
        if (state != GameState.RUNNING) return false;
        transition(GameState.PAUSED);
        cancelTimer();
        listener.onPauseStateChanged(true);
        return true;
    }

    /** Resume with a full new drop interval; paused time never accumulates. */
    public boolean resume() {
        requireEdt();
        if (state != GameState.PAUSED) return false;
        transition(GameState.RUNNING);
        scheduleTimer();
        scheduleSurvivalTimer();
        listener.onPauseStateChanged(false);
        return true;
    }

    /** Voluntary exit: stop the timer without registering a completed-game score. */
    public boolean stop() {
        requireEdt();
        if (state != GameState.RUNNING && state != GameState.PAUSED) return false;
        boolean wasPaused = state == GameState.PAUSED;
        transition(GameState.STOPPED);
        cancelTimer();
        if (wasPaused) listener.onPauseStateChanged(false);
        return true;
    }

    /** Every movement key press is handled immediately, including repeated presses. */
    public boolean handle(GameAction action) {
        requireEdt();
        Objects.requireNonNull(action, "action");
        return switch (action) {
            case QUIT -> stop();
            case PAUSE -> state == GameState.PAUSED ? resume() : pause();
            case MOVE_LEFT -> state == GameState.RUNNING && publishIfMoved(board.moveLeft());
            case MOVE_RIGHT -> state == GameState.RUNNING && publishIfMoved(board.moveRight());
            case ROTATE_CLOCKWISE -> state == GameState.RUNNING && publishIfMoved(board.rotateClockwise());
            case SOFT_DROP -> state == GameState.RUNNING && dropOneCell();
            case HARD_DROP -> state == GameState.RUNNING && hardDrop();
        };
    }

    private boolean hardDrop() {
        boolean awardsPoints = board.awardsDropPoints();
        int distance = board.hardDrop();
        if (awardsPoints) award(scoring.dropPoints(distance, getLevel()));
        lockAndAdvance();
        return true;
    }

    public GameState getState() { return state; }
    public int getScore() { return score; }
    public int getLevel() { return speed.level(); }
    public int getGeneratedBlocks() { return generatedBlocks; }
    public int getClearedLines() { return clearedLines; }
    public Difficulty getDifficulty() { return difficulty; }

    public int getDropIntervalMillis() {
        return speed.dropIntervalMillis();
    }

    @Override
    public void close() {
        requireEdt();
        stop();
        cancelTimer();
    }

    private boolean dropOneCell() {
        if (board.moveDown()) {
            if (board.awardsDropPoints()) award(scoring.dropPoints(1, getLevel()));
            publishBoard();
        } else {
            lockAndAdvance();
        }
        return true;
    }

    private void lockAndAdvance() {
        LockResult result = Objects.requireNonNull(board.lockAndClearLines(), "lock result");
        if (!result.clearedRows().isEmpty()) {
            listener.onLinesCleared(result.clearedRows(), result.boardBeforeClear());
        }
        award(scoring.lineClearPoints(result.clearedLines(), getLevel(), difficulty));
        clearedLines = addCount(clearedLines, result.clearedLines());
        if (result.topOut()) {
            gameOver();
            return;
        }
        if (!spawn()) {
            gameOver();
            return;
        }
        scheduleTimer();
        publishBoard();
        publishPreview();
    }

    private boolean spawn() {
        if (!board.spawnNextBlock()) return false;
        generatedBlocks = addCount(generatedBlocks, 1);
        updateSpeed();
        return true;
    }

    private void updateSpeed() {
        speed = Objects.requireNonNull(speedPolicy.calculate(generatedBlocks, clearedLines, difficulty), "speed");
    }

    private static int addCount(int count, int increment) {
        return (int) Math.min(Integer.MAX_VALUE, (long) count + increment);
    }

    private void gameOver() {
        transition(GameState.GAME_OVER);
        cancelTimer();
        publishBoard();
        listener.onGameOver(score);
    }

    private void award(int points) {
        if (points == 0) return;
        int updated = scoring.add(score, points);
        if (updated != score) {
            score = updated;
            listener.onScoreChanged(score);
        }
    }

    private boolean publishIfMoved(boolean moved) {
        if (moved) publishBoard();
        return moved;
    }

    private void publishBoard() {
        int[][] cells = board.snapshot().cells();
        int[][] copy = new int[cells.length][];
        for (int row = 0; row < cells.length; row++) copy[row] = cells[row].clone();
        listener.onBoardUpdated(new BoardSnapshot(copy));
        listener.onLandingUpdated(board.fallingSnapshot(), board.landingSnapshot());
    }

    private void publishPreview() {
        listener.onNextBlocksChanged(board.nextBlockTypes().clone());
    }

    private void scheduleTimer() {
        long generation = ++timerGeneration;
        timer.start(getDropIntervalMillis(), () -> {
            requireEdt();
            if (generation == timerGeneration && state == GameState.RUNNING) dropOneCell();
        });
    }

    private void cancelTimer() {
        timerGeneration++;
        survivalGeneration++;
        timer.stop();
        survivalTimer.stop();
    }

    /** Independent from gravity: spawning a new block never restarts the scoring interval. */
    private void scheduleSurvivalTimer() {
        long generation = ++survivalGeneration;
        survivalTimer.start(1000, () -> {
            requireEdt();
            if (generation == survivalGeneration && state == GameState.RUNNING) {
                award(SURVIVAL_POINTS_PER_SECOND);
            }
        });
    }

    private void transition(GameState next) {
        if (!state.canTransitionTo(next)) throw new IllegalStateException(state + " -> " + next);
        state = next;
    }

    private static void requireEdt() {
        if (!SwingUtilities.isEventDispatchThread()) {
            throw new IllegalStateException("GameEngine must be used on the Swing EDT");
        }
    }
}

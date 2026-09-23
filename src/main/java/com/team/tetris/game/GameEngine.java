package com.team.tetris.game;

import com.team.tetris.common.events.BoardSnapshot;
import com.team.tetris.common.events.GameEventListener;

import javax.swing.SwingUtilities;
import javax.swing.Timer;
import java.util.Objects;

/**
 * Game coordinator. Invoke lifecycle/input methods on the Swing event dispatch thread
 * (EDT); timer callbacks and UI notifications also run there. Disk I/O belongs outside
 * this class. See docs/person2-integration.md for the block adapter contract.
 */
public final class GameEngine implements AutoCloseable {
    /**
     * Adapter implemented alongside 담당자1's block code, not by the block package
     * itself (block must not import game). Engine owns spawning and score calculation.
     */
    public interface BoardDriver {
        /** Empty the board and reset the generator/preview; do not spawn a block. */
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
        /** Lock once and clear full rows; report overflow above the board as topOut. */
        LockResult lockAndClearLines();
        /** Composed 20 x 10 board; EMPTY = -1; falling piece is included. */
        BoardSnapshot snapshot();
        /** Preview block IDs use the block/UI team's agreed 0..6 mapping. */
        int[] nextBlockTypes();
    }

    public record LockResult(int clearedLines, boolean topOut) {
        public LockResult {
            if (clearedLines < 0 || clearedLines > 4) {
                throw new IllegalArgumentException("clearedLines must be between 0 and 4");
            }
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
    private GameState state = GameState.READY;
    private int score;
    private SpeedPolicy.Speed speed;
    private int generatedBlocks;
    private int clearedLines;
    private long timerGeneration;

    public GameEngine(BoardDriver board, GameEventListener listener) {
        this(board, listener, new ScoreCalculator(), new DefaultSpeedPolicy(), new SwingDropTimer());
    }

    public GameEngine(BoardDriver board, GameEventListener listener, ScoringPolicy scoring, DropTimer timer) {
        this(board, listener, scoring, new DefaultSpeedPolicy(), timer);
    }

    public GameEngine(BoardDriver board, GameEventListener listener, ScoringPolicy scoring,
                      SpeedPolicy speedPolicy, DropTimer timer) {
        this.board = Objects.requireNonNull(board, "board");
        this.listener = Objects.requireNonNull(listener, "listener");
        this.scoring = Objects.requireNonNull(scoring, "scoring");
        this.speedPolicy = Objects.requireNonNull(speedPolicy, "speedPolicy");
        this.speed = Objects.requireNonNull(speedPolicy.calculate(0, 0), "speed");
        this.timer = Objects.requireNonNull(timer, "timer");
    }

    /** Start a fresh game. Resume a paused game with resume(), not start(). */
    public boolean start() {
        requireEdt();
        if (state == GameState.RUNNING || state == GameState.PAUSED) return false;
        cancelTimer();
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
        award(scoring.dropPoints(board.hardDrop(), getLevel()));
        lockAndAdvance();
        return true;
    }

    public GameState getState() { return state; }
    public int getScore() { return score; }
    public int getLevel() { return speed.level(); }
    public int getGeneratedBlocks() { return generatedBlocks; }
    public int getClearedLines() { return clearedLines; }

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
            award(scoring.dropPoints(1, getLevel()));
            publishBoard();
        } else {
            lockAndAdvance();
        }
        return true;
    }

    private void lockAndAdvance() {
        LockResult result = Objects.requireNonNull(board.lockAndClearLines(), "lock result");
        if (result.topOut()) {
            gameOver();
            return;
        }
        award(scoring.lineClearPoints(result.clearedLines(), getLevel()));
        clearedLines = addCount(clearedLines, result.clearedLines());
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
        speed = Objects.requireNonNull(speedPolicy.calculate(generatedBlocks, clearedLines), "speed");
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
        timer.stop();
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

package com.team.tetris.game;

import com.team.tetris.common.constants.GameConstants;
import com.team.tetris.common.events.BoardSnapshot;
import com.team.tetris.common.events.GameEventListener;
import com.team.tetris.settings.KeyBindings;

import javax.swing.SwingUtilities;
import javax.swing.Timer;
import java.util.Objects;

/**
 * Game coordinator. Invoke lifecycle/input methods on the Swing event dispatch thread
 * (EDT); timer callbacks and UI notifications also run there. Disk I/O belongs outside
 * this class. See docs/person2-integration.md for the block adapter contract.
 */
public final class GameEngine implements AutoCloseable {
    public static final int BLOCKS_PER_LEVEL = 10;
    public static final int LINES_PER_LEVEL = 10;
    public static final int MAX_LEVEL = 10;
    public static final int MIN_DROP_INTERVAL_MS = 100;
    public static final int SPEED_STEP_MS = 100;

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
    private final ScoreCalculator scoring;
    private final DropTimer timer;
    private GameState state = GameState.READY;
    private int score;
    private int level = 1;
    private int generatedBlocks;
    private int clearedLines;
    private long timerGeneration;

    public GameEngine(BoardDriver board, GameEventListener listener) {
        this(board, listener, new ScoreCalculator(), new SwingDropTimer());
    }

    public GameEngine(BoardDriver board, GameEventListener listener, ScoreCalculator scoring, DropTimer timer) {
        this.board = Objects.requireNonNull(board, "board");
        this.listener = Objects.requireNonNull(listener, "listener");
        this.scoring = Objects.requireNonNull(scoring, "scoring");
        this.timer = Objects.requireNonNull(timer, "timer");
    }

    /** Start a fresh game. Resume a paused game with resume(), not start(). */
    public boolean start() {
        requireEdt();
        if (state == GameState.RUNNING || state == GameState.PAUSED) return false;
        cancelTimer();
        board.reset();
        score = 0;
        level = 1;
        generatedBlocks = 0;
        clearedLines = 0;
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
    public boolean handle(KeyBindings.Action action) {
        requireEdt();
        Objects.requireNonNull(action, "action");
        if (action == KeyBindings.Action.QUIT) return stop();
        if (action == KeyBindings.Action.PAUSE) return state == GameState.PAUSED ? resume() : pause();
        if (state != GameState.RUNNING) return false;
        return switch (action) {
            case MOVE_LEFT -> publishIfMoved(board.moveLeft());
            case MOVE_RIGHT -> publishIfMoved(board.moveRight());
            case ROTATE_CLOCKWISE -> publishIfMoved(board.rotateClockwise());
            case SOFT_DROP -> dropOneCell();
            case HARD_DROP -> {
                award(scoring.dropPoints(board.hardDrop(), level));
                lockAndAdvance();
                yield true;
            }
            default -> false;
        };
    }

    public boolean handleKey(int keyCode, KeyBindings bindings) {
        requireEdt();
        return Objects.requireNonNull(bindings, "bindings").actionFor(keyCode)
                .map(this::handle).orElse(false);
    }

    public GameState getState() { return state; }
    public int getScore() { return score; }
    public int getLevel() { return level; }
    public int getGeneratedBlocks() { return generatedBlocks; }
    public int getClearedLines() { return clearedLines; }

    public int getDropIntervalMillis() {
        return Math.max(MIN_DROP_INTERVAL_MS,
                (int) GameConstants.INITIAL_DROP_INTERVAL_MS - (level - 1) * SPEED_STEP_MS);
    }

    @Override
    public void close() {
        requireEdt();
        stop();
        cancelTimer();
    }

    private boolean dropOneCell() {
        if (board.moveDown()) {
            award(scoring.dropPoints(1, level));
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
        award(scoring.lineClearPoints(result.clearedLines(), level));
        clearedLines = scoring.add(clearedLines, result.clearedLines());
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
        generatedBlocks = scoring.add(generatedBlocks, 1);
        level = Math.min(MAX_LEVEL, 1 + Math.max(generatedBlocks / BLOCKS_PER_LEVEL,
                clearedLines / LINES_PER_LEVEL));
        return true;
    }

    private void gameOver() {
        transition(GameState.GAME_OVER);
        cancelTimer();
        publishBoard();
        listener.onGameOver(score);
    }

    private void award(int points) {
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

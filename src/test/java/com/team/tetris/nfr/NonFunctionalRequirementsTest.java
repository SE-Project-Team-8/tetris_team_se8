package com.team.tetris.nfr;

import com.team.tetris.common.events.BoardSnapshot;
import com.team.tetris.common.events.GameEventListener;
import com.team.tetris.game.BlockBoardDriver;
import com.team.tetris.game.GameEngine;
import org.junit.jupiter.api.Test;

import javax.swing.SwingUtilities;
import java.lang.reflect.InvocationTargetException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertTrue;

class NonFunctionalRequirementsTest {

    @Test
    void firstAutomaticDropOccursApproximatelyOneSecondAfterStarting() throws Exception {
        CountDownLatch firstDrop = new CountDownLatch(1);
        AtomicLong startTime = new AtomicLong();
        AtomicLong firstScoreTime = new AtomicLong();
        GameEventListener listener = new GameEventListener() {
            @Override public void onBoardUpdated(BoardSnapshot snapshot) { }
            @Override public void onScoreChanged(int score) {
                if (score > 0 && firstScoreTime.compareAndSet(0, System.nanoTime())) {
                    firstDrop.countDown();
                }
            }
            @Override public void onNextBlocksChanged(int[] blockTypes) { }
            @Override public void onPauseStateChanged(boolean paused) { }
            @Override public void onGameOver(int score) { }
        };
        GameEngine engine = new GameEngine(new BlockBoardDriver(), listener);

        try {
            runOnEdt(() -> {
                startTime.set(System.nanoTime());
                assertTrue(engine.start());
            });

            assertTrue(firstDrop.await(4, TimeUnit.SECONDS), "Automatic drop did not occur");
            long elapsedMillis = TimeUnit.NANOSECONDS.toMillis(firstScoreTime.get() - startTime.get());
            assertTrue(elapsedMillis >= 900 && elapsedMillis <= 2_500,
                    "Expected the first automatic drop near 1 second, got " + elapsedMillis + " ms");
        } finally {
            runOnEdt(engine::close);
        }
    }

    private static void runOnEdt(Runnable action) throws Exception {
        try {
            SwingUtilities.invokeAndWait(action);
        } catch (InvocationTargetException failure) {
            if (failure.getCause() instanceof AssertionError assertion) throw assertion;
            if (failure.getCause() instanceof Exception exception) throw exception;
            throw failure;
        }
    }
}
package com.team.tetris.ui.theme;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import javax.swing.JPanel;
import javax.swing.SwingUtilities;

import org.junit.jupiter.api.Test;

class PressFeedbackTest {
    @Test
    void pressedButtonShowsThePressedStateBeforeItsActionRuns() throws Exception {
        PressFeedback[] press = new PressFeedback[1];
        AtomicInteger runs = new AtomicInteger();
        CountDownLatch ran = new CountDownLatch(1);
        SwingUtilities.invokeAndWait(() -> {
            press[0] = new PressFeedback(new JPanel());
            press[0].press(1, () -> {
                runs.incrementAndGet();
                ran.countDown();
            });
            assertEquals(UiPainter.ButtonState.PRESSED, press[0].stateOf(1, 1));
            assertEquals(UiPainter.ButtonState.NORMAL, press[0].stateOf(0, 1));
            assertEquals(0, runs.get(), "The action waits until the pressed look has shown");
            // 눌림 표시 중에 다시 눌러도 동작이 두 번 실행되지 않는다.
            press[0].press(1, runs::incrementAndGet);
        });

        assertTrue(ran.await(2, TimeUnit.SECONDS));
        SwingUtilities.invokeAndWait(() -> {
            assertEquals(1, runs.get());
            assertFalse(press[0].isPressing());
            assertEquals(UiPainter.ButtonState.SELECTED, press[0].stateOf(1, 1));
        });
    }

    @Test
    void cancelDropsThePendingAction() throws Exception {
        AtomicInteger runs = new AtomicInteger();
        SwingUtilities.invokeAndWait(() -> {
            PressFeedback press = new PressFeedback(new JPanel());
            press.press(0, runs::incrementAndGet);
            press.cancel();
            assertFalse(press.isPressing());
            assertEquals(UiPainter.ButtonState.SELECTED, press.stateOf(0, 0));
        });
        Thread.sleep(PressFeedback.PRESS_MS * 3L);
        SwingUtilities.invokeAndWait(() -> assertEquals(0, runs.get()));
    }
}

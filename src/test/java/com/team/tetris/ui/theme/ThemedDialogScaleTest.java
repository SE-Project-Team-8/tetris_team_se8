package com.team.tetris.ui.theme;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

import java.awt.GraphicsEnvironment;
import java.lang.reflect.InvocationTargetException;

import javax.swing.JFrame;
import javax.swing.SwingUtilities;

import org.junit.jupiter.api.Test;

/** 팝업 크기는 창 폭과 높이 중 작은 쪽을 따른다. 넓은 대전 창에서 팝업만 두 배로 커지지 않는다. */
class ThemedDialogScaleTest {
    @Test
    void theWideVersusWindowKeepsTheDialogAtTheSinglePlayerSize() throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless(), "Needs a window");
        float[] scales = new float[3];
        edt(() -> {
            JFrame frame = new JFrame();
            try {
                frame.setSize(600, 800);
                scales[0] = ThemedDialog.dialogScale(frame);
                frame.setSize(480, 640);
                scales[1] = ThemedDialog.dialogScale(frame);
                frame.setSize(1280, 800);
                scales[2] = ThemedDialog.dialogScale(frame);
            } finally {
                frame.dispose();
            }
        });
        assertEquals(1f, scales[0], 0.01f, "Large single-player window");
        assertEquals(0.8f, scales[1], 0.01f, "Small single-player window");
        assertEquals(1f, scales[2], 0.01f, "The wide versus window does not double the dialog");
        assertEquals(1f, ThemedDialog.dialogScale(null), 0.01f);
    }

    private static void edt(Runnable action) throws Exception {
        try {
            SwingUtilities.invokeAndWait(action);
        } catch (InvocationTargetException failure) {
            if (failure.getCause() instanceof AssertionError assertion) throw assertion;
            if (failure.getCause() instanceof Exception exception) throw exception;
            throw failure;
        }
    }
}

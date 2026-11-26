package com.team.tetris.ui.theme;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import javax.swing.JPanel;
import javax.swing.SwingUtilities;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** 버튼 선택(클릭) 이펙트: ② 눌림 → ③ 확정 플래시(크림 테두리 + ✦) → 동작 실행. 직접 그리는 메뉴와 Swing 버튼 모두. */
class ButtonConfirmFlashTest {
    @BeforeEach
    void realDurations() {
        PressFeedback.setPressDurationForTests(PressFeedback.PRESS_MS);
    }

    @AfterEach
    void restore() {
        PressFeedback.setPressDurationForTests(PressFeedback.PRESS_MS);
    }

    @Test
    void aDrawnMenuButtonIsPressedThenFlashesThenRunsItsAction() throws Exception {
        // 상태가 바뀔 때마다 화면을 다시 그리므로, 다시 그릴 때의 상태를 차례로 적는다 (기기 속도와 상관없다).
        List<String> seen = new ArrayList<>();
        CountDownLatch ran = new CountDownLatch(1);
        PressFeedback[] press = new PressFeedback[1];
        SwingUtilities.invokeAndWait(() -> {
            JPanel screen = new JPanel() {
                @Override public void repaint() {
                    if (press != null && press[0] != null) seen.add(press[0].stateOf(2, 2).name());
                }
            };
            press[0] = new PressFeedback(screen);
            press[0].press(2, () -> {
                seen.add("ACTION");
                ran.countDown();
            });
        });
        assertTrue(ran.await(2, TimeUnit.SECONDS));
        SwingUtilities.invokeAndWait(() -> assertEquals(
                List.of("PRESSED", "CONFIRMED", "SELECTED", "ACTION"), seen,
                "② press, then ③ the confirm flash, then the action"));
    }

    @Test
    void aSwingButtonFlashesBeforeItsActionAndRunsItOnce() throws Exception {
        CountDownLatch ran = new CountDownLatch(1);
        int[] runs = {0};
        PillButton[] button = new PillButton[1];
        SwingUtilities.invokeAndWait(() -> {
            button[0] = new PillButton("저장", true);
            button[0].setSize(120, 44);
            button[0].addActionListener(event -> {
                runs[0]++;
                ran.countDown();
            });
            button[0].doClick(0);
            button[0].doClick(0);   // 플래시 중에 다시 눌러도
            assertEquals(0, runs[0], "The action waits for the confirm flash");
            paint(button[0]);
        });
        assertTrue(ran.await(2, TimeUnit.SECONDS));
        Thread.sleep(PressFeedback.CONFIRM_MS);
        SwingUtilities.invokeAndWait(() -> assertEquals(1, runs[0], "Pressing during the flash does not run it twice"));
    }

    @Test
    void testsThatSkipThePressAlsoSkipTheFlash() throws Exception {
        PressFeedback.setPressDurationForTests(0);
        int[] runs = {0};
        SwingUtilities.invokeAndWait(() -> {
            PillButton button = new PillButton("확인", false);
            button.addActionListener(event -> runs[0]++);
            button.doClick(0);
            assertEquals(1, runs[0]);
        });
    }

    @Test
    void theConfirmedStateDrawsACreamBorder() {
        BufferedImage image = new BufferedImage(200, 80, BufferedImage.TYPE_INT_RGB);
        var g = image.createGraphics();
        UiPainter.drawPillButton(g, 20, 14, 160, 48, "시작", UiPainter.ButtonState.CONFIRMED, true, 18f);
        g.dispose();
        // 왼쪽 가장자리 세로 가운데의 테두리 색이 크림이다.
        int rgb = image.getRGB(20 + 1, 14 + 24) & 0xFFFFFF;
        int cream = UiColors.CREAM.getRGB() & 0xFFFFFF;
        assertTrue(Math.abs(((rgb >> 16) & 0xFF) - ((cream >> 16) & 0xFF)) < 40
                && Math.abs(((rgb >> 8) & 0xFF) - ((cream >> 8) & 0xFF)) < 40, Integer.toHexString(rgb));
    }

    private static void paint(PillButton button) {
        BufferedImage image = new BufferedImage(button.getWidth(), button.getHeight(), BufferedImage.TYPE_INT_ARGB);
        var g = image.createGraphics();
        button.paint(g);
        g.dispose();
    }
}

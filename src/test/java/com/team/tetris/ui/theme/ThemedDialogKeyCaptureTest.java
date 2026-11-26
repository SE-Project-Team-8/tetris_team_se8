package com.team.tetris.ui.theme;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

import java.awt.Component;
import java.awt.Container;
import java.awt.GraphicsEnvironment;
import java.awt.Window;
import java.awt.event.KeyEvent;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.SwingUtilities;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** 설정의 "키 변경" 팝업: 다음에 누른 키를 돌려주고, Esc는 취소, 팝업을 연 키의 자동 반복과 겹치는 키는 받지 않는다. */
class ThemedDialogKeyCaptureTest {
    @BeforeEach
    void requireDisplay() {
        assumeFalse(GraphicsEnvironment.isHeadless(), "The popup is a window");
    }

    @Test
    void thePressedKeyIsReturnedWithItsSideAndTabIsAKeyToo() throws Exception {
        CompletableFuture<ThemedDialog.KeyPress> result = open(KeyEvent.VK_UNDEFINED, press -> null);
        send(KeyEvent.KEY_PRESSED, KeyEvent.VK_TAB, KeyEvent.KEY_LOCATION_STANDARD);
        assertEquals(new ThemedDialog.KeyPress(KeyEvent.VK_TAB, KeyEvent.KEY_LOCATION_STANDARD), result.get(5, TimeUnit.SECONDS));

        result = open(KeyEvent.VK_UNDEFINED, press -> null);
        send(KeyEvent.KEY_PRESSED, KeyEvent.VK_SHIFT, KeyEvent.KEY_LOCATION_RIGHT);
        assertEquals(new ThemedDialog.KeyPress(KeyEvent.VK_SHIFT, KeyEvent.KEY_LOCATION_RIGHT), result.get(5, TimeUnit.SECONDS));
    }

    @Test
    void escapeCancels() throws Exception {
        CompletableFuture<ThemedDialog.KeyPress> result = open(KeyEvent.VK_UNDEFINED, press -> null);
        send(KeyEvent.KEY_PRESSED, KeyEvent.VK_ESCAPE, KeyEvent.KEY_LOCATION_STANDARD);
        assertNull(result.get(5, TimeUnit.SECONDS));
    }

    @Test
    void theHeldOpeningKeyIsIgnoredUntilItIsReleased() throws Exception {
        CompletableFuture<ThemedDialog.KeyPress> result = open(KeyEvent.VK_ENTER, press -> null);
        send(KeyEvent.KEY_PRESSED, KeyEvent.VK_ENTER, KeyEvent.KEY_LOCATION_STANDARD);   // 자동 반복
        assertTrue(!result.isDone(), "Holding Enter does not bind Enter");
        send(KeyEvent.KEY_RELEASED, KeyEvent.VK_ENTER, KeyEvent.KEY_LOCATION_STANDARD);
        send(KeyEvent.KEY_PRESSED, KeyEvent.VK_ENTER, KeyEvent.KEY_LOCATION_STANDARD);
        assertEquals(KeyEvent.VK_ENTER, result.get(5, TimeUnit.SECONDS).keyCode(), "A fresh Enter press is a key");
    }

    @Test
    void aRejectedKeyShowsWhyAndThePopupKeepsWaiting() throws Exception {
        CompletableFuture<ThemedDialog.KeyPress> result = open(KeyEvent.VK_UNDEFINED,
                press -> press.keyCode() == KeyEvent.VK_SPACE ? "이미 '하드 드롭'에 쓰이는 키입니다" : null);
        send(KeyEvent.KEY_PRESSED, KeyEvent.VK_SPACE, KeyEvent.KEY_LOCATION_STANDARD);
        AtomicReference<String> shown = new AtomicReference<>("");
        SwingUtilities.invokeAndWait(() -> shown.set(texts(dialog())));
        assertTrue(shown.get().contains("하드 드롭"), shown.get());
        assertTrue(!result.isDone());
        send(KeyEvent.KEY_PRESSED, KeyEvent.VK_K, KeyEvent.KEY_LOCATION_STANDARD);
        assertEquals(KeyEvent.VK_K, result.get(5, TimeUnit.SECONDS).keyCode());
    }

    private static CompletableFuture<ThemedDialog.KeyPress> open(int openingKey,
            java.util.function.Function<ThemedDialog.KeyPress, String> problem) throws Exception {
        CompletableFuture<ThemedDialog.KeyPress> result = new CompletableFuture<>();
        SwingUtilities.invokeLater(() -> result.complete(
                ThemedDialog.captureKey(null, "키 변경", "'왼쪽 이동'에 쓸 키를 누르세요\nEsc 취소", openingKey, problem)));
        long deadline = System.currentTimeMillis() + 5_000;
        while (System.currentTimeMillis() < deadline) {
            JDialog[] found = {null};
            SwingUtilities.invokeAndWait(() -> found[0] = dialog());
            if (found[0] != null) return result;
            Thread.sleep(20);
        }
        throw new AssertionError("The popup did not open");
    }

    /** 팝업 카드(키를 받는 곳)에 키 이벤트를 보낸다. */
    private static void send(int id, int keyCode, int location) throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            Component card = dialog().getContentPane();
            KeyEvent event = new KeyEvent(card, id, System.currentTimeMillis(), 0, keyCode, KeyEvent.CHAR_UNDEFINED,
                    location);
            card.dispatchEvent(event);
        });
    }

    private static JDialog dialog() {
        for (Window window : Window.getWindows()) {
            if (window instanceof JDialog dialog && dialog.isShowing()) return dialog;
        }
        return null;
    }

    private static String texts(Container root) {
        StringBuilder text = new StringBuilder();
        for (Component child : root.getComponents()) {
            if (child instanceof JLabel label) text.append(label.getText());
            if (child instanceof Container container) text.append(texts(container));
        }
        return text.toString();
    }
}

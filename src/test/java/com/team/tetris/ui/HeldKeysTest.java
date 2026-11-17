package com.team.tetris.ui;

import org.junit.jupiter.api.Test;

import javax.swing.JPanel;
import java.awt.event.KeyEvent;

import static org.junit.jupiter.api.Assertions.*;

class HeldKeysTest {
    private final JPanel source = new JPanel();
    private final HeldKeys keys = new HeldKeys();

    @Test
    void aPressOfAKeyThatIsAlreadyDownIsAnAutoRepeatThatStillReachesTheSameScreen() {
        assertFalse(press(KeyEvent.VK_SPACE), "Nothing is swallowed without a screen change");
        assertFalse(keys.lastPressRepeated());
        assertFalse(press(KeyEvent.VK_SPACE));
        assertTrue(keys.lastPressRepeated());
        release(KeyEvent.VK_SPACE);
        press(KeyEvent.VK_SPACE);
        assertFalse(keys.lastPressRepeated(), "Pressing again after releasing is a new press");
    }

    @Test
    void repeatsOfAKeyHeldAcrossAScreenChangeAreSwallowedUntilItIsReleased() {
        press(KeyEvent.VK_ENTER);
        press(KeyEvent.VK_LEFT);
        keys.markHeldKeys();
        assertTrue(press(KeyEvent.VK_ENTER), "The held Enter does not reach the new screen");
        assertTrue(typed('\n'), "Neither does the character it would type");
        assertFalse(press(KeyEvent.VK_SPACE), "A key pressed on the new screen reaches it");
        assertFalse(typed(' '));
        assertTrue(press(KeyEvent.VK_LEFT));
        release(KeyEvent.VK_ENTER);
        assertFalse(press(KeyEvent.VK_ENTER), "A new press after releasing reaches the new screen");
        assertFalse(press(KeyEvent.VK_ENTER), "Its own repeats belong to the new screen");
        assertTrue(keys.lastPressRepeated());
    }

    private boolean press(int keyCode) {
        return keys.dispatchKeyEvent(new KeyEvent(source, KeyEvent.KEY_PRESSED, 0, 0, keyCode,
                KeyEvent.CHAR_UNDEFINED));
    }

    private boolean typed(char character) {
        return keys.dispatchKeyEvent(new KeyEvent(source, KeyEvent.KEY_TYPED, 0, 0, KeyEvent.VK_UNDEFINED,
                character));
    }

    private void release(int keyCode) {
        keys.dispatchKeyEvent(new KeyEvent(source, KeyEvent.KEY_RELEASED, 0, 0, keyCode,
                KeyEvent.CHAR_UNDEFINED));
    }
}

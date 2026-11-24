/*
 * SidedKey 테스트: 키 이벤트의 위치로 왼쪽·오른쪽을 가르고, 쪽이 없는 키는 키 코드 그대로이며, 겹침은 같은 키 코드에서 쪽이
 * 같거나 한쪽이 ANY일 때다 (D-13).
 */
package com.team.tetris.game.versus;

import org.junit.jupiter.api.Test;

import java.awt.event.KeyEvent;

import static org.junit.jupiter.api.Assertions.*;

class SidedKeyTest {
    @Test
    void onlyLeftAndRightLocationsKeepTheirSide() {
        assertEquals(SidedKey.left(KeyEvent.VK_SHIFT), SidedKey.of(KeyEvent.VK_SHIFT, KeyEvent.KEY_LOCATION_LEFT));
        assertEquals(SidedKey.right(KeyEvent.VK_SHIFT), SidedKey.of(KeyEvent.VK_SHIFT, KeyEvent.KEY_LOCATION_RIGHT));
        assertEquals(KeyEvent.VK_A, SidedKey.of(KeyEvent.VK_A, KeyEvent.KEY_LOCATION_STANDARD), "a plain key is its key code");
        assertEquals(KeyEvent.VK_ENTER, SidedKey.of(KeyEvent.VK_ENTER, KeyEvent.KEY_LOCATION_NUMPAD), "numpad Enter is Enter");
    }

    @Test
    void theKeyCodeAndSideCanBeReadBack() {
        int right = SidedKey.right(KeyEvent.VK_CONTROL);
        assertEquals(KeyEvent.VK_CONTROL, SidedKey.keyCode(right));
        assertEquals(SidedKey.RIGHT, SidedKey.side(right));
        assertEquals(SidedKey.ANY, SidedKey.side(KeyEvent.VK_UP));
    }

    @Test
    void keysOverlapWhenTheSameKeyCouldMatchBoth() {
        int left = SidedKey.left(KeyEvent.VK_SHIFT);
        int right = SidedKey.right(KeyEvent.VK_SHIFT);
        assertFalse(SidedKey.overlaps(left, right));
        assertTrue(SidedKey.overlaps(left, KeyEvent.VK_SHIFT));
        assertTrue(SidedKey.overlaps(KeyEvent.VK_SHIFT, right));
        assertTrue(SidedKey.overlaps(left, left));
        assertFalse(SidedKey.overlaps(left, SidedKey.left(KeyEvent.VK_CONTROL)));
    }

    @Test
    void invalidKeysAreRejected() {
        assertTrue(SidedKey.isValid(SidedKey.left(KeyEvent.VK_ALT)));
        assertFalse(SidedKey.isValid(KeyEvent.VK_UNDEFINED));
        assertFalse(SidedKey.isValid(KeyEvent.VK_A | 7 << 16));
        assertThrows(IllegalArgumentException.class, () -> SidedKey.left(KeyEvent.VK_UNDEFINED));
        assertThrows(IllegalArgumentException.class, () -> SidedKey.of(0x10000, KeyEvent.KEY_LOCATION_STANDARD));
    }
}

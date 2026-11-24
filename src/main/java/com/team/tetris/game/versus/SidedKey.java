/*
 * 대전 키 값: 키 코드에 왼쪽·오른쪽 구분을 더한 정수. 왼쪽 Shift는 P1, 오른쪽 Shift는 P2처럼 쓴다 (D-13).
 */
package com.team.tetris.game.versus;

import java.awt.event.KeyEvent;

/**
 * 대전 키 하나를 정수 하나로 나타낸다. 아래 16비트는 키 코드, 그 위는 쪽({@link #ANY}, {@link #LEFT}, {@link #RIGHT})이다.
 *
 * Shift·Ctrl·Alt처럼 키보드 양쪽에 있는 키는 같은 키 코드로 오고 {@link KeyEvent#getKeyLocation()}으로만 구분된다.
 * 쪽을 정한 키는 그쪽에서 눌렸을 때만, {@link #ANY} 키는 어느 쪽에서 눌려도 맞는다. 쪽이 없는 키(문자, 방향키)는 항상
 * {@link #ANY}라서 값이 키 코드와 같다. 1인용 {@code KeyBindings}의 키 코드도 그대로 {@link #ANY} 키로 읽힌다.
 */
public final class SidedKey {
    /** 쪽을 가리지 않는다. */
    public static final int ANY = 0;
    /** 키보드 왼쪽. {@link KeyEvent#KEY_LOCATION_LEFT}와 같은 값이다. */
    public static final int LEFT = KeyEvent.KEY_LOCATION_LEFT;
    /** 키보드 오른쪽. {@link KeyEvent#KEY_LOCATION_RIGHT}와 같은 값이다. */
    public static final int RIGHT = KeyEvent.KEY_LOCATION_RIGHT;

    private static final int CODE_BITS = 16;
    private static final int CODE_MASK = (1 << CODE_BITS) - 1;

    private SidedKey() { }

    /** 창에서 받은 키 이벤트의 키 코드와 위치로 만든다. 왼쪽·오른쪽이 아닌 위치(일반, 숫자 패드)는 {@link #ANY}다. */
    public static int of(int keyCode, int location) {
        int side = location == LEFT || location == RIGHT ? location : ANY;
        return make(keyCode, side);
    }

    /** 키보드 왼쪽의 그 키. 예: {@code left(KeyEvent.VK_SHIFT)} */
    public static int left(int keyCode) {
        return make(keyCode, LEFT);
    }

    /** 키보드 오른쪽의 그 키. */
    public static int right(int keyCode) {
        return make(keyCode, RIGHT);
    }

    public static int keyCode(int key) {
        return key & CODE_MASK;
    }

    public static int side(int key) {
        return key >>> CODE_BITS;
    }

    /** 키 코드가 있고 쪽이 셋 중 하나인 값인지. */
    public static boolean isValid(int key) {
        int side = side(key);
        return keyCode(key) > KeyEvent.VK_UNDEFINED && (side == ANY || side == LEFT || side == RIGHT);
    }

    /** 두 키가 같은 누름에 함께 맞을 수 있는지. 같은 키 코드이고, 쪽이 같거나 한쪽이 {@link #ANY}이면 겹친다. */
    public static boolean overlaps(int a, int b) {
        return keyCode(a) == keyCode(b) && (side(a) == side(b) || side(a) == ANY || side(b) == ANY);
    }

    private static int make(int keyCode, int side) {
        if (keyCode <= KeyEvent.VK_UNDEFINED || keyCode > CODE_MASK) {
            throw new IllegalArgumentException("Invalid key code: " + keyCode);
        }
        return keyCode | side << CODE_BITS;
    }
}

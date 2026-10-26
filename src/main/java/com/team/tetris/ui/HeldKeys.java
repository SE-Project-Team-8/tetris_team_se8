package com.team.tetris.ui;

import java.awt.KeyEventDispatcher;
import java.awt.KeyboardFocusManager;
import java.awt.event.KeyEvent;
import java.util.HashSet;
import java.util.Set;

/**
 * 키를 누르고 있을 때 OS가 보내는 자동 반복 KEY_PRESSED를 가려낸다.
 * Windows·macOS는 자동 반복 때 KEY_RELEASED 없이 KEY_PRESSED만 다시 보내므로, 이미 눌려 있는 키의
 * KEY_PRESSED를 자동 반복으로 본다. 화면의 키 처리보다 먼저 모든 키 이벤트를 보도록 디스패처로 등록한다.
 *
 * <p>화면이 바뀔 때 눌려 있던 키는 뗄 때까지 자동 반복을 새 화면에 전달하지 않는다. 앞 화면에서 누른 키가
 * 새 화면의 선택·재개·제출 키로 처리되지 않게 하려는 것이다. 같은 화면 안의 자동 반복은 그대로 전달하므로
 * 좌우·아래 이동과 회전은 누르고 있으면 계속 반복되고, 하드 드롭처럼 한 번만 일어나야 하는 동작은
 * 그 화면이 {@link #isAutoRepeat()}로 걸러 낸다.
 */
final class HeldKeys implements KeyEventDispatcher {
    private static final HeldKeys INSTANCE = new HeldKeys();
    private static boolean installed;

    private final Set<Integer> down = new HashSet<>();
    private final Set<Integer> heldAcrossScreenChange = new HashSet<>();
    private boolean lastPressRepeated;
    private boolean swallowTyped;

    HeldKeys() { }

    /** 화면 창(ScreenRouter)을 만들 때 부른다. 새 창에서는 이전에 눌린 키 기록을 버린다. */
    static void install() {
        if (!installed) {
            installed = true;
            KeyboardFocusManager manager = KeyboardFocusManager.getCurrentKeyboardFocusManager();
            manager.addKeyEventDispatcher(INSTANCE);
            // 다른 창에 포커스가 있는 동안 키를 떼면 KEY_RELEASED가 오지 않으므로 기록을 비운다.
            manager.addPropertyChangeListener("activeWindow", event -> INSTANCE.clear());
        }
        INSTANCE.clear();
    }

    /** ScreenRouter가 화면을 바꿀 때 부른다. */
    static void screenChanged() {
        INSTANCE.markHeldKeys();
    }

    /** 지금 처리 중인 KEY_PRESSED가 누르고 있던 키의 자동 반복이면 true. */
    static boolean isAutoRepeat() {
        return INSTANCE.lastPressRepeated();
    }

    boolean lastPressRepeated() {
        return lastPressRepeated;
    }

    void markHeldKeys() {
        heldAcrossScreenChange.addAll(down);
    }

    /** true를 돌려주면 그 이벤트는 어느 화면에도 전달되지 않는다. */
    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        switch (event.getID()) {
            case KeyEvent.KEY_PRESSED -> {
                int keyCode = event.getKeyCode();
                lastPressRepeated = !down.add(keyCode);
                // 자동 반복 KEY_PRESSED 뒤에 오는 KEY_TYPED(입력칸에 들어갈 글자)도 같이 막는다.
                swallowTyped = lastPressRepeated && heldAcrossScreenChange.contains(keyCode);
                return swallowTyped;
            }
            case KeyEvent.KEY_TYPED -> {
                boolean swallow = swallowTyped;
                swallowTyped = false;
                return swallow;
            }
            case KeyEvent.KEY_RELEASED -> {
                down.remove(event.getKeyCode());
                heldAcrossScreenChange.remove(event.getKeyCode());
                return false;
            }
            default -> {
                return false;
            }
        }
    }

    private void clear() {
        down.clear();
        heldAcrossScreenChange.clear();
        lastPressRepeated = false;
        swallowTyped = false;
    }
}

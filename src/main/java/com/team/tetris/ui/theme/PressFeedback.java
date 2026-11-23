package com.team.tetris.ui.theme;

import java.awt.Component;
import java.util.Objects;

import javax.swing.Timer;

/**
 * 직접 그리는 메뉴 버튼(시작 메뉴, 게임 오버, 일시정지)에 눌림 모습을 잠깐 보여 준 뒤 동작을 실행한다.
 * Swing 버튼({@link PillButton})과 같은 눌림 피드백을 주기 위한 것이다 (디자인 스펙: 클릭·Enter 직후 80ms).
 */
public final class PressFeedback {
    /** 눌린 모습을 유지하는 시간(ms). */
    public static final int PRESS_MS = 80;

    /** 실제로 쓰는 눌림 시간. 테스트가 키 입력 직후 화면 전환을 바로 확인할 수 있게 바꿀 수 있다. */
    private static volatile int pressMs = PRESS_MS;

    private final Component owner;
    private final Timer timer;
    private int pressedIndex = -1;
    private Runnable pending;

    /**
     * @param owner 눌림 상태가 바뀔 때 다시 그릴 화면
     */
    public PressFeedback(Component owner) {
        this.owner = Objects.requireNonNull(owner, "owner");
        timer = new Timer(PRESS_MS, event -> release());
        timer.setRepeats(false);
    }

    /**
     * {@code index} 버튼을 눌린 모습으로 그리고, {@link #PRESS_MS} 뒤에 {@code action}을 실행한다.
     * 이미 눌림 표시 중이면 무시해 같은 동작이 두 번 실행되지 않게 한다.
     */
    public void press(int index, Runnable action) {
        if (isPressing()) return;
        pressedIndex = index;
        pending = Objects.requireNonNull(action, "action");
        if (pressMs <= 0) {
            release();
            return;
        }
        owner.repaint();
        timer.setInitialDelay(pressMs);
        timer.restart();
    }

    /**
     * 눌림 시간을 바꾼다. 0 이하이면 눌림 표시 없이 바로 실행한다. 테스트 전용이다.
     *
     * @param millis 눌림 시간(ms). 원래대로 돌리려면 {@link #PRESS_MS}를 넘긴다.
     */
    public static void setPressDurationForTests(int millis) {
        pressMs = millis;
    }

    /** 눌림 표시 중이면 true. 이 동안에는 화면이 다른 키 입력을 무시한다. */
    public boolean isPressing() {
        return pending != null;
    }

    /** 실행하지 않고 눌림 표시를 취소한다. 화면이 숨겨질 때 호출한다. */
    public void cancel() {
        timer.stop();
        pending = null;
        pressedIndex = -1;
        owner.repaint();
    }

    /**
     * 버튼의 표시 상태. 눌린 버튼이면 PRESSED, 아니면 선택 여부에 따라 SELECTED/NORMAL.
     */
    public UiPainter.ButtonState stateOf(int index, int selectedIndex) {
        if (index == pressedIndex) return UiPainter.ButtonState.PRESSED;
        return index == selectedIndex ? UiPainter.ButtonState.SELECTED : UiPainter.ButtonState.NORMAL;
    }

    private void release() {
        Runnable action = pending;
        pending = null;
        pressedIndex = -1;
        owner.repaint();
        if (action != null) action.run();
    }
}

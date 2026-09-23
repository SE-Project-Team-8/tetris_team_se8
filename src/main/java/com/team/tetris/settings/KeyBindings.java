package com.team.tetris.settings;

import java.awt.event.KeyEvent;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** Immutable key-code mapping shared by settings UI and the input controller. */
public record KeyBindings(Map<Action, Integer> keys) {
    public enum Action { MOVE_LEFT, MOVE_RIGHT, SOFT_DROP, ROTATE_CLOCKWISE, HARD_DROP, PAUSE, QUIT }

    public KeyBindings {
        Objects.requireNonNull(keys, "keys");
        EnumMap<Action, Integer> copy = new EnumMap<>(Action.class);
        copy.putAll(keys);
        if (copy.size() != Action.values().length) {
            throw new IllegalArgumentException("Every action needs a key");
        }
        HashSet<Integer> used = new HashSet<>();
        for (Integer key : copy.values()) {
            if (key == null || key <= KeyEvent.VK_UNDEFINED || key > 0xFFFF || !used.add(key)) {
                throw new IllegalArgumentException("Keys must be valid and unique");
            }
        }
        keys = Map.copyOf(copy);
    }

    public static KeyBindings defaults() {
        return new KeyBindings(Map.of(
                Action.MOVE_LEFT, KeyEvent.VK_LEFT,
                Action.MOVE_RIGHT, KeyEvent.VK_RIGHT,
                Action.SOFT_DROP, KeyEvent.VK_DOWN,
                Action.ROTATE_CLOCKWISE, KeyEvent.VK_UP,
                Action.HARD_DROP, KeyEvent.VK_SPACE,
                Action.PAUSE, KeyEvent.VK_P,
                Action.QUIT, KeyEvent.VK_ESCAPE));
    }

    public int keyFor(Action action) {
        return keys.get(Objects.requireNonNull(action, "action"));
    }

    public Optional<Action> actionFor(int keyCode) {
        return keys.entrySet().stream().filter(e -> e.getValue() == keyCode)
                .map(Map.Entry::getKey).findFirst();
    }

    /** A duplicate key is rejected; use the constructor to swap multiple keys at once. */
    public KeyBindings withKey(Action action, int keyCode) {
        EnumMap<Action, Integer> changed = new EnumMap<>(keys);
        changed.put(Objects.requireNonNull(action, "action"), keyCode);
        return new KeyBindings(changed);
    }
}

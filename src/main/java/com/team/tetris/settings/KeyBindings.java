package com.team.tetris.settings;

import com.team.tetris.game.GameAction;

import java.awt.event.KeyEvent;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** Immutable key-code mapping shared by settings UI and the input controller. */
public record KeyBindings(Map<GameAction, Integer> keys) {

    public KeyBindings {
        Objects.requireNonNull(keys, "keys");
        EnumMap<GameAction, Integer> copy = new EnumMap<>(GameAction.class);
        copy.putAll(keys);
        if (copy.size() != GameAction.values().length) {
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
                GameAction.MOVE_LEFT, KeyEvent.VK_LEFT,
                GameAction.MOVE_RIGHT, KeyEvent.VK_RIGHT,
                GameAction.SOFT_DROP, KeyEvent.VK_DOWN,
                GameAction.ROTATE_CLOCKWISE, KeyEvent.VK_UP,
                GameAction.HARD_DROP, KeyEvent.VK_SPACE,
                GameAction.PAUSE, KeyEvent.VK_P,
                GameAction.QUIT, KeyEvent.VK_ESCAPE));
    }

    public int keyFor(GameAction action) {
        return keys.get(Objects.requireNonNull(action, "action"));
    }

    public Optional<GameAction> actionFor(int keyCode) {
        return keys.entrySet().stream().filter(e -> e.getValue() == keyCode)
                .map(Map.Entry::getKey).findFirst();
    }

    /** A duplicate key is rejected; use the constructor to swap multiple keys at once. */
    public KeyBindings withKey(GameAction action, int keyCode) {
        EnumMap<GameAction, Integer> changed = new EnumMap<>(keys);
        changed.put(Objects.requireNonNull(action, "action"), keyCode);
        return new KeyBindings(changed);
    }
}

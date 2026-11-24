/*
 * 로컬 대전의 P1·P2 조작 키. 1인용 KeyBindings와 따로 저장하고, 일시정지·나가기는 1인용 키를 함께 쓴다 (docs/battle/input.md).
 */
package com.team.tetris.settings;

import com.team.tetris.common.battle.Player;
import com.team.tetris.game.GameAction;
import com.team.tetris.game.versus.PlayerMove;
import com.team.tetris.game.versus.SidedKey;

import java.awt.event.KeyEvent;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * 대전 키 열 개: 플레이어마다 {@link #PLAYER_ACTIONS}의 다섯 동작에 키 코드 하나씩 (LB-I11, D-11, D-13).
 *
 * 키 값은 {@link SidedKey}다. 키 코드에 왼쪽·오른쪽 구분을 더한 정수라서, 기본값처럼 왼쪽 Shift는 P1, 오른쪽 Shift는
 * P2에 줄 수 있다. 쪽을 정하지 않은 키는 어느 쪽에서 눌려도 맞는다.
 *
 * 열 개의 키는 서로 겹치지 않아야 한다({@link SidedKey#overlaps}). 1인용 키와는 겹쳐도 된다(기본값부터 P2 방향키가
 * 1인용과 같다). 일시정지·나가기 키와 겹치지 않아야 한다는 규칙은 두 키 묶음을 함께 가진 {@link GameSettings}가 맡는다.
 *
 * @param p1 P1의 동작별 키
 * @param p2 P2의 동작별 키
 */
public record VersusKeyBindings(Map<GameAction, Integer> p1, Map<GameAction, Integer> p2) {
    /** 플레이어마다 키를 정하는 동작. 순서는 설정 화면에 늘어놓는 순서다. */
    public static final Set<GameAction> PLAYER_ACTIONS = Collections.unmodifiableSet(EnumSet.of(
            GameAction.MOVE_LEFT, GameAction.MOVE_RIGHT, GameAction.SOFT_DROP,
            GameAction.ROTATE_CLOCKWISE, GameAction.HARD_DROP));

    /** 두 사람이 함께 쓰는 동작. 1인용 {@link KeyBindings}의 키를 쓴다. */
    public static final Set<GameAction> SHARED_ACTIONS = Collections.unmodifiableSet(EnumSet.of(
            GameAction.PAUSE, GameAction.QUIT));

    public VersusKeyBindings {
        p1 = copyOf(p1, "p1");
        p2 = copyOf(p2, "p2");
        List<Integer> used = new ArrayList<>();
        for (Map<GameAction, Integer> keys : List.of(p1, p2)) {
            for (int key : keys.values()) {
                if (used.stream().anyMatch(other -> SidedKey.overlaps(key, other))) {
                    throw new IllegalArgumentException("Versus keys must not overlap: " + key);
                }
                used.add(key);
            }
        }
    }

    /** 기본값: P1은 키보드 왼쪽(A·D·S·W·왼쪽 Shift), P2는 오른쪽(방향키·오른쪽 Shift). input.md 1절의 표. */
    public static VersusKeyBindings defaults() {
        return new VersusKeyBindings(
                Map.of(GameAction.MOVE_LEFT, KeyEvent.VK_A,
                        GameAction.MOVE_RIGHT, KeyEvent.VK_D,
                        GameAction.SOFT_DROP, KeyEvent.VK_S,
                        GameAction.ROTATE_CLOCKWISE, KeyEvent.VK_W,
                        GameAction.HARD_DROP, SidedKey.left(KeyEvent.VK_SHIFT)),
                Map.of(GameAction.MOVE_LEFT, KeyEvent.VK_LEFT,
                        GameAction.MOVE_RIGHT, KeyEvent.VK_RIGHT,
                        GameAction.SOFT_DROP, KeyEvent.VK_DOWN,
                        GameAction.ROTATE_CLOCKWISE, KeyEvent.VK_UP,
                        GameAction.HARD_DROP, SidedKey.right(KeyEvent.VK_SHIFT)));
    }

    /** 그 플레이어의 동작별 키. 읽기 전용이다. */
    public Map<GameAction, Integer> keys(Player player) {
        return Objects.requireNonNull(player, "player") == Player.P1 ? p1 : p2;
    }

    /** 그 플레이어가 그 동작에 쓰는 키. 화면의 키 안내에도 쓴다. */
    public int keyFor(Player player, GameAction action) {
        Integer key = keys(player).get(Objects.requireNonNull(action, "action"));
        if (key == null) throw new IllegalArgumentException("Not a per-player action: " + action);
        return key;
    }

    /** 그 키와 겹치는 대전 키의 조작. 없으면 비어 있다. 쪽이 없는 키(예: 1인용 키 코드)는 양쪽 키 모두와 겹친다. */
    public Optional<PlayerMove> moveFor(int key) {
        for (Player player : Player.values()) {
            for (Map.Entry<GameAction, Integer> entry : keys(player).entrySet()) {
                if (SidedKey.overlaps(entry.getValue(), key)) return Optional.of(new PlayerMove(player, entry.getKey()));
            }
        }
        return Optional.empty();
    }

    /** 열 개의 키 중 그 키와 겹치는 것이 있는지. */
    public boolean uses(int key) {
        return moveFor(key).isPresent();
    }

    /** 키 하나를 바꾼 새 값({@link SidedKey}). 다른 대전 키와 겹치면 거부한다. 두 키를 맞바꾸려면 생성자를 쓴다. */
    public VersusKeyBindings withKey(Player player, GameAction action, int keyCode) {
        Objects.requireNonNull(player, "player");
        EnumMap<GameAction, Integer> changed = new EnumMap<>(keys(player));
        if (!changed.containsKey(Objects.requireNonNull(action, "action"))) {
            throw new IllegalArgumentException("Not a per-player action: " + action);
        }
        changed.put(action, keyCode);
        return player == Player.P1 ? new VersusKeyBindings(changed, p2) : new VersusKeyBindings(p1, changed);
    }

    private static Map<GameAction, Integer> copyOf(Map<GameAction, Integer> keys, String name) {
        Objects.requireNonNull(keys, name);
        EnumMap<GameAction, Integer> copy = new EnumMap<>(GameAction.class);
        copy.putAll(keys);
        if (!copy.keySet().equals(PLAYER_ACTIONS)) {
            throw new IllegalArgumentException(name + " needs exactly the actions " + PLAYER_ACTIONS + ": "
                    + copy.keySet());
        }
        for (Integer key : copy.values()) {
            if (key == null || !SidedKey.isValid(key)) {
                throw new IllegalArgumentException("Invalid key in " + name + ": " + key);
            }
        }
        return Collections.unmodifiableMap(copy);
    }
}

package com.team.tetris.settings;

import com.team.tetris.common.Difficulty;
import com.team.tetris.common.battle.Player;
import com.team.tetris.game.GameAction;
import com.team.tetris.game.versus.PlayerMove;

import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Persisted preferences. Window sizes never change the 20 x 10 board dimensions.
 *
 * @param versusKeys 로컬 대전의 P1·P2 키. 일시정지·나가기는 {@code keyBindings}의 키를 함께 쓴다 (D-13)
 */
public record GameSettings(ScreenSize screenSize, ColorBlindMode colorBlindMode, KeyBindings keyBindings,
                           Difficulty difficulty, VersusKeyBindings versusKeys) {
    public enum ScreenSize {
        // LARGE가 디자인 기준 크기(배율 1.0)다. 노트북 화면에서도 창이 넘치지 않도록 800px 높이를 상한으로 둔다.
        SMALL(480, 640), MEDIUM(540, 720), LARGE(600, 800);

        private final int width;
        private final int height;

        ScreenSize(int width, int height) {
            this.width = width;
            this.height = height;
        }

        public int width() { return width; }
        public int height() { return height; }
    }

    public enum ColorBlindMode { OFF, RED_GREEN, BLUE_YELLOW }

    public GameSettings {
        Objects.requireNonNull(screenSize, "screenSize");
        Objects.requireNonNull(colorBlindMode, "colorBlindMode");
        Objects.requireNonNull(keyBindings, "keyBindings");
        Objects.requireNonNull(difficulty, "difficulty");
        Objects.requireNonNull(versusKeys, "versusKeys");
    }

    /** 대전 키는 기본값. */
    public GameSettings(ScreenSize screenSize, ColorBlindMode colorBlindMode, KeyBindings keyBindings,
                        Difficulty difficulty) {
        this(screenSize, colorBlindMode, keyBindings, difficulty, VersusKeyBindings.defaults());
    }

    public GameSettings(ScreenSize screenSize, ColorBlindMode colorBlindMode, KeyBindings keyBindings) {
        this(screenSize, colorBlindMode, keyBindings, Difficulty.NORMAL);
    }

    public static GameSettings defaults() {
        return new GameSettings(ScreenSize.MEDIUM, ColorBlindMode.OFF, KeyBindings.defaults());
    }

    public boolean isColorBlindModeEnabled() { return colorBlindMode != ColorBlindMode.OFF; }

    public GameSettings withScreenSize(ScreenSize size) {
        return new GameSettings(size, colorBlindMode, keyBindings, difficulty, versusKeys);
    }

    public GameSettings withColorBlindMode(ColorBlindMode mode) {
        return new GameSettings(screenSize, mode, keyBindings, difficulty, versusKeys);
    }

    public GameSettings withKeyBindings(KeyBindings bindings) {
        return new GameSettings(screenSize, colorBlindMode, bindings, difficulty, versusKeys);
    }

    public GameSettings withDifficulty(Difficulty difficulty) {
        return new GameSettings(screenSize, colorBlindMode, keyBindings, difficulty, versusKeys);
    }

    /**
     * 대전 키를 바꾼 새 설정. 대전 키가 일시정지·나가기 키와 겹치면 거부한다 (input.md 4절).
     *
     * @throws IllegalArgumentException 겹치는 키가 있을 때
     */
    public GameSettings withVersusKeys(VersusKeyBindings keys) {
        GameSettings changed = new GameSettings(screenSize, colorBlindMode, keyBindings, difficulty, keys);
        if (!changed.versusKeyClashes().isEmpty()) {
            throw new IllegalArgumentException("Versus keys must differ from the shared keys: "
                    + changed.versusKeyClashes());
        }
        return changed;
    }

    /**
     * 키가 대전 키와 겹친 함께 쓰는 동작(일시정지·나가기). 보통 비어 있다.
     *
     * 1인용 일시정지·나가기 키를 나중에 대전 키로 바꾸면 생길 수 있다. 이것을 거부하면 1인용 키 설정과 이전 설정 파일
     * 읽기가 대전 키 때문에 실패하므로 허용한다. 겹친 키는 대전 중에 일시정지·나가기로 쓰이고 조작에는 쓰이지 않는다.
     * 설정 화면은 이것으로 경고를 띄울 수 있다.
     */
    public Set<GameAction> versusKeyClashes() {
        EnumSet<GameAction> clashes = EnumSet.noneOf(GameAction.class);
        for (GameAction shared : VersusKeyBindings.SHARED_ACTIONS) {
            if (versusKeys.uses(keyBindings.keyFor(shared))) clashes.add(shared);
        }
        return Collections.unmodifiableSet(clashes);
    }

    /**
     * 대전 입력({@code VersusInput})이 쓰는 키 표: 키 코드 → 플레이어의 조작. 일시정지·나가기 키와 겹친 대전 키는
     * 빼므로, 그 키는 대전 중에 일시정지·나가기로만 쓰인다 ({@link #versusKeyClashes()}).
     */
    public Map<Integer, PlayerMove> versusKeyMap() {
        Set<Integer> shared = new HashSet<>();
        for (GameAction action : VersusKeyBindings.SHARED_ACTIONS) shared.add(keyBindings.keyFor(action));
        Map<Integer, PlayerMove> map = new HashMap<>();
        for (Player player : Player.values()) {
            versusKeys.keys(player).forEach((action, key) -> {
                if (!shared.contains(key)) map.put(key, new PlayerMove(player, action));
            });
        }
        return Map.copyOf(map);
    }
}

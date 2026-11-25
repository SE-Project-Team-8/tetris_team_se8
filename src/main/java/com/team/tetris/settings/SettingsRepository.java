package com.team.tetris.settings;

import com.team.tetris.common.persistence.PropertiesFile;
import com.team.tetris.common.Difficulty;
import com.team.tetris.common.battle.Player;
import com.team.tetris.game.GameAction;

import java.awt.event.KeyEvent;
import java.io.IOException;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Locale;
import java.util.Objects;
import java.util.Properties;
import java.util.Set;

/**
 * Missing data uses defaults; invalid data raises IOException without overwriting it.
 *
 * 대전 키는 {@code versus.p1.MOVE_LEFT=65}처럼 플레이어·동작마다 한 줄로 저장한다(값은 {@code SidedKey} 정수). 형식 버전은
 * 2 그대로다. 대전 키 줄이 하나도 없는 파일(이전 버전이 저장한 파일)은 대전 키를 기본값으로 읽고, 일부만 있으면 잘못된
 * 파일로 본다. 대전 키를 모르는 이전 버전의 게임은 이 줄들을 무시하므로 새 파일도 읽을 수 있다 (input.md 4절).
 */
public final class SettingsRepository {
    private final Path file;

    public SettingsRepository() {
        this(Path.of(System.getProperty("user.home"), ".tetris-team-se8", "settings.properties"));
    }

    public SettingsRepository(Path file) {
        this.file = Objects.requireNonNull(file, "file").toAbsolutePath().normalize();
    }

    public synchronized GameSettings load() throws IOException {
        Properties properties = PropertiesFile.read(file);
        if (properties.isEmpty()) return GameSettings.defaults();
        try {
            int version = PropertiesFile.requireVersion(properties, 1, 2);
            EnumMap<GameAction, Integer> keys = new EnumMap<>(GameAction.class);
            for (GameAction action : GameAction.values()) {
                if (ADDED_ACTIONS.contains(action) && properties.getProperty("key." + action.name()) == null) continue;
                keys.put(action, Integer.parseInt(PropertiesFile.required(properties, "key." + action.name())));
            }
            fillAddedActions(keys);
            // 대전 키가 일시정지·나가기 키와 겹쳐 있어도 읽는다. 겹침은 대전 키를 바꿀 때만 거부한다 (GameSettings).
            return new GameSettings(
                    GameSettings.ScreenSize.valueOf(PropertiesFile.required(properties, "screenSize")),
                    GameSettings.ColorBlindMode.valueOf(PropertiesFile.required(properties, "colorBlindMode")),
                    new KeyBindings(keys), version == 1 ? Difficulty.NORMAL
                            : Difficulty.valueOf(PropertiesFile.required(properties, "difficulty")),
                    loadVersusKeys(properties));
        } catch (IllegalArgumentException invalid) {
            throw new IOException("Invalid settings: " + file, invalid);
        }
    }

    /**
     * 설정 파일 형식을 만든 뒤에 추가된 동작. 예전 파일에 키가 없으면 오류 대신 기본 키로 채운다.
     * 기본 키를 이미 다른 동작이 쓰고 있으면 남는 키 중 하나를 고른다.
     */
    private static final Set<GameAction> ADDED_ACTIONS = EnumSet.of(GameAction.ROTATE_COUNTERCLOCKWISE);
    private static final int[] SPARE_KEYS = {KeyEvent.VK_Z, KeyEvent.VK_X, KeyEvent.VK_C, KeyEvent.VK_V,
            KeyEvent.VK_A, KeyEvent.VK_S, KeyEvent.VK_D, KeyEvent.VK_Q, KeyEvent.VK_W, KeyEvent.VK_E};

    private static void fillAddedActions(EnumMap<GameAction, Integer> keys) {
        KeyBindings defaults = KeyBindings.defaults();
        for (GameAction action : ADDED_ACTIONS) {
            if (keys.containsKey(action)) continue;
            int preferred = defaults.keyFor(action);
            if (!keys.containsValue(preferred)) {
                keys.put(action, preferred);
                continue;
            }
            for (int spare : SPARE_KEYS) {
                if (!keys.containsValue(spare)) {
                    keys.put(action, spare);
                    break;
                }
            }
        }
    }

    public synchronized void save(GameSettings settings) throws IOException {
        Objects.requireNonNull(settings, "settings");
        Properties properties = new Properties();
        properties.setProperty("version", "2");
        properties.setProperty("difficulty", settings.difficulty().name());
        properties.setProperty("screenSize", settings.screenSize().name());
        properties.setProperty("colorBlindMode", settings.colorBlindMode().name());
        settings.keyBindings().keys().forEach((action, key) ->
                properties.setProperty("key." + action.name(), key.toString()));
        for (Player player : Player.values()) {
            settings.versusKeys().keys(player).forEach((action, key) ->
                    properties.setProperty(versusKey(player, action), key.toString()));
        }
        PropertiesFile.write(file, properties);
    }

    /** 대전 키 줄이 하나도 없으면 기본값, 모두 있으면 그 값. 일부만 있거나 값이 잘못되면 IllegalArgumentException. */
    private static VersusKeyBindings loadVersusKeys(Properties properties) {
        boolean any = properties.stringPropertyNames().stream().anyMatch(name -> name.startsWith(VERSUS_PREFIX));
        if (!any) return VersusKeyBindings.defaults();
        EnumMap<Player, EnumMap<GameAction, Integer>> keys = new EnumMap<>(Player.class);
        for (Player player : Player.values()) {
            EnumMap<GameAction, Integer> playerKeys = new EnumMap<>(GameAction.class);
            for (GameAction action : VersusKeyBindings.PLAYER_ACTIONS) {
                playerKeys.put(action, Integer.parseInt(PropertiesFile.required(properties, versusKey(player, action))));
            }
            keys.put(player, playerKeys);
        }
        return new VersusKeyBindings(keys.get(Player.P1), keys.get(Player.P2));
    }

    private static final String VERSUS_PREFIX = "versus.";

    /** 예: {@code versus.p1.MOVE_LEFT} */
    private static String versusKey(Player player, GameAction action) {
        return VERSUS_PREFIX + player.name().toLowerCase(Locale.ROOT) + "." + action.name();
    }

    /** Reset preferences only; scoreboard reset is a separate user action. */
    public synchronized GameSettings reset() throws IOException {
        GameSettings defaults = GameSettings.defaults();
        save(defaults);
        return defaults;
    }
}

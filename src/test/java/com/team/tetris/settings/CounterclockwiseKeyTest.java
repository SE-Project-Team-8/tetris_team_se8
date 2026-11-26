/*
 * 반시계 회전(ROTATE_COUNTERCLOCKWISE) 조작 키: 기본값 Z, 키가 없는 예전 설정 파일 읽기 (이슈 #64).
 */
package com.team.tetris.settings;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.awt.event.KeyEvent;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.team.tetris.game.GameAction;

class CounterclockwiseKeyTest {
    @TempDir Path directory;

    @Test
    void theDefaultCounterclockwiseKeyIsZ() {
        assertEquals(KeyEvent.VK_Z, KeyBindings.defaults().keyFor(GameAction.ROTATE_COUNTERCLOCKWISE));
        assertEquals(KeyEvent.VK_UP, KeyBindings.defaults().keyFor(GameAction.ROTATE_CLOCKWISE));
    }

    @Test
    void aSettingsFileSavedBeforeTheKeyExistedLoadsWithZ() throws Exception {
        Path file = directory.resolve("settings.properties");
        SettingsRepository repository = new SettingsRepository(file);
        repository.save(GameSettings.defaults());
        removeCounterclockwiseKey(file);

        KeyBindings keys = repository.load().keyBindings();
        assertEquals(KeyEvent.VK_Z, keys.keyFor(GameAction.ROTATE_COUNTERCLOCKWISE));
        assertEquals(KeyEvent.VK_UP, keys.keyFor(GameAction.ROTATE_CLOCKWISE), "Other keys are kept");
    }

    @Test
    void whenZIsAlreadyTakenTheMissingKeyGetsASpareOne() throws Exception {
        Path file = directory.resolve("settings.properties");
        SettingsRepository repository = new SettingsRepository(file);
        // Z는 이미 왼쪽 이동이 쓰고 있다 (반시계 회전 키는 다른 키로 잠시 두었다가 파일에서 지운다).
        KeyBindings custom = KeyBindings.defaults()
                .withKey(GameAction.ROTATE_COUNTERCLOCKWISE, KeyEvent.VK_F2)
                .withKey(GameAction.MOVE_LEFT, KeyEvent.VK_Z);
        repository.save(GameSettings.defaults().withKeyBindings(custom));
        removeCounterclockwiseKey(file);

        KeyBindings keys = repository.load().keyBindings();
        assertEquals(KeyEvent.VK_Z, keys.keyFor(GameAction.MOVE_LEFT));
        assertEquals(KeyEvent.VK_X, keys.keyFor(GameAction.ROTATE_COUNTERCLOCKWISE));
    }

    @Test
    void aChangedCounterclockwiseKeySurvivesSaving() throws Exception {
        Path file = directory.resolve("settings.properties");
        SettingsRepository repository = new SettingsRepository(file);
        KeyBindings custom = KeyBindings.defaults().withKey(GameAction.ROTATE_COUNTERCLOCKWISE, KeyEvent.VK_Q);
        repository.save(GameSettings.defaults().withKeyBindings(custom));
        assertEquals(KeyEvent.VK_Q,
                new SettingsRepository(file).load().keyBindings().keyFor(GameAction.ROTATE_COUNTERCLOCKWISE));
    }

    private static void removeCounterclockwiseKey(Path file) throws Exception {
        String text = Files.readString(file).replaceAll("(?m)^key\\.ROTATE_COUNTERCLOCKWISE=.*\\R?", "");
        Files.writeString(file, text);
    }
}

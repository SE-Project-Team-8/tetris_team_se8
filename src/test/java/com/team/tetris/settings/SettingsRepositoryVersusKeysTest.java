/*
 * 대전 키 저장 테스트: 저장 후 다시 읽기, 대전 키 줄이 없는 이전 파일은 기본값, 일부만 있거나 잘못된 값은 거부하고 파일을
 * 보존, 기본값 되돌리기, 일시정지 키와 겹친 채 저장된 설정 읽기 (input.md 4절, LB-I11). 기존 SettingsRepositoryTest는 그대로 둔다.
 */
package com.team.tetris.settings;

import com.team.tetris.common.Difficulty;
import com.team.tetris.common.battle.Player;
import com.team.tetris.game.GameAction;
import com.team.tetris.game.versus.SidedKey;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.awt.event.KeyEvent;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class SettingsRepositoryVersusKeysTest {
    @TempDir Path directory;

    private static final VersusKeyBindings CUSTOM = VersusKeyBindings.defaults()
            .withKey(Player.P1, GameAction.ROTATE_CLOCKWISE, KeyEvent.VK_E)
            .withKey(Player.P2, GameAction.HARD_DROP, SidedKey.right(KeyEvent.VK_SHIFT));

    @Test
    void versusKeysSurviveSavingAndLoading() throws Exception {
        Path file = directory.resolve("settings.properties");
        GameSettings settings = GameSettings.defaults().withDifficulty(Difficulty.HARD).withVersusKeys(CUSTOM);
        new SettingsRepository(file).save(settings);
        GameSettings loaded = new SettingsRepository(file).load();
        assertEquals(settings, loaded);
        assertEquals(SidedKey.right(KeyEvent.VK_SHIFT), loaded.versusKeys().keyFor(Player.P2, GameAction.HARD_DROP),
                "the side of a key is kept");
    }

    @Test
    void eachVersusKeyIsOneReadableLineAndTheFormatVersionStaysTwo() throws Exception {
        Path file = directory.resolve("settings.properties");
        new SettingsRepository(file).save(GameSettings.defaults());
        String text = Files.readString(file);
        assertTrue(text.contains("versus.p1.MOVE_LEFT=" + KeyEvent.VK_A), text);
        assertTrue(text.contains("versus.p2.HARD_DROP=" + KeyEvent.VK_ENTER), text);
        assertTrue(text.contains("versus.p1.HARD_DROP=" + SidedKey.left(KeyEvent.VK_SHIFT)), text);
        assertTrue(text.contains("version=2"), "older builds of the game can still read the file");
        assertEquals(12, text.lines().filter(line -> line.startsWith("versus.")).count());
    }

    @Test
    void filesWithoutVersusKeysLoadTheDefaultsAndAreNotRewritten() throws Exception {
        Path file = directory.resolve("settings.properties");
        GameSettings older = GameSettings.defaults().withScreenSize(GameSettings.ScreenSize.LARGE)
                .withKeyBindings(KeyBindings.defaults().withKey(GameAction.MOVE_LEFT, KeyEvent.VK_J));
        new SettingsRepository(file).save(older.withVersusKeys(CUSTOM));
        String withoutVersus = Files.readString(file).replaceAll("(?m)^versus\\..*\\R?", "");
        for (String text : new String[]{withoutVersus, withoutVersus.replace("version=2", "version=1")
                .replaceAll("(?m)^difficulty=.*\\R?", "")}) {
            Files.writeString(file, text);
            GameSettings loaded = new SettingsRepository(file).load();
            assertEquals(VersusKeyBindings.defaults(), loaded.versusKeys());
            assertEquals(GameSettings.ScreenSize.LARGE, loaded.screenSize(), "the rest is kept");
            assertEquals(KeyEvent.VK_J, loaded.keyBindings().keyFor(GameAction.MOVE_LEFT));
            assertEquals(text, Files.readString(file));
        }
    }

    @Test
    void filesSavedBeforeTheCounterclockwiseKeyGetItFilledIn() throws Exception {
        Path file = directory.resolve("settings.properties");
        new SettingsRepository(file).save(GameSettings.defaults().withVersusKeys(CUSTOM));
        String older = Files.readString(file).replaceAll("(?m)^versus\\.p[12]\\.ROTATE_COUNTERCLOCKWISE=.*\\R?", "");
        Files.writeString(file, older);
        VersusKeyBindings loaded = new SettingsRepository(file).load().versusKeys();
        assertEquals(KeyEvent.VK_Q, loaded.keyFor(Player.P1, GameAction.ROTATE_COUNTERCLOCKWISE));
        assertEquals(KeyEvent.VK_SLASH, loaded.keyFor(Player.P2, GameAction.ROTATE_COUNTERCLOCKWISE));
        assertEquals(KeyEvent.VK_E, loaded.keyFor(Player.P1, GameAction.ROTATE_CLOCKWISE), "the saved keys are kept");

        // 기본 왼쪽 회전 키(Q)를 이미 다른 동작에 쓰고 있으면 비어 있는 다른 키를 준다.
        Files.writeString(file, older.replace("versus.p1.ROTATE_CLOCKWISE=" + KeyEvent.VK_E,
                "versus.p1.ROTATE_CLOCKWISE=" + KeyEvent.VK_Q));
        loaded = new SettingsRepository(file).load().versusKeys();
        assertEquals(KeyEvent.VK_Q, loaded.keyFor(Player.P1, GameAction.ROTATE_CLOCKWISE));
        assertEquals(KeyEvent.VK_E, loaded.keyFor(Player.P1, GameAction.ROTATE_COUNTERCLOCKWISE));
    }

    @Test
    void partialOrInvalidVersusKeysAreReportedAndTheFileIsPreserved() throws Exception {
        Path file = directory.resolve("settings.properties");
        new SettingsRepository(file).save(GameSettings.defaults());
        String valid = Files.readString(file);
        String[] broken = {
                valid.replaceAll("(?m)^versus\\.p2\\.SOFT_DROP=.*\\R?", ""),          // one line missing
                valid.replace("versus.p2.MOVE_LEFT=" + KeyEvent.VK_LEFT, "versus.p2.MOVE_LEFT=left"),
                valid.replace("versus.p2.MOVE_LEFT=" + KeyEvent.VK_LEFT, "versus.p2.MOVE_LEFT=" + KeyEvent.VK_A),
                valid.replace("versus.p2.MOVE_LEFT=" + KeyEvent.VK_LEFT,
                        "versus.p2.MOVE_LEFT=" + (KeyEvent.VK_LEFT | 9 << 16)),          // no such side
        };
        for (String text : broken) {
            Files.writeString(file, text);
            assertThrows(IOException.class, () -> new SettingsRepository(file).load(), text);
            assertEquals(text, Files.readString(file));
        }
    }

    @Test
    void resettingAlsoResetsTheVersusKeys() throws Exception {
        Path file = directory.resolve("settings.properties");
        SettingsRepository repository = new SettingsRepository(file);
        repository.save(GameSettings.defaults().withVersusKeys(CUSTOM));
        assertEquals(VersusKeyBindings.defaults(), repository.reset().versusKeys());
        assertEquals(VersusKeyBindings.defaults(), new SettingsRepository(file).load().versusKeys());
    }

    @Test
    void aPauseKeyThatClashesWithAVersusKeyStillLoads() throws Exception {
        Path file = directory.resolve("settings.properties");
        GameSettings clashing = GameSettings.defaults()
                .withKeyBindings(KeyBindings.defaults().withKey(GameAction.PAUSE, KeyEvent.VK_ENTER));
        assertFalse(clashing.versusKeyClashes().isEmpty());
        new SettingsRepository(file).save(clashing);
        assertEquals(clashing, new SettingsRepository(file).load(), "a clash must not make the file unreadable");
    }
}

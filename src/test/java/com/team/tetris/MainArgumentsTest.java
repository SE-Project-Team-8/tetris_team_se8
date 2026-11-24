/*
 * 실행 인자 --versus=... 로 확인용 대전 화면의 방식을 고르는지 (ui-contract.md 5절).
 */
package com.team.tetris;

import com.team.tetris.common.battle.VersusMode;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class MainArgumentsTest {
    @Test
    void theVersusArgumentPicksTheMode() {
        assertEquals(Optional.of(VersusMode.NORMAL), Main.versusModeFrom("--versus=normal"));
        assertEquals(Optional.of(VersusMode.ITEM), Main.versusModeFrom("--versus=ITEM"));
        assertEquals(Optional.of(VersusMode.TIME_LIMIT), Main.versusModeFrom("--other", "--versus=time"));
    }

    @Test
    void withoutTheArgumentTheGameStartsAsUsual() {
        assertEquals(Optional.empty(), Main.versusModeFrom());
        assertEquals(Optional.empty(), Main.versusModeFrom("--verbose"));
    }

    @Test
    void anUnknownModeIsRejectedWithTheChoices() {
        IllegalArgumentException failure =
                assertThrows(IllegalArgumentException.class, () -> Main.versusModeFrom("--versus=solo"));
        assertTrue(failure.getMessage().contains("normal, item or time"));
    }
}

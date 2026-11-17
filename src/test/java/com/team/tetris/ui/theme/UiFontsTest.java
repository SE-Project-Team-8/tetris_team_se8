package com.team.tetris.ui.theme;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class UiFontsTest {
    @Test
    void bundledFontsLoadInsteadOfFallback() {
        assertEquals("Jua", UiFonts.display(20f).getFamily());
        assertEquals("IBM Plex Mono", UiFonts.mono(20f).getFamily());
    }

    @Test
    void derivedFontsKeepRequestedSize() {
        assertEquals(18f, UiFonts.display(18f).getSize2D());
        assertEquals(14f, UiFonts.body(14f).getSize2D());
        assertEquals(9.5f, UiFonts.mono(9.5f).getSize2D());
    }

    @Test
    void playerNamesFallBackWhenMonoCannotDrawThem() {
        assertEquals("IBM Plex Mono", UiFonts.monoOrFallback("JOSE_01", 16f).getFamily());
        String korean = "성빈";
        assertEquals(-1, UiFonts.monoOrFallback(korean, 16f).canDisplayUpTo(korean),
                "Korean names must not render as missing-glyph boxes");
    }

    @Test
    void displayFontRendersKoreanLabels() {
        assertEquals(-1, UiFonts.display(20f).canDisplayUpTo("게임 시작 스코어보드"));
    }
}

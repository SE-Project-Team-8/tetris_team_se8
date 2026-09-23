package com.team.tetris.block;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class ColorSchemeTest {
    @Test
    void bothModesHaveSevenUniqueValidColorsDifferentFromBackground() {
        for (ColorScheme scheme : new ColorScheme[]{ColorScheme.standard(), ColorScheme.colorBlind()}) {
            Set<Integer> colors = new HashSet<>();
            for (TetrominoType type : TetrominoType.values()) {
                int rgb = scheme.rgbOf(type);
                assertTrue(rgb >= 0 && rgb <= 0xFFFFFF);
                assertNotEquals(scheme.emptyRgb(), rgb);
                assertTrue(colors.add(rgb));
            }
            assertEquals(7, colors.size());
            assertThrows(NullPointerException.class, () -> scheme.rgbOf(null));
        }
    }

    @ParameterizedTest
    @EnumSource(TetrominoType.class)
    void modeSwitchChangesColorWithoutChangingTypeId(TetrominoType type) {
        int id = type.id();
        assertNotEquals(ColorScheme.standard().rgbOf(type), ColorScheme.colorBlind().rgbOf(type));
        assertSame(type, TetrominoType.fromId(id));
    }

    @Test
    void copiesInputAndSupportsRgbBoundaryValues() {
        Map<TetrominoType, Integer> input = standardColors();
        input.put(TetrominoType.I, 0x000000);
        input.put(TetrominoType.O, 0xFFFFFF);
        var scheme = new ColorScheme(input, 0x10141C);
        input.clear();
        assertEquals(0x000000, scheme.rgbOf(TetrominoType.I));
        assertEquals(0xFFFFFF, scheme.rgbOf(TetrominoType.O));
        assertEquals(0x10141C, scheme.emptyRgb());
    }

    @ParameterizedTest
    @EnumSource(TetrominoType.class)
    void rejectsEveryMissingType(TetrominoType missing) {
        var input = standardColors();
        input.remove(missing);
        var before = new EnumMap<>(input);
        assertThrows(InvalidColorSchemeException.class, () -> new ColorScheme(input, 0x10141C));
        assertEquals(before, input);
    }

    @ParameterizedTest
    @ValueSource(ints = {-1, 0x1000000, Integer.MIN_VALUE, Integer.MAX_VALUE})
    void rejectsInvalidRgbForBothBlockAndEmptyCell(int rgb) {
        var input = standardColors();
        assertThrows(InvalidColorSchemeException.class, () -> new ColorScheme(input, rgb));
        input.put(TetrominoType.T, rgb);
        assertThrows(InvalidColorSchemeException.class, () -> new ColorScheme(input, 0x10141C));
    }

    @Test
    void rejectsDuplicateColorsIncludingBackground() {
        var input = standardColors();
        input.put(TetrominoType.J, input.get(TetrominoType.L));
        assertThrows(InvalidColorSchemeException.class, () -> new ColorScheme(input, 0x10141C));
        var valid = standardColors();
        assertThrows(InvalidColorSchemeException.class,
                () -> new ColorScheme(valid, valid.get(TetrominoType.I)));
    }

    @Test
    void nullMapKeysAndValuesAreProgrammingErrors() {
        assertThrows(NullPointerException.class, () -> new ColorScheme(null, 0));
        Map<TetrominoType, Integer> withNullKey = new HashMap<>(standardColors());
        withNullKey.put(null, 0);
        assertThrows(NullPointerException.class, () -> new ColorScheme(withNullKey, 0));
        var withNullValue = standardColors();
        withNullValue.put(TetrominoType.I, null);
        assertThrows(NullPointerException.class, () -> new ColorScheme(withNullValue, 0));
    }

    private static EnumMap<TetrominoType, Integer> standardColors() {
        var colors = new EnumMap<TetrominoType, Integer>(TetrominoType.class);
        for (TetrominoType type : TetrominoType.values()) {
            colors.put(type, ColorScheme.standard().rgbOf(type));
        }
        return colors;
    }
}

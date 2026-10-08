package com.team.tetris.block;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class TetrominoTypeTest {
    @ParameterizedTest
    @CsvSource({"I,0", "O,1", "T,2", "S,3", "Z,4", "J,5", "L,6"})
    void idsAreExplicitAndRoundTrip(TetrominoType type, int expectedId) {
        assertEquals(expectedId, type.id());
        assertSame(type, TetrominoType.fromId(expectedId));
    }

    @ParameterizedTest
    @ValueSource(ints = {-1, -2, 7, Integer.MIN_VALUE, Integer.MAX_VALUE})
    void unknownIdsRetainFailureContext(int id) {
        var error = assertThrows(InvalidTetrominoIdException.class,
                () -> TetrominoType.fromId(id));
        assertEquals(id, error.id());
        assertInstanceOf(IllegalArgumentException.class, error);
    }

    @Test
    void allRequiredTypesExist() {
        assertEquals(Set.of("I", "O", "T", "S", "Z", "J", "L"),
                java.util.Arrays.stream(TetrominoType.values())
                        .map(Enum::name).collect(java.util.stream.Collectors.toSet()));
    }
}

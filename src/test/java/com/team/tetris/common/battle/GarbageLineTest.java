/*
 * GarbageLine 테스트: 열별 채움 여부, 만들 수 없는 모양(빈 줄·꽉 찬 줄·폭 초과), 값 비교와 직렬화.
 */
package com.team.tetris.common.battle;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

import static org.junit.jupiter.api.Assertions.*;

class GarbageLineTest {
    @Test
    void columnsAreReadLeftToRightFromTheBits() {
        GarbageLine line = GarbageLine.of(true, true, false, true, true, true, true, true, true, false);
        assertTrue(line.filled(0));
        assertTrue(line.filled(1));
        assertFalse(line.filled(2), "the hole left by the locked piece");
        assertTrue(line.filled(8));
        assertFalse(line.filled(9));
        assertEquals(0b0111111011, line.cells());
    }

    @Test
    void emptyFullAndTooWideLinesCannotBeMade() {
        assertThrows(IllegalArgumentException.class, () -> new GarbageLine(0), "nothing to send");
        assertThrows(IllegalArgumentException.class, () -> new GarbageLine(0b1111111111), "would clear at once");
        assertThrows(IllegalArgumentException.class, () -> new GarbageLine(1 << 10), "beyond the board");
        assertThrows(IllegalArgumentException.class, () -> new GarbageLine(-1));
        assertFalse(GarbageLine.isSendable(0));
        assertFalse(GarbageLine.isSendable(0b1111111111));
        assertTrue(GarbageLine.isSendable(1));
        assertTrue(GarbageLine.isSendable(0b0111111111));
    }

    @Test
    void columnCountAndIndexAreChecked() {
        assertThrows(IllegalArgumentException.class, () -> GarbageLine.of(true, false));
        assertThrows(NullPointerException.class, () -> GarbageLine.of((boolean[]) null));
        GarbageLine line = new GarbageLine(1);
        assertThrows(IndexOutOfBoundsException.class, () -> line.filled(-1));
        assertThrows(IndexOutOfBoundsException.class, () -> line.filled(10));
    }

    @Test
    void sameShapeMeansEqualAndSurvivesSerialization() throws Exception {
        GarbageLine line = GarbageLine.of(false, true, true, true, true, true, true, true, true, true);
        assertEquals(new GarbageLine(line.cells()), line);
        assertEquals(new GarbageLine(line.cells()).hashCode(), line.hashCode());

        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream out = new ObjectOutputStream(bytes)) {
            out.writeObject(line);
        }
        try (ObjectInputStream in = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
            assertEquals(line, in.readObject(), "P2P sends the line as is");
        }
    }

    @Test
    void playersAreEachOthersOpponents() {
        assertEquals(Player.P2, Player.P1.opponent());
        assertEquals(Player.P1, Player.P2.opponent());
    }
}

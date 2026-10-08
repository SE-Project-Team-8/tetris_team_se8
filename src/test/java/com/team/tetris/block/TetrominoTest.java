package com.team.tetris.block;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.HashSet;
import java.util.Set;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

class TetrominoTest {
    /** 회전 알고리즘을 기대값 계산에 복제하지 않고, 28개 결과 격자를 직접 고정한다. */
    static Stream<Arguments> shapes() {
        return Stream.of(
                states(TetrominoType.I, "..../1111/..../....", "..1./..1./..1./..1.",
                        "..../..../1111/....", ".1../.1../.1../.1.."),
                states(TetrominoType.O, "11/11", "11/11", "11/11", "11/11"),
                states(TetrominoType.T, ".1./111/...", ".1./.11/.1.", ".../111/.1.", ".1./11./.1."),
                states(TetrominoType.S, ".11/11./...", ".1./.11/..1", ".../.11/11.", "1../11./.1."),
                states(TetrominoType.Z, "11./.11/...", "..1/.11/.1.", ".../11./.11", ".1./11./1.."),
                states(TetrominoType.J, "111/..1/...", "..1/..1/.11", ".../1../111", "11./1../1.."),
                states(TetrominoType.L, "111/1../...", ".11/..1/..1", ".../..1/111", "1../1../11."))
                .flatMap(stream -> stream);
    }

    private static Stream<Arguments> states(TetrominoType type, String... pictures) {
        return IntStream.range(0, 4)
                .mapToObj(index -> Arguments.of(type, Rotation.values()[index], pictures[index]));
    }

    @ParameterizedTest(name = "{0} {1}: {2}")
    @MethodSource("shapes")
    void matchesEveryExpectedOrientation(TetrominoType type, Rotation rotation, String picture) {
        Set<Cell> expected = new HashSet<>();
        String[] rows = picture.split("/");
        for (int row = 0; row < rows.length; row++) {
            for (int col = 0; col < rows[row].length(); col++) {
                if (rows[row].charAt(col) == '1') {
                    expected.add(new Cell(row + 5, col + 4));
                }
            }
        }
        var piece = new Tetromino(type, rotation, 5, 4);
        assertEquals(4, piece.cells().size());
        assertEquals(4, new HashSet<>(piece.cells()).size());
        assertEquals(expected, new HashSet<>(piece.cells()));
    }

    @ParameterizedTest
    @EnumSource(TetrominoType.class)
    void movementAndFourRotationsPreserveOriginal(TetrominoType type) {
        var original = new Tetromino(type, Rotation.SPAWN, 3, 4);
        var before = original.cells();
        var moved = original.moveBy(2, -3);
        assertEquals(5, moved.row());
        assertEquals(1, moved.col());
        assertEquals(before.stream().map(cell -> new Cell(cell.row() + 2, cell.col() - 3)).toList(),
                moved.cells());
        assertEquals(original, original.rotateClockwise().rotateClockwise()
                .rotateClockwise().rotateClockwise());
        assertEquals(before, original.cells());
        assertEquals(3, original.row());
        assertEquals(4, original.col());
        assertThrows(UnsupportedOperationException.class, () -> before.add(new Cell(0, 0)));
    }

    @Test
    void squareKeepsCellsAndRotationMovesClockwise() {
        var square = new Tetromino(TetrominoType.O, Rotation.SPAWN, 2, 3);
        assertEquals(square.cells(), square.rotateClockwise().cells());
        assertEquals(Rotation.RIGHT, square.rotateClockwise().rotation());
        var tee = new Tetromino(TetrominoType.T, Rotation.SPAWN, 0, 0);
        assertEquals(Set.of(new Cell(0, 1), new Cell(1, 1), new Cell(1, 2), new Cell(2, 1)),
                new HashSet<>(tee.rotateClockwise().cells()));
    }

    @Test
    void permitsOutsideCandidatesButRejectsNullAndOverflow() {
        var outside = new Tetromino(TetrominoType.O, Rotation.SPAWN, -10, -10);
        assertEquals(new Cell(-10, -10), outside.cells().getFirst());
        assertThrows(NullPointerException.class, () -> new Tetromino(null, Rotation.SPAWN, 0, 0));
        assertThrows(NullPointerException.class, () -> new Tetromino(TetrominoType.I, null, 0, 0));
        assertThrows(ArithmeticException.class,
                () -> new Tetromino(TetrominoType.O, Rotation.SPAWN, Integer.MAX_VALUE, 0));
        assertThrows(ArithmeticException.class,
                () -> new Tetromino(TetrominoType.O, Rotation.SPAWN, 0, Integer.MAX_VALUE));
        var nearTop = new Tetromino(TetrominoType.O, Rotation.SPAWN, Integer.MIN_VALUE, 0);
        assertThrows(ArithmeticException.class, () -> nearTop.moveBy(-1, 0));
        var nearLeft = new Tetromino(TetrominoType.O, Rotation.SPAWN, 0, Integer.MIN_VALUE);
        assertThrows(ArithmeticException.class, () -> nearLeft.moveBy(0, -1));
        var nearBottom = new Tetromino(TetrominoType.I, Rotation.SPAWN, Integer.MAX_VALUE - 1, 0);
        assertThrows(ArithmeticException.class, nearBottom::rotateClockwise);
        assertEquals(Rotation.SPAWN, nearBottom.rotation());
    }
}

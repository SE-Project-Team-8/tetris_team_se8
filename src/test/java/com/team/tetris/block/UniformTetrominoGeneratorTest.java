package com.team.tetris.block;

import org.junit.jupiter.api.Test;

import java.util.Random;
import java.util.random.RandomGenerator;

import static org.junit.jupiter.api.Assertions.*;

class UniformTetrominoGeneratorTest {
    @Test
    void mapsOneUniformSamplePerCallAndAllowsConsecutiveRepeats() {
        var random = new SequenceRandom(6, 6, 0, 0, 0, 1, 2, 3, 4, 5);
        var generator = new UniformTetrominoGenerator(random);
        TetrominoType[] expected = {TetrominoType.L, TetrominoType.L,
                TetrominoType.I, TetrominoType.I, TetrominoType.I, TetrominoType.O,
                TetrominoType.T, TetrominoType.S, TetrominoType.Z, TetrominoType.J};
        for (TetrominoType type : expected) {
            assertSame(type, generator.next());
        }
        assertEquals(expected.length, random.calls);
    }

    @Test
    void sameRandomAlgorithmAndSeedReproduceSequence() {
        var first = new UniformTetrominoGenerator(new Random(8128));
        var second = new UniformTetrominoGenerator(new Random(8128));
        for (int count = 0; count < 100; count++) {
            assertSame(first.next(), second.next());
        }
    }

    @Test
    void separateRandomSourcesDoNotShareProgress() {
        var first = new UniformTetrominoGenerator(new Random(42));
        var second = new UniformTetrominoGenerator(new Random(42));
        var control = new UniformTetrominoGenerator(new Random(42));
        assertSame(first.next(), second.next());
        control.next();
        for (int count = 0; count < 100; count++) {
            first.next();
        }
        assertSame(control.next(), second.next());
    }

    @Test
    void rejectsNullAndPropagatesRandomFailureWithoutFallback() {
        assertThrows(NullPointerException.class, () -> new UniformTetrominoGenerator(null));
        var failure = new IllegalStateException("random unavailable");
        RandomGenerator broken = new RandomGenerator() {
            @Override
            public long nextLong() {
                throw failure;
            }

            @Override
            public int nextInt(int bound) {
                throw failure;
            }
        };
        var generator = new UniformTetrominoGenerator(broken);
        assertSame(failure, assertThrows(IllegalStateException.class, generator::next));
    }

    /** 표본과 종류의 대응 및 불필요한 재추첨 여부를 검사하는 테스트용 난수원. */
    private static final class SequenceRandom implements RandomGenerator {
        private final int[] sequence;
        private int calls;

        private SequenceRandom(int... sequence) {
            this.sequence = sequence.clone();
        }

        @Override
        public int nextInt(int bound) {
            assertEquals(7, bound);
            if (calls >= sequence.length) {
                throw new AssertionError("Generator drew more than one sample per result");
            }
            return sequence[calls++];
        }

        @Override
        public long nextLong() {
            throw new AssertionError("Expected bounded uniform integer sampling");
        }
    }
}

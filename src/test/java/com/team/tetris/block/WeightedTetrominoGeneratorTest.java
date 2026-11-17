package com.team.tetris.block;

import com.team.tetris.common.Difficulty;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.EnumMap;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

class WeightedTetrominoGeneratorTest {
    @ParameterizedTest
    @EnumSource(Difficulty.class)
    void realGameGeneratorStaysWithinFivePercentOfItsConfiguredDistribution(Difficulty difficulty) {
        WeightedTetrominoGenerator generator = new WeightedTetrominoGenerator(new Random(20261014), difficulty);
        EnumMap<TetrominoType, Integer> counts = new EnumMap<>(TetrominoType.class);
        int samples = 100_000;
        for (int i = 0; i < samples; i++) counts.merge(generator.next(), 1, Integer::sum);
        double totalWeight = 60 + difficulty.iBlockWeight();
        for (TetrominoType type : TetrominoType.values()) {
            double weight = type == TetrominoType.I ? difficulty.iBlockWeight() : 10;
            double expected = samples * weight / totalWeight;
            assertEquals(expected, counts.getOrDefault(type, 0), expected * 0.05,
                    difficulty + " / " + type + " must stay within 5% of its expected count");
        }
    }

    @ParameterizedTest
    @EnumSource(Difficulty.class)
    void changingDifficultyUpdatesTheSameRouletteGenerator(Difficulty difficulty) {
        Random fixedSelection = new Random() {
            @Override public int nextInt(int bound) {
                assertEquals(60 + difficulty.iBlockWeight(), bound);
                return 8;
            }
        };
        WeightedTetrominoGenerator generator = new WeightedTetrominoGenerator(fixedSelection, Difficulty.NORMAL);
        generator.setDifficulty(difficulty);
        assertEquals(difficulty == Difficulty.HARD ? TetrominoType.O : TetrominoType.I, generator.next());
    }
}

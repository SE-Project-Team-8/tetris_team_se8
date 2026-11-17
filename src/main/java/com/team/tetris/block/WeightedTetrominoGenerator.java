package com.team.tetris.block;

import com.team.tetris.common.Difficulty;

import java.util.List;
import java.util.Objects;
import java.util.random.RandomGenerator;

/** Roulette wheel selection used by the real game. Change difficulty before starting a game. */
public final class WeightedTetrominoGenerator implements TetrominoGenerator {
    private static final List<TetrominoType> TYPES = List.of(TetrominoType.values());
    private final RandomGenerator random;
    private Difficulty difficulty;

    public WeightedTetrominoGenerator(RandomGenerator random, Difficulty difficulty) {
        this.random = Objects.requireNonNull(random, "random");
        setDifficulty(difficulty);
    }

    public void setDifficulty(Difficulty difficulty) {
        this.difficulty = Objects.requireNonNull(difficulty, "difficulty");
    }

    @Override
    public TetrominoType next() {
        int total = TYPES.stream().mapToInt(this::weight).sum();
        int selection = random.nextInt(total);
        for (TetrominoType type : TYPES) {
            int weight = weight(type);
            if (selection < weight) return type;
            selection -= weight;
        }
        throw new IllegalStateException("Random selection was outside the roulette wheel");
    }

    private int weight(TetrominoType type) {
        return type == TetrominoType.I ? difficulty.iBlockWeight() : 10;
    }
}

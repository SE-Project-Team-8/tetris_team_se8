/*
 * 가중치 비례(룰렛 휠) 아이템 선택기. 블록 생성기(WeightedTetrominoGenerator)와 같은 방식이며,
 * 실제 게임은 uniform()으로 등록된 모든 아이템을 같은 확률로 고른다.
 */
package com.team.tetris.game.item;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.random.RandomGenerator;

/**
 * 가중치 비례(룰렛 휠) 아이템 선택기. 블록 생성기와 같은 방식이다.
 * 실제 게임은 {@link #uniform}으로 모든 아이템을 같은 확률로 고른다.
 */
public final class WeightedItemSelector implements ItemSelector {
    private final List<Item> items;
    private final List<Integer> weights;
    private final int total;
    private final RandomGenerator random;

    /**
     * @param items 후보 아이템. 비어 있으면 안 된다
     * @param weights 같은 순서의 0 이상 가중치. 합은 양수여야 하며 0인 아이템은 나오지 않는다
     */
    public WeightedItemSelector(List<? extends Item> items, List<Integer> weights, RandomGenerator random) {
        this.items = List.copyOf(Objects.requireNonNull(items, "items"));
        this.weights = List.copyOf(Objects.requireNonNull(weights, "weights"));
        this.random = Objects.requireNonNull(random, "random");
        if (this.items.isEmpty()) throw new IllegalArgumentException("At least one item is required");
        if (this.items.size() != this.weights.size()) {
            throw new IllegalArgumentException("Every item needs exactly one weight");
        }
        int sum = 0;
        for (int weight : this.weights) {
            if (weight < 0) throw new IllegalArgumentException("Weights must be non-negative: " + weight);
            sum = Math.addExact(sum, weight);
        }
        if (sum == 0) throw new IllegalArgumentException("At least one weight must be positive");
        this.total = sum;
    }

    /** 레지스트리의 모든 아이템을 같은 확률로 고른다. */
    public static WeightedItemSelector uniform(ItemRegistry registry, RandomGenerator random) {
        List<Item> items = Objects.requireNonNull(registry, "registry").items();
        return new WeightedItemSelector(items, Collections.nCopies(items.size(), 1), random);
    }

    @Override
    public Item next() {
        int selection = random.nextInt(total);
        for (int index = 0; index < items.size(); index++) {
            int weight = weights.get(index);
            if (selection < weight) return items.get(index);
            selection -= weight;
        }
        throw new IllegalStateException("Random selection was outside the roulette wheel");
    }
}

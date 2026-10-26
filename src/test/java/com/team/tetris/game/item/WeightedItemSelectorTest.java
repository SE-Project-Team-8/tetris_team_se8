package com.team.tetris.game.item;

import org.junit.jupiter.api.Test;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

/** 블록 생성기와 같은 기준(2차 요구사항 3.2)으로 아이템 선택 확률을 검증한다: 1,000회 이상, ±5%. */
class WeightedItemSelectorTest {
    private static final int DRAWS = 10_000;

    @Test
    void uniformSelectorGivesEveryRegisteredItemTheSameChance() {
        ItemRegistry registry = ItemRegistry.defaults();
        Map<String, Integer> counts = count(WeightedItemSelector.uniform(registry, new Random(7)));
        for (Item item : registry.items()) {
            double share = counts.getOrDefault(item.name(), 0) / (double) DRAWS;
            assertEquals(1.0 / registry.size(), share, 0.05, item.name());
        }
    }

    @Test
    void weightsAreProportionalAndZeroWeightItemsNeverAppear() {
        Item line = new LineDeleteItem();
        Item anvil = new AnvilItem();
        Map<String, Integer> counts = count(new WeightedItemSelector(List.of(line, anvil), List.of(3, 1), new Random(11)));
        assertEquals(0.75, counts.get("LINE_DELETE") / (double) DRAWS, 0.05);
        assertEquals(0.25, counts.get("ANVIL") / (double) DRAWS, 0.05);

        Map<String, Integer> never = count(new WeightedItemSelector(List.of(line, anvil), List.of(0, 5), new Random(3)));
        assertFalse(never.containsKey("LINE_DELETE"));
        assertEquals(DRAWS, never.get("ANVIL"));
    }

    @Test
    void rejectsImpossibleConfigurations() {
        Random random = new Random(1);
        assertThrows(IllegalArgumentException.class, () -> new WeightedItemSelector(List.of(), List.of(), random));
        assertThrows(IllegalArgumentException.class,
                () -> new WeightedItemSelector(List.of(new AnvilItem()), List.of(1, 2), random));
        assertThrows(IllegalArgumentException.class,
                () -> new WeightedItemSelector(List.of(new AnvilItem()), List.of(-1), random));
        assertThrows(IllegalArgumentException.class,
                () -> new WeightedItemSelector(List.of(new AnvilItem()), List.of(0), random));
        assertThrows(IllegalArgumentException.class,
                () -> WeightedItemSelector.uniform(new ItemRegistry(List.of()), random));
    }

    private static Map<String, Integer> count(ItemSelector selector) {
        Map<String, Integer> counts = new HashMap<>();
        for (int draw = 0; draw < DRAWS; draw++) {
            counts.merge(selector.next().name(), 1, Integer::sum);
        }
        return counts;
    }
}

package com.team.tetris.game.item;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class ItemPolicyTest {
    @Test
    void everyTenLinesEarnsExactlyOneItemEvenAcrossUnevenClears() {
        AtomicInteger selections = new AtomicInteger();
        Item anvil = new AnvilItem();
        ItemPolicy policy = ItemPolicy.everyLines(10, () -> { selections.incrementAndGet(); return anvil; });

        policy.onLinesCleared(4);
        policy.onLinesCleared(4);
        assertTrue(policy.nextItem().isEmpty(), "8 lines earn nothing");
        policy.onLinesCleared(3);
        assertSame(anvil, policy.nextItem().orElseThrow(), "11 lines earn one item");
        assertTrue(policy.nextItem().isEmpty(), "the opportunity is consumed once");
        policy.onLinesCleared(12);
        assertTrue(policy.nextItem().isPresent(), "23 lines: second milestone");
        assertTrue(policy.nextItem().isEmpty(), "23 lines: not yet the third");
        assertEquals(2, selections.get(), "the selector is asked only when an item is handed out");
    }

    @Test
    void resetDropsPendingItemsAndProgress() {
        ItemPolicy policy = ItemPolicy.everyLines(10, AnvilItem::new);
        policy.onLinesCleared(19);
        policy.reset();
        assertTrue(policy.nextItem().isEmpty());
        policy.onLinesCleared(9);
        assertTrue(policy.nextItem().isEmpty(), "progress from before the reset does not count");
    }

    @Test
    void noneNeverHandsOutAnItemAndInvalidInputsAreRejected() {
        ItemPolicy none = ItemPolicy.none();
        none.onLinesCleared(100);
        assertTrue(none.nextItem().isEmpty());
        none.reset();
        assertSame(ItemPolicy.none(), none);
        assertThrows(IllegalArgumentException.class, () -> ItemPolicy.everyLines(0, AnvilItem::new));
        assertThrows(NullPointerException.class, () -> ItemPolicy.everyLines(10, null));
        assertThrows(IllegalArgumentException.class, () -> ItemPolicy.everyLines(10, AnvilItem::new).onLinesCleared(-1));
    }
}

/*
 * ItemRegistry 테스트: 기본 목록(L, W), 문자·이름 중복과 보이지 않는 문자 거부, 블록형 조회.
 */
package com.team.tetris.game.item;

import com.team.tetris.block.Board;
import com.team.tetris.block.Cell;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class ItemRegistryTest {
    @Test
    void defaultsContainTheTwoRequiredItemsWithDistinctSymbols() {
        ItemRegistry registry = ItemRegistry.defaults();
        assertEquals(List.of("LINE_DELETE", "ANVIL"), registry.items().stream().map(Item::name).toList());
        assertEquals(LineDeleteItem.SYMBOL, registry.bySymbol('L').orElseThrow().symbol());
        assertTrue(registry.standaloneBySymbol(AnvilItem.SYMBOL).isPresent());
        assertTrue(registry.standaloneBySymbol(LineDeleteItem.SYMBOL).isEmpty(), "L is embedded, not standalone");
        assertTrue(registry.bySymbol('?').isEmpty());
    }

    @Test
    void rejectsDuplicateSymbolsNamesAndInvisibleSymbols() {
        assertThrows(IllegalArgumentException.class,
                () -> new ItemRegistry(List.of(new LineDeleteItem(), new FakeItem("OTHER", 'L'))));
        assertThrows(IllegalArgumentException.class,
                () -> new ItemRegistry(List.of(new LineDeleteItem(), new FakeItem("LINE_DELETE", 'X'))));
        assertThrows(IllegalArgumentException.class, () -> new ItemRegistry(List.of(new FakeItem(" ", 'X'))));
        assertThrows(IllegalArgumentException.class, () -> new ItemRegistry(List.of(new FakeItem("SPACE", ' '))));
        assertThrows(NullPointerException.class, () -> new ItemRegistry(null));
        assertEquals(0, new ItemRegistry(List.of()).size());
    }

    private record FakeItem(String name, char symbol) implements EmbeddedItem {
        @Override public Set<Integer> onLock(Board board, Cell itemCell) { return Set.of(); }
    }
}

package com.team.tetris.game.item;

import com.team.tetris.common.events.CellCodes;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * 게임에 등장하는 아이템 목록. 아이템을 추가하는 유일한 지점은 {@link #defaults()}다.
 *
 * 생성 시 이름과 문자의 중복을 거부하므로 "아이템마다 서로 다른 문자"(2차 요구사항 4.2)가
 * 코드 수준에서 보장된다. 드라이버는 이 목록으로 아이템을 고르고, 화면은 블록형 아이템의
 * 미리보기 모양을 문자로 찾는다.
 */
public final class ItemRegistry {
    private final List<Item> items;
    private final Map<Character, Item> bySymbol = new HashMap<>();

    public ItemRegistry(List<? extends Item> items) {
        this.items = List.copyOf(Objects.requireNonNull(items, "items"));
        Set<String> names = new HashSet<>();
        for (Item item : this.items) {
            String name = Objects.requireNonNull(item.name(), "item name");
            if (name.isBlank() || !names.add(name)) {
                throw new IllegalArgumentException("Item names must be unique and non-blank: " + name);
            }
            if (!CellCodes.isSymbol(item.symbol())) {
                throw new IllegalArgumentException(name + " needs a visible symbol character");
            }
            if (bySymbol.putIfAbsent(item.symbol(), item) != null) {
                throw new IllegalArgumentException("Item symbol '" + item.symbol() + "' is used twice");
            }
        }
    }

    /** 실제 게임의 아이템 목록. 새 아이템은 이 목록에 한 줄 추가한다. */
    public static ItemRegistry defaults() {
        return new ItemRegistry(List.of(
                new LineDeleteItem(),
                new AnvilItem()));
    }

    /** 등록 순서대로의 불변 목록. */
    public List<Item> items() {
        return items;
    }

    public int size() {
        return items.size();
    }

    public Optional<Item> bySymbol(char symbol) {
        return Optional.ofNullable(bySymbol.get(symbol));
    }

    /** 문자가 블록형 아이템의 것일 때만 값이 있다. 화면이 미리보기 모양을 찾을 때 쓴다. */
    public Optional<StandaloneItem> standaloneBySymbol(char symbol) {
        return bySymbol(symbol).filter(StandaloneItem.class::isInstance).map(StandaloneItem.class::cast);
    }
}

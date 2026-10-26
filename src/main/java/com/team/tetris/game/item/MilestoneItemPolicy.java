package com.team.tetris.game.item;

import java.util.Objects;
import java.util.Optional;

/** 누적 줄 수가 일정 배수를 넘을 때마다 아이템 기회를 하나씩 쌓는다. 2차 요구사항 4.2의 "줄 10개를 삭제할 때마다". */
final class MilestoneItemPolicy implements ItemPolicy {
    private final int linesPerItem;
    private final ItemSelector selector;
    private int clearedLines;
    private int pendingItems;

    MilestoneItemPolicy(int linesPerItem, ItemSelector selector) {
        if (linesPerItem <= 0) throw new IllegalArgumentException("linesPerItem must be positive");
        this.linesPerItem = linesPerItem;
        this.selector = Objects.requireNonNull(selector, "selector");
    }

    @Override
    public void onLinesCleared(int lines) {
        if (lines < 0) throw new IllegalArgumentException("lines must be non-negative");
        int previousMilestones = clearedLines / linesPerItem;
        clearedLines += lines;
        pendingItems += clearedLines / linesPerItem - previousMilestones;
    }

    @Override
    public Optional<Item> nextItem() {
        if (pendingItems == 0) return Optional.empty();
        pendingItems--;
        return Optional.of(Objects.requireNonNull(selector.next(), "selected item"));
    }

    @Override
    public void reset() {
        clearedLines = 0;
        pendingItems = 0;
    }
}

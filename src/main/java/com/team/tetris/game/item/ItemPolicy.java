/*
 * 아이템 기회가 언제 생기고 무엇이 나오는지 정하는 규칙의 계약. none()은 일반 모드(아이템 없음),
 * everyLines(10, selector)는 아이템 모드(누적 10줄마다 하나, 요구사항 4.2).
 */
package com.team.tetris.game.item;

import java.util.Optional;

/**
 * 아이템 기회가 언제 생기고 무엇이 나오는지 정하는 규칙. 드라이버는 줄이 지워질 때와 큐를 채울 때만 이 객체에 묻는다.
 * 일반 모드는 {@link #none()}, 아이템 모드는 {@link #everyLines}(10줄마다 하나)를 쓴다.
 */
public interface ItemPolicy {
    /** 줄이 지워질 때마다 호출한다. 기회가 쌓인다. */
    void onLinesCleared(int lines);

    /** 큐에 블록을 넣을 때 호출한다. 쌓인 기회가 있으면 하나를 소모해 아이템을 돌려준다. */
    Optional<Item> nextItem();

    /** 새 게임을 시작할 때 쌓인 진행 상황을 버린다. */
    void reset();

    /** 아이템이 전혀 나오지 않는 규칙. 일반 모드. */
    static ItemPolicy none() {
        return None.INSTANCE;
    }

    /** 누적 {@code linesPerItem}줄마다 기회를 하나 얻고, 다음에 큐에 들어가는 블록이 선택기가 고른 아이템이 된다. */
    static ItemPolicy everyLines(int linesPerItem, ItemSelector selector) {
        return new MilestoneItemPolicy(linesPerItem, selector);
    }

    /** 상태가 없으므로 하나만 둔다. */
    enum None implements ItemPolicy {
        INSTANCE;

        @Override
        public void onLinesCleared(int lines) { }

        @Override
        public Optional<Item> nextItem() {
            return Optional.empty();
        }

        @Override
        public void reset() { }
    }
}

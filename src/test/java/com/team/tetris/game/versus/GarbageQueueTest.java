/*
 * GarbageQueue 테스트: 3차 요구사항 p.6의 공격 대기열 규칙(최대 10줄, 아래쪽에 쌓임, 가득 차면 무시,
 * 넘치면 새 공격의 아래쪽을 잘라냄)과 비우기.
 */
package com.team.tetris.game.versus;

import com.team.tetris.common.battle.GarbageLine;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;

class GarbageQueueTest {
    @Test
    void capacityIsTenLines() {
        assertEquals(10, GarbageQueue.CAPACITY);
    }

    @Test
    void anAttackIsKeptInOrder() {
        GarbageQueue queue = new GarbageQueue();
        assertTrue(queue.isEmpty());
        assertEquals(2, queue.offer(lines(1, 2)));
        assertEquals(lines(1, 2), queue.lines());
        assertEquals(2, queue.size());
        assertFalse(queue.isEmpty());
    }

    @Test
    void aLaterAttackIsAddedBelowTheEarlierOne() {
        GarbageQueue queue = new GarbageQueue();
        queue.offer(lines(1, 2));
        queue.offer(lines(3, 4, 5));
        assertEquals(lines(1, 2, 3, 4, 5), queue.lines(), "top to bottom");
    }

    @Test
    void overflowKeepsTheExistingLinesAndCutsTheBottomOfTheNewAttack() {
        GarbageQueue queue = new GarbageQueue();
        queue.offer(lines(1, 2, 3, 4));
        queue.offer(lines(5, 6, 7, 8));
        assertEquals(2, queue.offer(lines(9, 10, 11, 12)), "only two places were left");
        assertEquals(lines(1, 2, 3, 4, 5, 6, 7, 8, 9, 10), queue.lines());
    }

    @Test
    void aFullQueueIgnoresTheWholeAttack() {
        GarbageQueue queue = new GarbageQueue();
        queue.offer(lines(1, 2, 3, 4, 5, 6, 7, 8, 9, 10));
        assertEquals(0, queue.offer(lines(11, 12)));
        assertEquals(lines(1, 2, 3, 4, 5, 6, 7, 8, 9, 10), queue.lines());
    }

    @Test
    void anAttackThatExactlyFillsTheQueueIsTakenWhole() {
        GarbageQueue queue = new GarbageQueue();
        queue.offer(lines(1, 2, 3, 4, 5, 6, 7));
        assertEquals(3, queue.offer(lines(8, 9, 10)));
        assertEquals(10, queue.size());
    }

    @Test
    void drainReturnsEverythingAndEmptiesTheQueue() {
        GarbageQueue queue = new GarbageQueue();
        queue.offer(lines(1, 2, 3));
        assertEquals(lines(1, 2, 3), queue.drain());
        assertTrue(queue.isEmpty());
        assertEquals(List.of(), queue.drain());
        assertEquals(2, queue.offer(lines(4, 5)), "an emptied queue accepts attacks again");
    }

    @Test
    void clearEmptiesTheQueue() {
        GarbageQueue queue = new GarbageQueue();
        queue.offer(lines(1, 2));
        queue.clear();
        assertTrue(queue.isEmpty());
    }

    @Test
    void callersCannotChangeTheQueueThroughLists() {
        GarbageQueue queue = new GarbageQueue();
        List<GarbageLine> attack = new ArrayList<>(lines(1, 2));
        queue.offer(attack);
        attack.add(line(3));
        assertEquals(2, queue.size(), "the offered list is copied");
        assertThrows(UnsupportedOperationException.class, () -> queue.lines().add(line(4)));
        assertThrows(UnsupportedOperationException.class, () -> queue.drain().add(line(4)));
    }

    @Test
    void anEmptyAttackChangesNothingAndNullIsRejected() {
        GarbageQueue queue = new GarbageQueue();
        assertEquals(0, queue.offer(List.of()));
        assertTrue(queue.isEmpty());
        assertThrows(NullPointerException.class, () -> queue.offer(null));
    }

    /** 번호마다 모양이 다른 줄. 순서 비교에 쓴다. */
    private static GarbageLine line(int number) {
        return new GarbageLine(number);
    }

    private static List<GarbageLine> lines(int... numbers) {
        return IntStream.of(numbers).mapToObj(GarbageQueueTest::line).toList();
    }
}

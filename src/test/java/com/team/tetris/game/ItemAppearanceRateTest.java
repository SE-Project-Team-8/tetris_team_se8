/*
 * 실제 게임 경로(ItemRegistry.defaults() + 균등 선택기 + BlockBoardDriver)로 아이템 2,000개를 뽑아
 * 누적 10줄마다 정확히 하나가 나오는지와 아이템별 비율(±5%)을 검증한다.
 */
package com.team.tetris.game;

import com.team.tetris.block.Board;
import com.team.tetris.block.Rotation;
import com.team.tetris.block.Tetromino;
import com.team.tetris.block.TetrominoType;
import com.team.tetris.common.constants.GameConstants;
import com.team.tetris.common.events.CellCodes;
import com.team.tetris.game.item.Item;
import com.team.tetris.game.item.ItemRegistry;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Random;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 2차 요구사항 3.2의 기준(1,000회 이상 반복, ±5%)을 실제 게임의 아이템 등장 경로에 적용한다.
 * 테스트용 선택기가 아니라 {@code Main}과 같은 생성자({@link ItemRegistry#defaults()} + 균등 선택)를 쓴다.
 */
class ItemAppearanceRateTest {
    private static final int ITEMS = 2_000;
    private static final double TOLERANCE = 0.05;

    @Test
    void realDriverHandsOutExactlyOneItemPerTenLinesAndEveryItemEquallyOften() {
        ItemRegistry registry = ItemRegistry.defaults();
        Board board = new Board();
        BlockBoardDriver driver = new BlockBoardDriver(board, () -> TetrominoType.O, new Random(2026), registry);
        driver.setItemMode(true);
        driver.reset();

        Map<Character, Integer> counts = new TreeMap<>();
        int totalLines = 0;
        int handedOut = 0;
        int piecesDropped = 0;
        while (handedOut < ITEMS) {
            int current = driver.nextBlockTypes()[0];
            assertTrue(driver.spawnNextBlock());
            piecesDropped++;

            // 큐를 다시 채운 직후: 누적 줄 수가 10의 배수를 새로 넘었을 때만 아이템이 보여야 한다.
            int shown = driver.nextBlockTypes()[0];
            boolean expectItem = totalLines / GameConstants.LINES_PER_ITEM > handedOut;
            assertEquals(expectItem, CellCodes.hasItem(shown), "after " + totalLines + " lines");
            if (CellCodes.hasItem(shown)) {
                handedOut++;
                counts.merge(CellCodes.symbol(shown), 1, Integer::sum);
            }

            if (CellCodes.hasItem(current)) {
                // 아이템 조각: L은 자기 행을, W는 아무것도 지우지 않는다. 다음 일반 블록을 위해 지형만 비운다.
                driver.hardDrop();
                totalLines += driver.lockAndClearLines().clearedLines();
                board.clear();
            } else {
                fillTwoRowsExceptTheCenter(board);
                driver.hardDrop();
                int cleared = driver.lockAndClearLines().clearedLines();
                assertEquals(2, cleared);
                totalLines += cleared;
            }
        }

        System.out.println("items=" + handedOut + " lines=" + totalLines + " pieces=" + piecesDropped
                + " counts=" + counts);
        assertEquals(registry.size(), counts.size(), "every registered item appears");
        double expectedShare = 1.0 / registry.size();
        for (Item item : registry.items()) {
            double share = counts.getOrDefault(item.symbol(), 0) / (double) ITEMS;
            assertEquals(expectedShare, share, TOLERANCE,
                    item.name() + " share " + share + " over " + ITEMS + " items");
        }
    }

    private static void fillTwoRowsExceptTheCenter(Board board) {
        for (int col : new int[]{0, 2, 6, 8}) {
            assertTrue(board.tryPlace(new Tetromino(TetrominoType.O, Rotation.SPAWN, 18, col)));
        }
    }
}

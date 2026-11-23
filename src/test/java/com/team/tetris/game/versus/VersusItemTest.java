/*
 * 아이템 대전 테스트: 아이템 방식에서만 아이템이 나옴, 아이템 기회를 플레이어마다 따로 셈, 받은 회색 줄도 누적 줄 수에
 * 들어감, 줄 삭제 아이템이 지운 줄도 공격이 됨, 무게추는 공격을 만들지 않음 (rules.md 7절, LB-F16).
 */
package com.team.tetris.game.versus;

import com.team.tetris.block.Board;
import com.team.tetris.block.Rotation;
import com.team.tetris.block.Tetromino;
import com.team.tetris.block.TetrominoType;
import com.team.tetris.common.battle.GarbageLine;
import com.team.tetris.common.battle.Player;
import com.team.tetris.common.battle.VersusMode;
import com.team.tetris.common.constants.GameConstants;
import com.team.tetris.common.events.BoardSnapshot;
import com.team.tetris.common.events.CellCodes;
import com.team.tetris.game.BlockBoardDriver;
import com.team.tetris.game.GameAction;
import com.team.tetris.game.item.AnvilItem;
import com.team.tetris.game.item.Item;
import com.team.tetris.game.item.ItemPolicy;
import com.team.tetris.game.item.ItemSelector;
import com.team.tetris.game.item.LineDeleteItem;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Random;

import static com.team.tetris.game.versus.VersusMatchTest.edt;
import static com.team.tetris.game.versus.VersusMatchTest.fillAroundTheMiddle;
import static org.junit.jupiter.api.Assertions.*;

class VersusItemTest {
    /** 4·5열만 빈 줄. 가운데에 떨어지는 O가 지우고 남긴 모양. */
    private static final GarbageLine HOLES_AT_4_5 = new GarbageLine(0b1111001111);

    @Test
    void eachPlayerEarnsItemsFromTheirOwnClearedLinesIncludingReceivedGreyLines() throws Exception {
        ItemSelector lineDelete = LineDeleteItem::new;
        VersusMatchTest.Fixture f = new VersusMatchTest.Fixture(
                board -> new BlockBoardDriver(board, () -> TetrominoType.O, new Random(1), lineDelete));
        edt(() -> {
            f.match.start(VersusMode.ITEM);

            // P2 clears 8 lines and sends them all to P1's queue.
            for (int round = 0; round < 4; round++) clearTwoRows(f, Player.P2);
            assertEquals(8, f.match.queue(Player.P1).size());
            assertFalse(nextHasItem(f, Player.P2), "8 lines are not yet a milestone");

            // P1 clears 2 lines; the 8 received grey lines then enter P1's board.
            clearTwoRows(f, Player.P1);
            assertTrue(CellCodes.isGarbage(f.views.get(Player.P1).board.cells()[12][0]));
            assertFalse(nextHasItem(f, Player.P1), "P2's lines do not count for P1");

            // Each O falls through the grey lines' holes and clears the bottom two.
            for (int round = 0; round < 3; round++) {
                f.match.handle(Player.P1, GameAction.HARD_DROP);
                assertFalse(nextHasItem(f, Player.P1));
            }
            f.match.handle(Player.P1, GameAction.HARD_DROP);
            assertTrue(nextHasItem(f, Player.P1),
                    GameConstants.LINES_PER_ITEM + " lines, 8 of them grey, earn P1 an item");
            assertFalse(nextHasItem(f, Player.P2), "P2 still has 8 lines");
        });
    }

    @Test
    void onlyAnItemMatchHasItems() throws Exception {
        VersusMatchTest.Fixture f = new VersusMatchTest.Fixture(board -> alwaysItem(board, new LineDeleteItem()));
        edt(() -> {
            f.match.start(VersusMode.ITEM);
            assertTrue(nextHasItem(f, Player.P1));
            assertTrue(nextHasItem(f, Player.P2));
            assertTrue(fallingHasItem(f, Player.P1), "the first piece already carries one");

            for (VersusMode mode : List.of(VersusMode.NORMAL, VersusMode.TIME_LIMIT)) {
                f.match.start(mode);
                for (Player player : Player.values()) {
                    assertFalse(nextHasItem(f, player), mode + " has no items");
                    assertFalse(fallingHasItem(f, player), mode + " has no items");
                }
            }

            f.match.start(VersusMode.ITEM);
            assertTrue(nextHasItem(f, Player.P1), "items come back with the item mode");
        });
    }

    @Test
    void aRowDeletedByTheLineDeleteItemCountsTowardTheAttack() throws Exception {
        VersusMatchTest.Fixture f = new VersusMatchTest.Fixture(board -> alwaysItem(board, new LineDeleteItem()));
        edt(() -> {
            f.match.start(VersusMode.ITEM);
            Board board = f.boards.get(Player.P1);
            fillAroundTheMiddle(board, 19);
            assertTrue(board.tryPlace(new Tetromino(TetrominoType.I, Rotation.SPAWN, 17, 0)));   // row 18, cols 0-3

            f.match.handle(Player.P1, GameAction.HARD_DROP);
            assertEquals(List.of(new GarbageLine(0b0000001111), HOLES_AT_4_5), f.match.queue(Player.P2),
                    "the O completes row 19 and its L deletes row 18: two lines, an attack (A3)");
        });
    }

    @Test
    void theSameLockInANormalMatchClearsOneLineAndSendsNothing() throws Exception {
        VersusMatchTest.Fixture f = new VersusMatchTest.Fixture(board -> alwaysItem(board, new LineDeleteItem()));
        edt(() -> {
            f.match.start(VersusMode.NORMAL);
            Board board = f.boards.get(Player.P1);
            fillAroundTheMiddle(board, 19);
            assertTrue(board.tryPlace(new Tetromino(TetrominoType.I, Rotation.SPAWN, 17, 0)));

            f.match.handle(Player.P1, GameAction.HARD_DROP);
            assertEquals(List.of(), f.match.queue(Player.P2));
        });
    }

    @Test
    void anAnvilCrushesGreyCellsButSendsNothing() throws Exception {
        VersusMatchTest.Fixture f = new VersusMatchTest.Fixture(board -> alwaysItem(board, new AnvilItem()));
        edt(() -> {
            f.match.start(VersusMode.ITEM);
            f.boards.get(Player.P1).insertRowsAtBottom(List.of(HOLES_AT_4_5, HOLES_AT_4_5));

            f.match.handle(Player.P1, GameAction.HARD_DROP);
            assertEquals(List.of(), f.match.queue(Player.P2), "no line was cleared");
            int[][] cells = f.views.get(Player.P1).board.cells();
            assertTrue(CellCodes.isGarbage(cells[19][0]), "grey cells outside the anvil stay");
            assertFalse(CellCodes.isGarbage(cells[19][3]), "the anvil crushed the grey cells under it");
            assertFalse(CellCodes.isGarbage(cells[19][6]));
        });
    }

    /** row 18·19를 가운데만 비우고 그 플레이어의 O를 떨어뜨려 두 줄을 지운다. 보드는 비어 있어야 한다. */
    private static void clearTwoRows(VersusMatchTest.Fixture f, Player player) {
        fillAroundTheMiddle(f.boards.get(player), 18);
        fillAroundTheMiddle(f.boards.get(player), 19);
        f.match.handle(player, GameAction.HARD_DROP);
    }

    /** 아이템 방식이면 첫 블록부터 매번 item이 붙는 드라이버. 끼워 넣는 아이템은 O의 왼쪽 위 칸에 붙는다. */
    private static BlockBoardDriver alwaysItem(Board board, Item item) {
        ItemPolicy always = new ItemPolicy() {
            @Override public void onLinesCleared(int lines) { }
            @Override public Optional<Item> nextItem() { return Optional.of(item); }
            @Override public void reset() { }
        };
        Random firstCell = new Random() {
            @Override public int nextInt(int bound) { return 0; }
        };
        return new BlockBoardDriver(board, () -> TetrominoType.O, firstCell, always);
    }

    private static boolean nextHasItem(VersusMatchTest.Fixture f, Player player) {
        return Arrays.stream(f.views.get(player).next).anyMatch(CellCodes::hasItem);
    }

    private static boolean fallingHasItem(VersusMatchTest.Fixture f, Player player) {
        BoardSnapshot board = f.views.get(player).board;
        return Arrays.stream(board.cells()).flatMapToInt(Arrays::stream)
                .anyMatch(code -> code != BoardSnapshot.EMPTY && !CellCodes.isGarbage(code) && CellCodes.hasItem(code));
    }
}

/*
 * 다음 블록 큐의 한 항목. 일반 테트로미노(Regular, 포함형 아이템 부착 가능)와 블록형 아이템(Standalone)이
 * 각자 미리보기 코드를 만들고, 차례가 오면 낙하 조각(FallingPiece)을 생성한다.
 */
package com.team.tetris.game;

import com.team.tetris.block.Board;
import com.team.tetris.block.TetrominoType;
import com.team.tetris.common.events.CellCodes;
import com.team.tetris.game.item.EmbeddedItem;
import com.team.tetris.game.item.FallingPiece;
import com.team.tetris.game.item.StandaloneItem;

import java.util.Objects;

/** 다음 블록 큐의 한 항목. 미리보기 코드를 만들고, 차례가 오면 낙하 조각을 생성한다. */
sealed interface QueuedPiece {
    int previewCode();

    /** 위쪽 중앙에 생성한 조각. 생성 위치가 막혀 있으면 null. */
    FallingPiece spawn(Board board);

    /** 일반 테트로미노. 포함형 아이템이 있으면 {@code itemIndex} 칸에 붙는다. */
    record Regular(TetrominoType type, EmbeddedItem item, int itemIndex) implements QueuedPiece {
        public Regular {
            Objects.requireNonNull(type, "type");
            if ((item == null) != (itemIndex < 0)) {
                throw new IllegalArgumentException("An attached item needs exactly one cell index");
            }
        }

        @Override
        public int previewCode() {
            return item == null ? type.id() : CellCodes.preview(type.id(), item.symbol(), itemIndex);
        }

        @Override
        public FallingPiece spawn(Board board) {
            return TetrominoPiece.spawn(board, type, item, itemIndex);
        }
    }

    /** 블록 자체가 아이템인 항목. 모양·색·조각 생성은 아이템이 정한다. */
    record Standalone(StandaloneItem item) implements QueuedPiece {
        public Standalone {
            Objects.requireNonNull(item, "item");
        }

        @Override
        public int previewCode() {
            return CellCodes.cell(item.paletteType().id(), item.symbol());
        }

        @Override
        public FallingPiece spawn(Board board) {
            return item.spawn(board);
        }
    }
}

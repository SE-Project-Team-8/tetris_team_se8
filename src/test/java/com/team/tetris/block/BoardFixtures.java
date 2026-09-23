package com.team.tetris.block;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** 테스트에서만 쓰는 보드 준비·비교 도우미. 실제 Board의 쓰기 경계를 우회하지 않는다. */
final class BoardFixtures {
    private BoardFixtures() {
    }

    /** 문자 한 개를 셀 한 개로 사용한다. 점은 빈 칸, I/O/T/S/Z/J/L은 종류다. */
    static BoardView readOnly(String... rows) {
        List<String> copy = List.of(rows);
        if (copy.isEmpty() || copy.getFirst().isEmpty()
                || copy.stream().anyMatch(row -> row.length() != copy.getFirst().length())) {
            throw new IllegalArgumentException("Fixture must be a nonempty rectangle");
        }
        return new BoardView() {
            @Override
            public int rows() {
                return copy.size();
            }

            @Override
            public int cols() {
                return copy.getFirst().length();
            }

            @Override
            public Optional<TetrominoType> cellAt(int row, int col) {
                Objects.checkIndex(row, rows());
                Objects.checkIndex(col, cols());
                char symbol = copy.get(row).charAt(col);
                return symbol == '.' ? Optional.empty()
                        : Optional.of(TetrominoType.valueOf(String.valueOf(symbol)));
            }
        };
    }

    /** 호출 당시 셀을 문자열로 복사해 이후 변이와 독립적으로 비교한다. */
    static List<String> picture(BoardView board) {
        List<String> rows = new ArrayList<>();
        for (int row = 0; row < board.rows(); row++) {
            StringBuilder line = new StringBuilder();
            for (int col = 0; col < board.cols(); col++) {
                line.append(board.cellAt(row, col).map(Enum::name).orElse("."));
            }
            rows.add(line.toString());
        }
        return List.copyOf(rows);
    }

    /** 배치가 유효해야 하는 준비 코드에서 잘못된 좌표를 즉시 발견한다. */
    static void place(Board board, TetrominoType type, Rotation rotation, int row, int col) {
        if (!board.tryPlace(new Tetromino(type, rotation, row, col))) {
            throw new AssertionError("Invalid fixture placement: " + type + " at " + row + "," + col);
        }
    }

    /** 폭 4 보드의 원하는 행을 I로 채운다. 초기 I의 실제 행은 기준 행 + 1이다. */
    static void fillRow(Board board, int row) {
        place(board, TetrominoType.I, Rotation.SPAWN, row - 1, 0);
    }
}
